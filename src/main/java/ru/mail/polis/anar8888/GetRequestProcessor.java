package ru.mail.polis.anar8888;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import one.nio.http.Request;
import one.nio.http.Response;
import ru.mail.polis.KVDao;

public class GetRequestProcessor extends AbstractRequestProcessor {

    protected GetRequestProcessor(KVDao dao, Set<String> replicas, String myReplica) {
        super(dao, replicas, myReplica);
    }

    @Override
    public Response processDirectRequest(QueryParams queryParams, Request request) {
        byte[] id = queryParams.getId();
        int ackFound = 0;
        int ackNotFound = 0;
        int ackDeleted = 0;
        Map<Long, byte[]> timestampToValue = new HashMap<>();

        for (String replica : getReplicas(queryParams)) {
            try {
                if (replica.equals(myReplica)) {
                    try {
                        timestampToValue.put(dao.getUpdateTime(id), dao.get(id));
                        ackFound++;
                    } catch (NoSuchElementException e) {
                        if (dao.isDeleted(id)) {
                            ackDeleted++;
                        } else {
                            ackNotFound++;
                        }
                    }
                } else {
                    Response response = proxiedGet(getClientForReplica(replica), id);
                    switch (response.getStatus()) {
                        case 200:
                            timestampToValue.put(getTimestamp(response), response.getBody());
                            ackFound++;
                            break;
                        case 404:
                            if (isDeleted(response)) {
                                ackDeleted++;
                            } else {
                                ackNotFound++;
                            }
                            break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (ackNotFound + ackDeleted + ackFound < queryParams.getAck()) {
            return new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
        } else if (ackFound > 0 && ackDeleted == 0) {
            byte[] maxTimestampValue = timestampToValue.entrySet().stream().max(Comparator.comparingLong(Map.Entry::getKey)).get().getValue();
            return Response.ok(maxTimestampValue);
        } else {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
    }

    @Override
    public Response processProxiedRequest(QueryParams queryParams, Request request) {
        Long updateTime = null;
        byte[] id = queryParams.getId();
        try {
            updateTime = dao.getUpdateTime(id);
            return addTimestamp(Response.ok(dao.get(id)), updateTime);
        } catch (NoSuchElementException e) {
            boolean deleted = false;
            try {
                deleted = dao.isDeleted(id) && updateTime != null;
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return deleted
                    ? addDeleted(addTimestamp(new Response(Response.NOT_FOUND, Response.EMPTY), updateTime))
                    : new Response(Response.NOT_FOUND, Response.EMPTY);
        } catch (IOException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    private Response addTimestamp(Response response, long timestamp) {
        response.addHeader("UPDATE-TIME: " + timestamp);
        return response;
    }

    private Response addDeleted(Response response) {
        response.addHeader("DELETED: 1");
        return response;
    }

    private boolean isDeleted(Response response) {
        return response.getHeader("DELETED:") != null;
    }

    private long getTimestamp(Response response) {
        String timestamp = response.getHeader("UPDATE-TIME: ");
        if (timestamp == null) {
            return 0;
        } else {
            return Long.parseLong(timestamp);
        }
    }
}
