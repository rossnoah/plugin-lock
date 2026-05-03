package dev.noah.pluginlock.core.workflow;

import dev.noah.pluginlock.core.DownloadProgress;
import dev.noah.pluginlock.core.PluginInstaller;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginRequest;
import dev.noah.pluginlock.core.project.ProjectPaths;
import dev.noah.pluginlock.core.server.ServerDownloads;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallWorkflowTest {
    @Test
    void appliesRequestsWritesLockAndInstallsPluginJar() throws Exception {
        java.nio.file.Path tempDir = Files.createTempDirectory("plugin-lock-install");
        try {
            java.nio.file.Path source = tempDir.resolve("source.jar");
            Files.writeString(source, "plugin jar");
            java.nio.file.Path pluginsDir = tempDir.resolve("plugins");

            ProjectService projectService = new ProjectService(
                    new ProjectPaths(tempDir),
                    new ServerDownloads(HttpClient.newHttpClient()),
                    ignored -> lockFor(source)
            );

            InstallWorkflow.InstallWorkflowResult result = new InstallWorkflow(projectService)
                    .install(List.of(new PluginRequest("local", "modrinth", "latest")), pluginsDir, null, DownloadProgress.NONE);

            assertTrue(result.manifestChanged());
            assertTrue(result.lockChanged());
            assertEquals(1, result.installResult().installedCount());
            assertTrue(Files.exists(pluginsDir.resolve("local.jar")));
            assertEquals(1, PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE)).getPlugins().size());
            assertEquals(1, PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE)).getPlugins().size());
        } finally {
            Files.deleteIfExists(tempDir.resolve("plugins/local.jar"));
            Files.deleteIfExists(tempDir.resolve("plugins"));
            Files.deleteIfExists(tempDir.resolve("source.jar"));
            Files.deleteIfExists(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
            Files.deleteIfExists(tempDir.resolve(PluginLockFiles.LOCK_FILE));
            Files.deleteIfExists(tempDir);
        }
    }

    private static PluginLock lockFor(java.nio.file.Path source) throws Exception {
        LockedPlugin plugin = new LockedPlugin();
        plugin.setProvider("modrinth");
        plugin.setId("local");
        plugin.setFileName("local.jar");
        plugin.setDownloadUrl(source.toUri().toString());
        plugin.setSha512(PluginInstaller.sha512(source));
        plugin.setSize(Files.size(source));
        PluginLock lock = new PluginLock();
        lock.setPlugins(List.of(plugin));
        return lock;
    }
}
