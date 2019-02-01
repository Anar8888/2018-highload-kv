package ru.mail.polis.anar8888;

import java.io.IOException;
import java.util.NoSuchElementException;

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

    private KVDao dao;

    public static Service createDaoServiceOnPort(int port, KVDao dao) throws IOException {
        AcceptorConfig acceptorConfig = new AcceptorConfig();
        acceptorConfig.port = port;

        HttpServerConfig config = new HttpServerConfig();
        config.acceptors = new AcceptorConfig[]{acceptorConfig};
        return new Service(config, dao);
    }

    public Service(HttpServerConfig config, KVDao dao) throws IOException {
        super(config);
        this.dao = dao;
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
        String idString = request.getParameter("id=");

        if (idString == null || idString.isEmpty()) {
            response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        } else {
            byte[] id = idString.getBytes();
            switch (request.getMethod()) {
                case Request.METHOD_GET:
                    try {
                        response = Response.ok(dao.get(id));
                    } catch (NoSuchElementException e) {
                        response = new Response(Response.NOT_FOUND, Response.EMPTY);
                    }
                    break;
                case Request.METHOD_PUT:
                    dao.upsert(id, request.getBody());
                    response = new Response(Response.CREATED, Response.EMPTY);
                    break;
                case Request.METHOD_DELETE:
                    dao.remove(id);
                    response = new Response(Response.ACCEPTED, Response.EMPTY);
                    break;
                default:
                    response = new Response(Response.BAD_GATEWAY, Response.EMPTY);
                    break;
            }
        }

        httpSession.sendResponse(response);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendError(Response.NOT_FOUND, "");
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
