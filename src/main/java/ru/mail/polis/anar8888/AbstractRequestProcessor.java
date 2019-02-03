package ru.mail.polis.anar8888;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import one.nio.http.HttpClient;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import ru.mail.polis.KVDao;

abstract public class AbstractRequestProcessor {

    private static final String PROXIED_HEADER = "PROXIED";
    private static final String PROXIED_HEADER_LONG = PROXIED_HEADER + ": 1";

    protected final KVDao dao;
    protected final Set<String> replicas;
    protected final String myReplica;
    private HashMap<String, HttpClient> clients;

    public Response process(QueryParams queryParams, Request request) {
        return request.getHeader(PROXIED_HEADER) == null
                ? processDirectRequest(queryParams, request)
                : processProxiedRequest(queryParams, request);
    }

    protected AbstractRequestProcessor(KVDao dao, Set<String> replicas, String myReplica) {
        this.dao = dao;
        this.replicas = replicas;
        this.myReplica = myReplica;
        this.clients = new HashMap<>();
        for (String host : replicas) {
            if (!host.equals(myReplica)) {
                this.clients.put(host, new HttpClient(new ConnectionString(host)));
            }
        }
    }

    public HttpClient getClientForReplica(String replica) {
        return clients.get(replica);
    }

    public Response proxiedPut(HttpClient client, byte[] key, byte[] value) throws IOException {
        try {
            return client.put(createEntityQuery(key), value, PROXIED_HEADER_LONG);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public Response proxiedGet(HttpClient client, byte[] key) throws IOException {
        try {
            return client.get(createEntityQuery(key), PROXIED_HEADER_LONG);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public Response proxiedDelete(HttpClient client, byte[] key) throws IOException {
        try {
            return client.delete(createEntityQuery(key),PROXIED_HEADER_LONG);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public List<String> getReplicas(QueryParams params) {
        int salt = Arrays.hashCode(params.getId());
        return replicas.stream()
                .sorted(Comparator.comparingInt(s -> s.hashCode() ^ salt))
                .collect(Collectors.toList())
                .subList(0, params.getFrom());
    }

    private String createEntityQuery(byte[] key) {
        return "/v0/entity?id=" + new String(key);
    }

    abstract protected Response processDirectRequest(QueryParams queryParams, Request request);

    abstract protected Response processProxiedRequest(QueryParams queryParams, Request request);
}
