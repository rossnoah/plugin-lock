package dev.noah.pluginlock.core.project;

import dev.noah.pluginlock.core.PluginLockFiles;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectPathsTest {
    @Test
    void resolvesProjectFilesAndPluginDirectory() {
        Path projectRoot = Path.of(System.getProperty("java.io.tmpdir"), "plugin-lock-project")
                .toAbsolutePath()
                .normalize();
        Path absolutePluginsDir = projectRoot.resolveSibling("plugins");
        ProjectPaths paths = new ProjectPaths(projectRoot);

        assertEquals(projectRoot.resolve(PluginLockFiles.MANIFEST_FILE), paths.manifestPath());
        assertEquals(projectRoot.resolve(PluginLockFiles.LOCK_FILE), paths.lockPath());
        assertEquals(projectRoot.resolve("plugins"), paths.pluginsDir(Path.of("plugins")));
        assertEquals(absolutePluginsDir, paths.pluginsDir(absolutePluginsDir));
    }
}
