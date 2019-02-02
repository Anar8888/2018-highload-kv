package ru.mail.polis.anar8888;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.NoSuchElementException;

import org.jetbrains.annotations.NotNull;

import ru.mail.polis.KVDao;

public class KVFilesDao implements KVDao {

    private final static Base64.Encoder encoder = Base64.getUrlEncoder();
    private final String root;

    public KVFilesDao(File path) {
        root = path.getAbsolutePath();
    }

    @NotNull
    @Override
    public byte[] get(@NotNull byte[] key) throws NoSuchElementException, IOException {
        try {
            ValueWrapped valueWrapped = ValueWrapped.fromBytes(Files.readAllBytes(getPathForKey(key)));
            if (valueWrapped.deleted) {
                throw new NoSuchElementException();
            }
            return valueWrapped.value;
        } catch (NoSuchFileException e) {
            throw new NoSuchElementException();
        }
    }

    @Override
    public void upsert(@NotNull byte[] key, @NotNull byte[] value) throws IOException {
        ValueWrapped valueWrapped = new ValueWrapped(value, false, System.currentTimeMillis());
        Files.write(getPathForKey(key), valueWrapped.toBytes());
    }

    @Override
    public void remove(@NotNull byte[] key) throws IOException {
        ValueWrapped valueWrapped = new ValueWrapped(new byte[0], true, System.currentTimeMillis());
        Files.write(getPathForKey(key), valueWrapped.toBytes());
    }

    @Override
    public long getUpdateTime(byte[] key) throws NoSuchElementException, IOException {
        try {
            ValueWrapped valueWrapped = ValueWrapped.fromBytes(Files.readAllBytes(getPathForKey(key)));
            return valueWrapped.timestamp;
        } catch (NoSuchFileException e) {
            throw new NoSuchElementException();
        }
    }

    @Override
    public boolean isDeleted(byte[] key) throws IOException {
        try {
            ValueWrapped valueWrapped = ValueWrapped.fromBytes(Files.readAllBytes(getPathForKey(key)));
            return valueWrapped.deleted;
        } catch (NoSuchFileException e) {
            return false;
        }
    }

    @Override
    public void close() throws IOException {
    }

    private Path getPathForKey(byte[] key) {
        return Paths.get(root, encoder.encodeToString(key));
    }

    private static class ValueWrapped {
        private final byte[] value;
        private final boolean deleted;
        private final long timestamp;

        public ValueWrapped(byte[] value, boolean deleted, long timestamp) {
            this.value = value;
            this.deleted = deleted;
            this.timestamp = timestamp;
        }

        public static ValueWrapped fromBytes(byte[] data) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            long timestamp = buffer.getLong();
            boolean deleted = buffer.get() > 0;
            byte[] value = new byte[data.length - Long.BYTES - Byte.BYTES];
            buffer.get(value);
            return new ValueWrapped(value, deleted, timestamp);
        }

        public byte[] toBytes() {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + Byte.BYTES + value.length);
            buffer.putLong(timestamp);
            buffer.put((byte) (deleted ? 1 : 0));
            buffer.put(value);
            return buffer.array();
        }
    }
}
