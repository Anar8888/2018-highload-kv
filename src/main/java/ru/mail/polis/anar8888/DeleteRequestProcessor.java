package ru.mail.polis.anar8888;

import java.io.IOException;
import java.util.Set;

import one.nio.http.Request;
import one.nio.http.Response;
import ru.mail.polis.KVDao;

public class DeleteRequestProcessor extends AbstractRequestProcessor {

    protected DeleteRequestProcessor(KVDao dao, Set<String> replicas, String myReplica) {
        super(dao, replicas, myReplica);
    }

    @Override
    public Response processDirectRequest(QueryParams queryParams, Request request) {
        int ack = 0;
        byte[] id = queryParams.getId();
        try {
            for (String replica : getReplicas(queryParams)) {
                if (myReplica.equals(replica)) {
                    dao.remove(id);
                    ack++;
                } else if (proxiedDelete(getClientForReplica(replica), id).getStatus() == 202) {
                    ack++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ack >= queryParams.getAck() ?
                new Response(Response.ACCEPTED, Response.EMPTY) :
                new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @Override
    public Response processProxiedRequest(QueryParams queryParams, Request request) {
        try {
            dao.remove(queryParams.getId());
            return new Response(Response.ACCEPTED, Response.EMPTY);
        } catch (IOException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }
}
