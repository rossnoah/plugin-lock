package dev.noah.pluginlock.core.workflow;

import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginRequest;
import dev.noah.pluginlock.core.project.ProjectPaths;
import dev.noah.pluginlock.core.server.ServerDownloads;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RemoveWorkflowTest {
    @Test
    void removesManifestRequestLockEntryAndInstalledJar() throws Exception {
        java.nio.file.Path tempDir = Files.createTempDirectory("plugin-lock-remove");
        try {
            java.nio.file.Path pluginsDir = tempDir.resolve("plugins");
            Files.createDirectories(pluginsDir);
            Files.writeString(pluginsDir.resolve("local.jar"), "plugin jar");

            PluginManifest manifest = new PluginManifest();
            manifest.setPlugins(new java.util.ArrayList<>(List.of(new PluginRequest("local", "modrinth", "latest"))));
            PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

            LockedPlugin plugin = new LockedPlugin();
            plugin.setProvider("modrinth");
            plugin.setId("local");
            plugin.setFileName("local.jar");
            PluginLock lock = new PluginLock();
            lock.setPlugins(new java.util.ArrayList<>(List.of(plugin)));
            PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

            ProjectService projectService = new ProjectService(new ProjectPaths(tempDir), new ServerDownloads(HttpClient.newHttpClient()));
            RemoveWorkflow.RemoveResult result = new RemoveWorkflow(projectService).remove(List.of("local"), pluginsDir);

            assertEquals(1, result.removedLockedPlugins().size());
            assertEquals(1, result.removedManifestRequests().size());
            assertFalse(Files.exists(pluginsDir.resolve("local.jar")));
            assertEquals(0, PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE)).getPlugins().size());
            assertEquals(0, PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE)).getPlugins().size());
        } finally {
            Files.deleteIfExists(tempDir.resolve("plugins/local.jar"));
            Files.deleteIfExists(tempDir.resolve("plugins"));
            Files.deleteIfExists(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
            Files.deleteIfExists(tempDir.resolve(PluginLockFiles.LOCK_FILE));
            Files.deleteIfExists(tempDir);
        }
    }
}
