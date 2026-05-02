package dev.noah.pluginlock.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.noah.pluginlock.core.DownloadProgress;
import dev.noah.pluginlock.core.model.LockedServer;

import java.io.IOException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ServerDownloads {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final URI PAPER_BASE = URI.create("https://fill.papermc.io/v3/");
    private static final URI PURPUR_BASE = URI.create("https://api.purpurmc.org/v2/purpur/");
    private final HttpClient httpClient;
    private final URI paperBase;
    private final URI purpurBase;
    private static final String USER_AGENT = "plugin-lock/0.1.0";

    ServerDownloads(HttpClient httpClient) {
        this(httpClient, PAPER_BASE, PURPUR_BASE);
    }

    ServerDownloads(HttpClient httpClient, URI paperBase, URI purpurBase) {
        this.httpClient = httpClient;
        this.paperBase = paperBase;
        this.purpurBase = purpurBase;
    }

    List<String> versions(String provider) throws IOException, InterruptedException {
        if ("paper".equalsIgnoreCase(provider)) {
            return paperVersions();
        }
        if ("purpur".equalsIgnoreCase(provider)) {
            return purpurVersions();
        }
        throw new IllegalArgumentException("Unsupported server provider: " + provider);
    }

    LockedServer latest(String provider, String minecraftVersion) throws IOException, InterruptedException {
        if ("paper".equalsIgnoreCase(provider)) {
            return latestPaper(minecraftVersion);
        }
        if ("purpur".equalsIgnoreCase(provider)) {
            return latestPurpur(minecraftVersion);
        }
        throw new IllegalArgumentException("Unsupported server provider: " + provider);
    }

    Path download(LockedServer server, Path targetDirectory) throws IOException, InterruptedException {
        return download(server, targetDirectory, DownloadProgress.NONE);
    }

    Path download(LockedServer server, Path targetDirectory, DownloadProgress progress) throws IOException, InterruptedException {
        Files.createDirectories(targetDirectory);
        Path target = targetDirectory.resolve(server.getFileName());
        if (Files.exists(target) && hasExpectedHash(server, target)) {
            return target;
        }
        Path temp = Files.createTempFile(targetDirectory, server.getFileName(), ".download");
        try {
            HttpRequest request = request(URI.create(server.getDownloadUrl()), "application/octet-stream");
            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                throw new IOException("Failed to download server jar: HTTP " + response.statusCode());
            }
            copyAndVerify(response.body(), temp, server, progress);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private List<String> paperVersions() throws IOException, InterruptedException {
        JsonNode root = get(paperBase.resolve("projects/paper"));
        List<String> versions = new ArrayList<>();
        root.path("versions").fields().forEachRemaining(entry -> {
            for (JsonNode version : entry.getValue()) {
                String value = version.asText("");
                if (!value.isBlank()) {
                    versions.add(value);
                }
            }
        });
        return minecraftVersions(versions);
    }

    private List<String> purpurVersions() throws IOException, InterruptedException {
        JsonNode root = get(purpurBase);
        List<String> versions = new ArrayList<>();
        for (JsonNode version : root.path("versions")) {
            String value = version.asText("");
            if (!value.isBlank()) {
                versions.add(value);
            }
        }
        return minecraftVersions(versions);
    }

    private LockedServer latestPaper(String minecraftVersion) throws IOException, InterruptedException {
        JsonNode builds = get(paperBase.resolve("projects/paper/versions/" + minecraftVersion + "/builds"));
        for (JsonNode build : builds) {
            if (!"STABLE".equalsIgnoreCase(build.path("channel").asText(""))) {
                continue;
            }
            JsonNode download = build.path("downloads").path("server:default");
            LockedServer server = new LockedServer();
            server.setProvider("paper");
            server.setMinecraftVersion(minecraftVersion);
            server.setBuild(build.path("id").asText());
            server.setFileName(download.path("name").asText("paper-" + minecraftVersion + "-" + server.getBuild() + ".jar"));
            server.setDownloadUrl(download.path("url").asText());
            server.setSha256(download.path("checksums").path("sha256").asText());
            server.setSize(download.path("size").asLong());
            return server;
        }
        throw new IllegalArgumentException("No stable Paper builds found for " + minecraftVersion);
    }

    private LockedServer latestPurpur(String minecraftVersion) throws IOException, InterruptedException {
        JsonNode root = get(purpurBase.resolve(minecraftVersion));
        String build = root.path("builds").path("latest").asText("");
        if (build.isBlank()) {
            throw new IllegalArgumentException("No Purpur builds found for " + minecraftVersion);
        }
        LockedServer server = new LockedServer();
        server.setProvider("purpur");
        server.setMinecraftVersion(minecraftVersion);
        server.setBuild(build);
        server.setFileName("purpur-" + minecraftVersion + "-" + build + ".jar");
        server.setDownloadUrl(purpurBase.resolve(minecraftVersion + "/" + build + "/download").toString());
        return server;
    }

    private JsonNode get(URI uri) throws IOException, InterruptedException {
        HttpRequest request = request(uri, "application/json");
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new IOException("Server download API request failed: HTTP " + response.statusCode() + " for " + uri);
        }
        return JSON.readTree(response.body());
    }

    private static HttpRequest request(URI uri, String accept) {
        return HttpRequest.newBuilder(uri)
                .header("Accept", accept)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
    }

    private static void copyAndVerify(java.io.InputStream body, Path temp, LockedServer server, DownloadProgress progress) throws IOException {
        if (server.getSha256() == null || server.getSha256().isBlank()) {
            copyWithProgress(body, temp, server.getFileName(), server.getSize(), progress);
            return;
        }
        MessageDigest digest = sha256Digest();
        try (var digestBody = new DigestInputStream(body, digest)) {
            copyWithProgress(digestBody, temp, server.getFileName(), server.getSize(), progress);
        }
        String actualHash = HexFormat.of().formatHex(digest.digest());
        if (!server.getSha256().equalsIgnoreCase(actualHash)) {
            throw new IOException("SHA-256 mismatch for server jar");
        }
    }

    private static void copyWithProgress(java.io.InputStream body, Path temp, String fileName, long totalBytes, DownloadProgress progress) throws IOException {
        progress.update(fileName, 0, totalBytes);
        try (var output = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[16 * 1024];
            long downloaded = 0;
            int read;
            while ((read = body.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                downloaded += read;
                progress.update(fileName, downloaded, totalBytes);
            }
            if (totalBytes <= 0) {
                progress.update(fileName, downloaded, downloaded);
            }
        }
    }

    private static boolean hasExpectedHash(LockedServer server, Path target) throws IOException {
        return server.getSha256() != null
                && !server.getSha256().isBlank()
                && server.getSha256().equalsIgnoreCase(sha256(target));
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        try (var input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    static String pluginLoaderFor(String provider) {
        return "purpur".equalsIgnoreCase(provider) ? "paper" : provider.toLowerCase(Locale.ROOT);
    }

    static List<String> minecraftVersions(List<String> versions) {
        return versions.stream()
                .filter(SemanticVersion::isStable)
                .distinct()
                .sorted((left, right) -> SemanticVersion.parse(right).compareTo(SemanticVersion.parse(left)))
                .toList();
    }

    record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion> {
        static boolean isStable(String value) {
            return value != null && value.matches("\\d+\\.\\d+(?:\\.\\d+)?");
        }

        static SemanticVersion parse(String value) {
            String[] parts = value.split("\\.");
            return new SemanticVersion(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    parts.length > 2 ? Integer.parseInt(parts[2]) : 0
            );
        }

        @Override
        public int compareTo(SemanticVersion other) {
            int majorCompare = Integer.compare(major, other.major);
            if (majorCompare != 0) {
                return majorCompare;
            }
            int minorCompare = Integer.compare(minor, other.minor);
            if (minorCompare != 0) {
                return minorCompare;
            }
            return Integer.compare(patch, other.patch);
        }
    }
}
