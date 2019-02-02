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
            byte[] data = Files.readAllBytes(getPathForKey(key));
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.getLong();

            byte[] value = new byte[data.length - Long.BYTES];
            buffer.get(value);
            return value;
        } catch (NoSuchFileException e) {
            throw new NoSuchElementException();
        }
    }

    @Override
    public void upsert(@NotNull byte[] key, @NotNull byte[] value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + value.length);
        buffer.putLong(System.currentTimeMillis());
        buffer.put(value);
        Files.write(getPathForKey(key), buffer.array());
    }

    @Override
    public void remove(@NotNull byte[] key) throws IOException {
        try {
            Files.delete(getPathForKey(key));
        } catch (NoSuchFileException e) {
        }
    }

    @Override
    public long getUpdateTime(byte[] key) throws NoSuchElementException, IOException {
        try {
            byte[] data = Files.readAllBytes(getPathForKey(key));
            ByteBuffer buffer = ByteBuffer.wrap(data);
            return buffer.getLong();
        } catch (NoSuchFileException e) {
            throw new NoSuchElementException();
        }
    }

    @Override
    public void close() throws IOException {
    }

    private Path getPathForKey(byte[] key) {
        return Paths.get(root, encoder.encodeToString(key));
    }
}
