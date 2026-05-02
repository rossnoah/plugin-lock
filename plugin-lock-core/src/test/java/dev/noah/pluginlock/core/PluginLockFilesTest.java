package dev.noah.pluginlock.core;

import dev.noah.pluginlock.core.model.LockedServer;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginLockFilesTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripsManifest() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        manifest.setPlugins(List.of(new PluginRequest("luckperms", "modrinth", "latest")));

        Path path = tempDir.resolve(PluginLockFiles.MANIFEST_FILE);
        PluginLockFiles.writeManifest(path, manifest);
        PluginManifest restored = PluginLockFiles.readManifest(path);

        assertEquals("1.21.4", restored.getMinecraftVersion());
        assertEquals("paper", restored.getLoader());
        assertEquals("luckperms", restored.getPlugins().getFirst().getId());
    }

    @Test
    void readsYamlManifest() throws Exception {
        Path path = tempDir.resolve("server-lock.yaml");
        java.nio.file.Files.writeString(path, """
                minecraftVersion: 1.21.4
                loader: paper
                plugins:
                  - id: luckperms
                    provider: modrinth
                    version: latest
                """);

        PluginManifest manifest = PluginLockFiles.readManifest(path);

        assertEquals("1.21.4", manifest.getMinecraftVersion());
        assertEquals("paper", manifest.getLoader());
        assertEquals("modrinth", manifest.getPlugins().getFirst().getProvider());
    }

    @Test
    void roundTripsLockWithServerSelection() throws Exception {
        LockedServer server = new LockedServer();
        server.setProvider("paper");
        server.setMinecraftVersion("1.21.11");
        server.setBuild("130");
        server.setFileName("paper-1.21.11-130.jar");
        server.setDownloadUrl("https://example.test/paper.jar");
        server.setSha256("abc123");
        server.setSize(123);
        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.11");
        lock.setLoader("paper");
        lock.setServer(server);

        Path path = tempDir.resolve(PluginLockFiles.LOCK_FILE);
        PluginLockFiles.writeLock(path, lock);
        PluginLock restored = PluginLockFiles.readLock(path);

        assertEquals("paper", restored.getServer().getProvider());
        assertEquals("1.21.11", restored.getServer().getMinecraftVersion());
        assertEquals("130", restored.getServer().getBuild());
        assertEquals("abc123", restored.getServer().getSha256());
    }
}
