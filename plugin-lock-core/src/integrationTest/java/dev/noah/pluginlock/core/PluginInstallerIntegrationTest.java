package dev.noah.pluginlock.core;

import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginInstallerIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void downloadsAndVerifiesPluginJarFromFakeServer() throws Exception {
        byte[] jarBytes = "fake jar contents".getBytes(StandardCharsets.UTF_8);
        try (TestHttpServer server = new TestHttpServer()) {
            server.bytes("/download/fake.jar", jarBytes);
            PluginLock lock = lockWith(plugin(server.baseUri().resolve("download/fake.jar").toString(),
                    PluginInstaller.sha512(writeBytes("source.jar", jarBytes))));

            Path pluginsDir = tempDir.resolve("plugins");
            new PluginInstaller(HttpClient.newHttpClient()).install(lock, pluginsDir);

            assertEquals("fake jar contents", Files.readString(pluginsDir.resolve("fake.jar")));
        }
    }

    @Test
    void skipsDownloadWhenExistingFileAlreadyMatchesHash() throws Exception {
        byte[] jarBytes = "already present".getBytes(StandardCharsets.UTF_8);
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Path existing = pluginsDir.resolve("fake.jar");
        Files.write(existing, jarBytes);

        try (TestHttpServer server = new TestHttpServer()) {
            PluginLock lock = lockWith(plugin(server.baseUri().resolve("download/fake.jar").toString(),
                    PluginInstaller.sha512(existing)));

            new PluginInstaller(HttpClient.newHttpClient()).install(lock, pluginsDir);

            assertEquals(0, server.requestCount());
            assertEquals("already present", Files.readString(existing));
        }
    }

    @Test
    void rejectsHashMismatchFromFakeServer() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.bytes("/download/fake.jar", "tampered".getBytes(StandardCharsets.UTF_8));
            PluginLock lock = lockWith(plugin(server.baseUri().resolve("download/fake.jar").toString(), "0".repeat(128)));

            assertThrows(java.io.IOException.class, () ->
                    new PluginInstaller(HttpClient.newHttpClient()).install(lock, tempDir.resolve("plugins")));
        }
    }

    private Path writeBytes(String name, byte[] body) throws Exception {
        Path path = tempDir.resolve(name);
        Files.write(path, body);
        return path;
    }

    private static PluginLock lockWith(LockedPlugin plugin) {
        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        lock.setPlugins(List.of(plugin));
        return lock;
    }

    private static LockedPlugin plugin(String url, String sha512) {
        LockedPlugin plugin = new LockedPlugin();
        plugin.setId("fake");
        plugin.setProvider("modrinth");
        plugin.setName("Fake");
        plugin.setVersionId("version-1");
        plugin.setVersionName("1.0.0");
        plugin.setFileName("fake.jar");
        plugin.setDownloadUrl(url);
        plugin.setSha512(sha512);
        plugin.setSize(1);
        return plugin;
    }
}
