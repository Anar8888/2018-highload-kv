package ru.mail.polis.anar8888;

import one.nio.http.Request;

public class QueryParams {

    private final byte[] id;

    private final int ack;

    private final int from;

    public QueryParams(byte[] id, int ack, int from) {
        this.id = id;
        this.ack = ack;
        this.from = from;
    }

    public byte[] getId() {
        return id;
    }

    public int getAck() {
        return ack;
    }

    public int getFrom() {
        return from;
    }

    public static QueryParams fromRequest(Request request, int count) throws IllegalArgumentException {
        String id = request.getParameter("id=");
        String replicas = request.getParameter("replicas=");

        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException();
        } else if (replicas != null) {
            String[] replicasParts = replicas.split("/");
            if (replicasParts.length == 2) {
                try {
                    int ack = Integer.valueOf(replicasParts[0]);
                    int from = Integer.valueOf(replicasParts[1]);

                    if (from > count || ack < 1 || ack > from) {
                        throw new IllegalArgumentException();
                    }

                    return new QueryParams(id.getBytes(), ack, from);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e);
                }
            } else {
                throw new IllegalArgumentException();
            }
        } else {
            return new QueryParams(id.getBytes(), count / 2 + 1, count);
        }
    }
}
