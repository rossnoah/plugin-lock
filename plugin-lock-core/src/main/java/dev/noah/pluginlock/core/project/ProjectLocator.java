package dev.noah.pluginlock.core.project;

import dev.noah.pluginlock.core.PluginLockFiles;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ProjectLocator {
    private ProjectLocator() {
    }

    public static Path detectProjectDir(Path configuredProjectDir) {
        Path current = configuredProjectDir.toAbsolutePath().normalize();
        if (hasProjectFiles(current)) {
            return current;
        }
        Path fileName = current.getFileName();
        if (fileName != null && "plugins".equalsIgnoreCase(fileName.toString()) && current.getParent() != null) {
            return current.getParent();
        }
        Path pluginsChild = current.resolve("plugins");
        if (Files.isDirectory(pluginsChild)) {
            return current;
        }
        return current;
    }

    private static boolean hasProjectFiles(Path directory) {
        return Files.exists(directory.resolve(PluginLockFiles.MANIFEST_FILE))
                || Files.exists(directory.resolve(PluginLockFiles.LOCK_FILE));
    }
}
