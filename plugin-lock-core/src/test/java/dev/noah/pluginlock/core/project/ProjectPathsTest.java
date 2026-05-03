package dev.noah.pluginlock.core.project;

import dev.noah.pluginlock.core.PluginLockFiles;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectPathsTest {
    @Test
    void resolvesProjectFilesAndPluginDirectory() {
        ProjectPaths paths = new ProjectPaths(Path.of("/tmp/plugin-lock-project"));

        assertEquals(Path.of("/tmp/plugin-lock-project", PluginLockFiles.MANIFEST_FILE), paths.manifestPath());
        assertEquals(Path.of("/tmp/plugin-lock-project", PluginLockFiles.LOCK_FILE), paths.lockPath());
        assertEquals(Path.of("/tmp/plugin-lock-project/plugins"), paths.pluginsDir(Path.of("plugins")));
        assertEquals(Path.of("/srv/plugins"), paths.pluginsDir(Path.of("/srv/plugins")));
    }
}
