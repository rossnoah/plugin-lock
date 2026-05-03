package dev.noah.pluginlock.core.workflow;

import dev.noah.pluginlock.core.DownloadProgress;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateWorkflowTest {
    @Test
    void updatesSelectedPluginFromResolvedLock() throws Exception {
        java.nio.file.Path tempDir = Files.createTempDirectory("plugin-lock-update");
        try {
            PluginManifest manifest = new PluginManifest();
            manifest.setPlugins(List.of(new PluginRequest("local", "modrinth", "latest")));
            PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);
            PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lockWithPlugin("old"));

            ProjectService projectService = new ProjectService(
                    new ProjectPaths(tempDir),
                    new ServerDownloads(HttpClient.newHttpClient()),
                    ignored -> lockWithPlugin("new")
            );

            UpdateWorkflow.UpdateResult result = new UpdateWorkflow(projectService)
                    .update(List.of("local"), false, DownloadProgress.NONE);

            assertTrue(result.lockChanged());
            assertEquals(1, result.updatedPluginCount());
            assertEquals("new", PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE))
                    .getPlugins().getFirst().getVersionName());
        } finally {
            Files.deleteIfExists(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
            Files.deleteIfExists(tempDir.resolve(PluginLockFiles.LOCK_FILE));
            Files.deleteIfExists(tempDir);
        }
    }

    private static PluginLock lockWithPlugin(String version) {
        LockedPlugin plugin = new LockedPlugin();
        plugin.setProvider("modrinth");
        plugin.setId("local");
        plugin.setVersionName(version);
        PluginLock lock = new PluginLock();
        lock.setPlugins(new java.util.ArrayList<>(List.of(plugin)));
        return lock;
    }
}
