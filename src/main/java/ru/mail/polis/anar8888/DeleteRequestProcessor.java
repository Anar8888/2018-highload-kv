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
    public Response processDirectRequest(QueryParams queryParams, Request request) throws IOException {
        return null;
    }

    @Override
    public Response processProxiedRequest(QueryParams queryParams, Request request) throws IOException {
        return null;
    }
}
