package dev.noah.pluginlock.core.project;

import dev.noah.pluginlock.core.PluginLockFiles;

import java.nio.file.Path;

public record ProjectPaths(Path root) {
    public ProjectPaths {
        root = root.toAbsolutePath().normalize();
    }

    public Path manifestPath() {
        return resolve(PluginLockFiles.MANIFEST_FILE);
    }

    public Path lockPath() {
        return resolve(PluginLockFiles.LOCK_FILE);
    }

    public Path pluginsDir(Path configuredPluginsDir) {
        return configuredPluginsDir.isAbsolute() ? configuredPluginsDir : root.resolve(configuredPluginsDir);
    }

    public Path resolve(String fileName) {
        return root.resolve(fileName);
    }
}
