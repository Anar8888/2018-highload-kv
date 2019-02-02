package ru.mail.polis.anar8888;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import one.nio.http.Request;
import one.nio.http.Response;
import ru.mail.polis.KVDao;

public class PutRequestProcessor extends AbstractRequestProcessor {

    protected PutRequestProcessor(KVDao dao, Set<String> replicas, String myReplica) {
        super(dao, replicas, myReplica);
    }

    @Override
    public Response processDirectRequest(QueryParams queryParams, Request request) throws IOException {
        List<String> replicas = getReplicas(queryParams);

        byte[] id = queryParams.getId();
        byte[] value = request.getBody();
        int ack = 0;

        try {
            for (String replica : replicas) {
                if (myReplica.equals(replica)) {
                    dao.upsert(id, value);
                    ack++;
                } else if (proxiedPut(getClientForReplica(replica), id, value).getStatus() == 201) {
                    ack++;
                }
            }
        } catch (IOException e) {}

        return ack >= queryParams.getAck()
                ? new Response(Response.CREATED, Response.EMPTY)
                : new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
    }

    @Override
    public Response processProxiedRequest(QueryParams queryParams, Request request) throws IOException {
        dao.upsert(queryParams.getId(), request.getBody());
        return new Response(Response.CREATED, Response.EMPTY);
    }

}
