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
        int ack = 0;
        Map<Long, byte[]> timestampToValue = new HashMap<>();

        try {
            for (String replica : getReplicas(queryParams)) {
                if (replica.equals(myReplica)) {
                    try {
                        timestampToValue.put(dao.getUpdateTime(id), dao.get(id));
                        ack++;
                    } catch (NoSuchElementException e) {}
                } else {
                    Response response = proxiedGet(getClientForReplica(replica), id);
                    if (response.getStatus() == 200) {
                        timestampToValue.put(getTimestamp(response), response.getBody());
                        ack++;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        if (ack >= queryParams.getAck()) {
            byte[] maxTimestampValue = timestampToValue.entrySet().stream().max(Comparator.comparingLong(Map.Entry::getKey)).get().getValue();
            return Response.ok(maxTimestampValue);
        } else {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
    }

    @Override
    public Response processProxiedRequest(QueryParams queryParams, Request request) {
        try {
            byte[] id = queryParams.getId();
            return addTimestamp(Response.ok(dao.get(id)), dao.getUpdateTime(id));
        } catch (NoSuchElementException e) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        } catch (IOException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    private Response addTimestamp(Response response, long timestamp) {
        response.addHeader("UPDATE-TIME: " + timestamp);
        return response;
    }

    private long getTimestamp(Response request) {
        String timestamp = request.getHeader("UPDATE-TIME: ");
        if (timestamp == null) {
            return 0;
        } else {
            return Long.parseLong(timestamp);
        }
    }
}
