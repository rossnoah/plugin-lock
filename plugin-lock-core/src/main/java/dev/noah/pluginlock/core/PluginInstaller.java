package dev.noah.pluginlock.core;

import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginLock;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PluginInstaller {
    private final HttpClient httpClient;

    public PluginInstaller() {
        this(HttpClient.newHttpClient());
    }

    public PluginInstaller(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void install(PluginLock lock, Path pluginsDirectory) throws IOException, InterruptedException {
        Files.createDirectories(pluginsDirectory);
        for (LockedPlugin plugin : lock.getPlugins()) {
            install(plugin, pluginsDirectory);
        }
    }

    private void install(LockedPlugin plugin, Path pluginsDirectory) throws IOException, InterruptedException {
        Path target = pluginsDirectory.resolve(plugin.getFileName());
        HashCheck hashCheck = hashCheck(plugin);
        if (Files.exists(target) && hashCheck.expectedHash().equalsIgnoreCase(hash(target, hashCheck.algorithm()))) {
            return;
        }

        Path temp = Files.createTempFile(pluginsDirectory, plugin.getFileName(), ".download");
        try {
            MessageDigest digest = messageDigest(hashCheck.algorithm());
            try (InputStream body = new DigestInputStream(openDownload(plugin), digest)) {
                Files.copy(body, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            String actualHash = HexFormat.of().formatHex(digest.digest());
            if (!hashCheck.expectedHash().equalsIgnoreCase(actualHash)) {
                throw new IOException(hashCheck.algorithm() + " mismatch for " + plugin.getId());
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private InputStream openDownload(LockedPlugin plugin) throws IOException, InterruptedException {
        URI uri = URI.create(plugin.getDownloadUrl());
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return Files.newInputStream(Path.of(uri));
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", "plugin-lock/0.1.0")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new IOException("Failed to download " + plugin.getId() + ": HTTP " + response.statusCode());
        }
        return response.body();
    }

    public static String sha512(Path path) throws IOException {
        return hash(path, "SHA-512");
    }

    private static String hash(Path path, String algorithm) throws IOException {
        MessageDigest digest = messageDigest(algorithm);
        try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStreamDiscard.INSTANCE);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static HashCheck hashCheck(LockedPlugin plugin) {
        if (plugin.getSha512() != null && !plugin.getSha512().isBlank()) {
            return new HashCheck("SHA-512", plugin.getSha512());
        }
        if (plugin.getSha256() != null && !plugin.getSha256().isBlank()) {
            return new HashCheck("SHA-256", plugin.getSha256());
        }
        throw new IllegalArgumentException("No supported hash for " + plugin.getId());
    }

    private static MessageDigest messageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " is not available", exception);
        }
    }

    private record HashCheck(String algorithm, String expectedHash) {
    }
}
