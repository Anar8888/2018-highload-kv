package ru.mail.polis.anar8888;

import java.io.File;
import java.io.IOException;
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
            return Files.readAllBytes(getPathForKey(key));
        } catch (NoSuchFileException e) {
            throw new NoSuchElementException();
        }
    }

    @Override
    public void upsert(@NotNull byte[] key, @NotNull byte[] value) throws IOException {
        Files.write(getPathForKey(key), value);
    }

    @Override
    public void remove(@NotNull byte[] key) throws IOException {
        try {
            Files.delete(getPathForKey(key));
        } catch (NoSuchFileException e) {
        }
    }

    @Override
    public void close() throws IOException {
    }

    private Path getPathForKey(byte[] key) {
        return Paths.get(root, encoder.encodeToString(key));
    }
}
