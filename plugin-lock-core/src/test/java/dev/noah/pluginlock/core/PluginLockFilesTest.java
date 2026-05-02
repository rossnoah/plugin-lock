package dev.noah.pluginlock.core;

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
        Path path = tempDir.resolve("plugin-lock.yaml");
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
}
