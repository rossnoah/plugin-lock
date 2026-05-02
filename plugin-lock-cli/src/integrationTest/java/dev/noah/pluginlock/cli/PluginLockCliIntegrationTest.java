package dev.noah.pluginlock.cli;

import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLockCliIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void initCreatesManifestInWorkingDirectory() throws Exception {
        try (ApiServer server = apiServer()) {
            int exitCode = executeWithApi(server, tempDir, "init", "--minecraft", "1.21.4", "--server", "paper", "--yes");

            assertEquals(0, exitCode);
            PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
            PluginLock lock = PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE));
            assertEquals("1.21.4", manifest.getMinecraftVersion());
            assertEquals("paper", manifest.getLoader());
            assertEquals("paper", lock.getServer().getProvider());
            assertEquals("1.21.4", lock.getServer().getMinecraftVersion());
            assertEquals("101", lock.getServer().getBuild());
            assertEquals("paper 1.21.4 jar", Files.readString(tempDir.resolve("paper-1.21.4-101.jar")));
        }
    }

    @Test
    void detectsParentServerDirectoryWhenProjectDirIsPluginsDirectory() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);

        try (ApiServer server = apiServer()) {
            int exitCode = executeWithApi(server, pluginsDir, "init", "--minecraft", "1.21.4", "--server", "paper", "--yes");

            assertEquals(0, exitCode);
            assertTrue(Files.exists(tempDir.resolve(PluginLockFiles.MANIFEST_FILE)));
            assertTrue(Files.exists(tempDir.resolve(PluginLockFiles.LOCK_FILE)));
            assertTrue(Files.notExists(pluginsDir.resolve(PluginLockFiles.MANIFEST_FILE)));
        }
    }

    @Test
    void installsIntoDetectedPluginsDirectoryWhenRunFromPluginsDirectory() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Path source = tempDir.resolve("source.jar");
        Files.writeString(source, "jar body");

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        lock.setPlugins(java.util.List.of(lockedPlugin("local", "local.jar", source)));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        int exitCode = execute(pluginsDir, "ci");

        assertEquals(0, exitCode);
        assertTrue(Files.exists(pluginsDir.resolve("local.jar")));
        assertTrue(Files.notExists(pluginsDir.resolve("plugins/local.jar")));
    }

    @Test
    void keepsExplicitNonPluginsProjectDirectoryAsServerRoot() throws Exception {
        Path serverRoot = tempDir.resolve("server");
        Files.createDirectories(serverRoot.resolve("plugins"));

        assertEquals(serverRoot.toAbsolutePath().normalize(), PluginLockCli.detectProjectDir(serverRoot));
    }

    @Test
    void initAcceptsLatestPaperVersionFromApiWithYes() throws Exception {
        try (ApiServer server = apiServer()) {
            int exitCode = executeWithApi(server, tempDir, "init", "--yes");

            PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
            PluginLock lock = PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE));
            assertEquals(0, exitCode);
            assertEquals("26.1.2", manifest.getMinecraftVersion());
            assertEquals("paper", manifest.getLoader());
            assertEquals("paper-26.1.2-140.jar", lock.getServer().getFileName());
            assertEquals(sha256("paper latest jar"), lock.getServer().getSha256());
            assertEquals("paper latest jar", Files.readString(tempDir.resolve("paper-26.1.2-140.jar")));
        }
    }

    @Test
    void initSkipsServerDownloadWhenMatchingJarAlreadyExists() throws Exception {
        try (ApiServer server = apiServer()) {
            Files.writeString(tempDir.resolve("paper-26.1.2-140.jar"), "paper latest jar");

            int exitCode = executeWithApi(server, tempDir, "init", "--yes");

            assertEquals(0, exitCode);
            assertEquals(0, server.requestCount("/downloads/paper-latest.jar"));
            assertEquals("paper latest jar", Files.readString(tempDir.resolve("paper-26.1.2-140.jar")));
        }
    }

    @Test
    void initFailsCleanlyWhenServerDownloadHashMismatches() throws Exception {
        try (ApiServer server = apiServer()) {
            server.bytes("/downloads/paper-latest.jar", "tampered jar");

            CliResult result = executeCapturingWithApi(server, tempDir, "init", "--yes");

            assertEquals(1, result.exitCode());
            assertTrue(result.output().contains("Error: SHA-256 mismatch for server jar"));
            assertTrue(Files.notExists(tempDir.resolve("paper-26.1.2-140.jar")));
            assertFalse(result.output().contains("Exception"));
        }
    }

    @Test
    void initPromptsForServerAndVersionFromApis() throws Exception {
        try (ApiServer server = apiServer()) {
            int exitCode = executeWithApiAndInput(server, tempDir, "2\n2\n", "init");

            PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
            PluginLock lock = PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE));
            assertEquals(0, exitCode);
            assertEquals("1.21.11", manifest.getMinecraftVersion());
            assertEquals("paper", manifest.getLoader());
            assertEquals("purpur", lock.getServer().getProvider());
            assertEquals("3333", lock.getServer().getBuild());
            assertEquals("purpur 1.21.11 jar", Files.readString(tempDir.resolve("purpur-1.21.11-3333.jar")));
        }
    }

    @Test
    void initUsesDefaultsForBlankPromptAnswers() throws Exception {
        try (ApiServer server = apiServer()) {
            int exitCode = executeWithApiAndInput(server, tempDir, "\n\n", "init");

            PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
            assertEquals(0, exitCode);
            assertEquals("26.1.2", manifest.getMinecraftVersion());
            assertEquals("paper", manifest.getLoader());
        }
    }

    @Test
    void initFailsCleanlyWhenManifestAlreadyExists() throws Exception {
        try (ApiServer server = apiServer()) {
            assertEquals(0, executeWithApi(server, tempDir, "init", "--yes"));

            CliResult result = executeCapturingWithApi(server, tempDir, "init", "--yes");

            assertEquals(1, result.exitCode());
            assertTrue(result.output().contains("Error: plugin-lock.json already exists"));
            assertFalse(result.output().contains("Exception"));
        }
    }

    @Test
    void addCreatesManifestWhenMissingAndReplacesDuplicateProviderEntry() throws Exception {
        assertEquals(0, execute(tempDir, "add", "luckperms", "--version", "latest", "--yes"));
        assertEquals(0, execute(tempDir, "add", "luckperms", "--version", "v5", "--yes"));

        PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
        assertEquals(1, manifest.getPlugins().size());
        assertEquals("luckperms", manifest.getPlugins().getFirst().getId());
        assertEquals("v5", manifest.getPlugins().getFirst().getVersion());
    }

    @Test
    void installReadsLockfileAndWritesPluginDirectory() throws Exception {
        Path source = tempDir.resolve("source.jar");
        Files.writeString(source, "jar body");

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        lock.setPlugins(java.util.List.of(lockedPlugin("local", "local.jar", source)));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        int exitCode = execute(tempDir, "install", "--plugins-dir", tempDir.resolve("plugins").toString());

        assertEquals(0, exitCode);
        assertTrue(Files.exists(tempDir.resolve("plugins/local.jar")));
    }

    @Test
    void cleanInstallAliasReinstallsFromLockfileOnly() throws Exception {
        Path source = tempDir.resolve("source.jar");
        Files.writeString(source, "clean jar body");
        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        lock.setPlugins(java.util.List.of(lockedPlugin("local", "local.jar", source)));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        Path installed = tempDir.resolve("plugins/local.jar");
        Files.createDirectories(installed.getParent());
        Files.writeString(installed, "stale");

        int exitCode = execute(tempDir, "ci", "--plugins-dir", tempDir.resolve("plugins").toString());

        assertEquals(0, exitCode);
        assertEquals("clean jar body", Files.readString(installed));
    }

    @Test
    void removeDeletesManifestEntryLockEntryAndInstalledJar() throws Exception {
        Path source = tempDir.resolve("source.jar");
        Files.writeString(source, "jar body");
        PluginManifest manifest = new PluginManifest();
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest("local", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        lock.setPlugins(java.util.List.of(lockedPlugin("local", "local.jar", source)));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);
        Files.createDirectories(tempDir.resolve("plugins"));
        Files.writeString(tempDir.resolve("plugins/local.jar"), "jar body");

        int exitCode = execute(tempDir, "rm", "local", "--plugins-dir", tempDir.resolve("plugins").toString());

        assertEquals(0, exitCode);
        assertTrue(PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE)).getPlugins().isEmpty());
        assertTrue(PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE)).getPlugins().isEmpty());
        assertTrue(Files.notExists(tempDir.resolve("plugins/local.jar")));
    }

    @Test
    void removeMatchesPluginNameRecordedInLockfile() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Files.writeString(pluginsDir.resolve("renamed-artifact.jar"), "jar body");

        PluginManifest manifest = new PluginManifest();
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest("provider-slug", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        LockedPlugin plugin = new LockedPlugin();
        plugin.setId("provider-slug");
        plugin.setProvider("modrinth");
        plugin.setName("RuntimeName");
        plugin.setFileName("renamed-artifact.jar");
        lock.setPlugins(java.util.List.of(plugin));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        int exitCode = execute(tempDir, "rm", "runtimename", "--plugins-dir", pluginsDir.toString());

        assertEquals(0, exitCode);
        assertTrue(Files.notExists(pluginsDir.resolve("renamed-artifact.jar")));
        assertTrue(PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE)).getPlugins().isEmpty());
        assertTrue(PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE)).getPlugins().isEmpty());
    }

    @Test
    void removeMatchesFileNameRecordedInLockfile() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Files.writeString(pluginsDir.resolve("artifact-name.jar"), "jar body");

        PluginLock lock = new PluginLock();
        LockedPlugin plugin = new LockedPlugin();
        plugin.setId("provider-slug");
        plugin.setProvider("modrinth");
        plugin.setName("Provider Slug");
        plugin.setFileName("artifact-name.jar");
        lock.setPlugins(java.util.List.of(plugin));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        int exitCode = execute(tempDir, "rm", "artifact-name", "--plugins-dir", pluginsDir.toString());

        assertEquals(0, exitCode);
        assertTrue(Files.notExists(pluginsDir.resolve("artifact-name.jar")));
        assertTrue(PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE)).getPlugins().isEmpty());
    }

    @Test
    void removeCleansManifestWhenLockfileIsMissing() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setPlugins(java.util.List.of(
                new dev.noah.pluginlock.core.model.PluginRequest("keep", "modrinth", "latest"),
                new dev.noah.pluginlock.core.model.PluginRequest("remove-me", "hangar", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        int exitCode = execute(tempDir, "rm", "REMOVE-ME");

        PluginManifest restored = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
        assertEquals(0, exitCode);
        assertEquals(1, restored.getPlugins().size());
        assertEquals("keep", restored.getPlugins().getFirst().getId());
    }

    @Test
    void removeFailsCleanlyWhenPluginLockFilesAreMissing() {
        CliResult result = executeCapturing(tempDir, "rm", "missing");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("Error: No plugin-lock.json or plugin-lock.lock.json found"));
        assertFalse(result.output().contains("Exception"));
    }

    @Test
    void executionErrorsAreReportedWithoutStackTraces() {
        CliResult result = executeCapturing(tempDir, "install", "luckperms", "--provider", "unknown", "--yes");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("Error: Unsupported provider: unknown"));
        assertFalse(result.output().contains("Exception"));
        assertFalse(result.output().contains("at dev.noah"));
    }

    private static int execute(Path workingDirectory, String... args) {
        return executeCapturing(workingDirectory, args).exitCode();
    }

    private static int executeWithInput(Path workingDirectory, String input, String... args) {
        return executeCapturingWithInput(workingDirectory, input, args).exitCode();
    }

    private static int executeWithApi(ApiServer server, Path workingDirectory, String... args) {
        return executeCapturingWithApi(server, workingDirectory, args).exitCode();
    }

    private static int executeWithApiAndInput(ApiServer server, Path workingDirectory, String input, String... args) {
        return executeCapturingWithApiAndInput(server, workingDirectory, input, args).exitCode();
    }

    private static CliResult executeCapturing(Path workingDirectory, String... args) {
        return executeCapturingWithInput(workingDirectory, "", args);
    }

    private static CliResult executeCapturingWithApi(ApiServer server, Path workingDirectory, String... args) {
        return executeCapturingWithApiAndInput(server, workingDirectory, "", args);
    }

    private static CliResult executeCapturingWithInput(Path workingDirectory, String input, String... args) {
        return executeCapturing(new PluginLockCli(), workingDirectory, input, args);
    }

    private static CliResult executeCapturingWithApiAndInput(ApiServer server, Path workingDirectory, String input, String... args) {
        ServerDownloads downloads = new ServerDownloads(HttpClient.newHttpClient(), server.paperBaseUri(), server.purpurBaseUri());
        return executeCapturing(new PluginLockCli(downloads), workingDirectory, input, args);
    }

    private static CliResult executeCapturing(PluginLockCli cli, Path workingDirectory, String input, String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = PluginLockCli.commandLine(cli);
        commandLine.setOut(new PrintWriter(output, true));
        commandLine.setErr(new PrintWriter(output, true));
        String[] commandArgs = new String[args.length + 2];
        commandArgs[0] = "--project-dir";
        commandArgs[1] = workingDirectory.toString();
        System.arraycopy(args, 0, commandArgs, 2, args.length);
        InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
            return new CliResult(commandLine.execute(commandArgs), output.toString());
        } finally {
            System.setIn(originalIn);
        }
    }

    private record CliResult(int exitCode, String output) {
    }

    private static LockedPlugin lockedPlugin(String id, String fileName, Path source) throws Exception {
        LockedPlugin plugin = new LockedPlugin();
        plugin.setId(id);
        plugin.setProvider("modrinth");
        plugin.setName("Local");
        plugin.setFileName(fileName);
        plugin.setDownloadUrl(source.toUri().toString());
        plugin.setSha512(dev.noah.pluginlock.core.PluginInstaller.sha512(source));
        return plugin;
    }

    private static ApiServer apiServer() throws IOException {
        ApiServer server = new ApiServer();
        server.json("/paper/projects/paper", """
                {
                  "project":{"id":"paper","name":"Paper"},
                  "versions":{"26.1":["26.1.2"],"1.21":["1.21.11","1.21.10"],"1.20":["1.20.6"]}
                }
                """);
        server.json("/paper/projects/paper/versions/26.1.2/builds", """
                [{
                  "id":140,
                  "channel":"STABLE",
                  "downloads":{"server:default":{"name":"paper-26.1.2-140.jar","checksums":{"sha256":"%s"},"size":123,"url":"%s"}}
                }]
                """.formatted(sha256("paper latest jar"), server.uri("/downloads/paper-latest.jar")));
        server.json("/paper/projects/paper/versions/1.21.4/builds", """
                [{
                  "id":101,
                  "channel":"STABLE",
                  "downloads":{"server:default":{"name":"paper-1.21.4-101.jar","checksums":{"sha256":"%s"},"size":100,"url":"%s"}}
                }]
                """.formatted(sha256("paper 1.21.4 jar"), server.uri("/downloads/paper-1.21.4.jar")));
        server.json("/purpur/", """
                {"project":"purpur","versions":["1.20.6","26.1.2","1.21.10","1.21.11"]}
                """);
        server.json("/purpur/1.21.10", """
                {"project":"purpur","version":"1.21.10","builds":{"latest":"2222","all":["2221","2222"]}}
                """);
        server.json("/purpur/1.21.11", """
                {"project":"purpur","version":"1.21.11","builds":{"latest":"3333","all":["3333"]}}
                """);
        server.bytes("/downloads/paper-latest.jar", "paper latest jar");
        server.bytes("/downloads/paper-1.21.4.jar", "paper 1.21.4 jar");
        server.bytes("/purpur/1.21.10/2222/download", "purpur 1.21.10 jar");
        server.bytes("/purpur/1.21.11/3333/download", "purpur 1.21.11 jar");
        return server;
    }

    private static String sha256(String body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class ApiServer implements AutoCloseable {
        private final HttpServer server;
        private final Map<String, byte[]> responses = new HashMap<>();
        private final Map<String, Integer> requestCounts = new HashMap<>();

        ApiServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        URI paperBaseUri() {
            return baseUri().resolve("paper/");
        }

        URI purpurBaseUri() {
            return baseUri().resolve("purpur/");
        }

        void json(String path, String body) {
            responses.put(path, body.getBytes(StandardCharsets.UTF_8));
        }

        void bytes(String path, String body) {
            responses.put(path, body.getBytes(StandardCharsets.UTF_8));
        }

        int requestCount(String path) {
            return requestCounts.getOrDefault(path, 0);
        }

        URI uri(String path) {
            return baseUri().resolve(path.substring(1));
        }

        private URI baseUri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        private void handle(HttpExchange exchange) throws IOException {
            requestCounts.merge(exchange.getRequestURI().toString(), 1, Integer::sum);
            byte[] body = responses.get(exchange.getRequestURI().toString());
            if (body == null) {
                body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, body.length);
            } else {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
            }
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
