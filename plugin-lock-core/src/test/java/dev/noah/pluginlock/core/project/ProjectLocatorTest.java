package dev.noah.pluginlock.core.project;

import dev.noah.pluginlock.core.PluginLockFiles;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectLocatorTest {
    @Test
    void detectsDirectoryWithLockFiles() throws Exception {
        java.nio.file.Path tempDir = Files.createTempDirectory("plugin-lock-project");
        try {
            Files.writeString(tempDir.resolve(PluginLockFiles.LOCK_FILE), "{}");

            assertEquals(tempDir.toAbsolutePath().normalize(), ProjectLocator.detectProjectDir(tempDir));
        } finally {
            Files.deleteIfExists(tempDir.resolve(PluginLockFiles.LOCK_FILE));
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void detectsParentWhenConfiguredDirectoryIsPluginsDirectory() throws Exception {
        java.nio.file.Path tempDir = Files.createTempDirectory("plugin-lock-project");
        try {
            java.nio.file.Path plugins = tempDir.resolve("plugins");
            Files.createDirectories(plugins);

            assertEquals(tempDir.toAbsolutePath().normalize(), ProjectLocator.detectProjectDir(plugins));
        } finally {
            Files.deleteIfExists(tempDir.resolve("plugins"));
            Files.deleteIfExists(tempDir);
        }
    }
}
