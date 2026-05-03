package dev.noah.pluginlock.core.doctor;

import dev.noah.pluginlock.core.PluginInstaller;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginRequest;
import dev.noah.pluginlock.core.project.ProjectPaths;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoctorServiceTest {
    @Test
    void reportsHealthyManifestLockAndPluginJar() throws Exception {
        java.nio.file.Path tempDir = Files.createTempDirectory("plugin-lock-doctor");
        try {
            java.nio.file.Path pluginsDir = tempDir.resolve("plugins");
            Files.createDirectories(pluginsDir);
            java.nio.file.Path jar = pluginsDir.resolve("local.jar");
            Files.writeString(jar, "plugin jar");

            PluginManifest manifest = new PluginManifest();
            manifest.setPlugins(java.util.List.of(new PluginRequest("local", "modrinth", "latest")));
            PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

            PluginLock lock = new PluginLock();
            lock.setMinecraftVersion(manifest.getMinecraftVersion());
            lock.setLoader(manifest.getLoader());
            LockedPlugin plugin = new LockedPlugin();
            plugin.setProvider("modrinth");
            plugin.setId("local");
            plugin.setFileName("local.jar");
            plugin.setSha512(PluginInstaller.sha512(jar));
            lock.setPlugins(java.util.List.of(plugin));
            PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

            DoctorReport report = new DoctorService().check(new ProjectPaths(tempDir), pluginsDir);

            assertFalse(report.hasErrors());
            assertTrue(report.checks().stream().anyMatch(check -> check.message().contains("hash matches")));
        } finally {
            Files.deleteIfExists(tempDir.resolve("plugins/local.jar"));
            Files.deleteIfExists(tempDir.resolve("plugins"));
            Files.deleteIfExists(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
            Files.deleteIfExists(tempDir.resolve(PluginLockFiles.LOCK_FILE));
            Files.deleteIfExists(tempDir);
        }
    }
}
