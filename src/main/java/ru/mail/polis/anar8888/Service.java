package ru.mail.polis.anar8888;

import java.io.IOException;
import java.util.Set;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import ru.mail.polis.KVDao;
import ru.mail.polis.KVService;

public class Service extends HttpServer implements KVService {

    private Set<String> topology;

    private final PutRequestProcessor putRequestProcessor;
    private final DeleteRequestProcessor deleteRequestProcessor;
    private final GetRequestProcessor getRequestProcessor;

    public static Service createDaoService(int port, KVDao dao, Set<String> topology) throws IOException {
        AcceptorConfig acceptorConfig = new AcceptorConfig();
        acceptorConfig.port = port;

        HttpServerConfig config = new HttpServerConfig();
        config.acceptors = new AcceptorConfig[]{acceptorConfig};
        return new Service(config, port, dao, topology);
    }

    public Service(HttpServerConfig config, int port, KVDao dao, Set<String> topology) throws IOException {
        super(config);
        this.topology = topology;

        String myReplica = topology.stream().filter(r -> r.indexOf(":" + port) > 0).findFirst().get();
        putRequestProcessor = new PutRequestProcessor(dao, topology, myReplica);
        getRequestProcessor = new GetRequestProcessor(dao, topology, myReplica);
        deleteRequestProcessor = new DeleteRequestProcessor(dao, topology, myReplica);
    }

    @Path("/v0/status")
    public void status(Request request, HttpSession httpSession) throws IOException {
        if (request.getMethod() == Request.METHOD_GET) {
            httpSession.sendResponse(Response.ok("Hello, world!"));
        } else {
            httpSession.sendError(Response.BAD_GATEWAY, null);
        }
    }

    @Path("/v0/entity")
    public void entity(Request request, HttpSession httpSession) throws IOException {
        Response response;
        final QueryParams queryParams;
        try {
            queryParams = QueryParams.fromRequest(request, topology.size());
        } catch (IllegalArgumentException e) {
            httpSession.sendError(Response.BAD_REQUEST, null);
            return;
        }

        switch (request.getMethod()) {
            case Request.METHOD_GET:
                response = getRequestProcessor.process(queryParams, request);
                break;
            case Request.METHOD_PUT:
                response = putRequestProcessor.process(queryParams, request);
                break;
            case Request.METHOD_DELETE:
                response = deleteRequestProcessor.process(queryParams, request);
                break;
            default:
                response = new Response(Response.BAD_GATEWAY, Response.EMPTY);
                break;
        }

        httpSession.sendResponse(response);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }
}
