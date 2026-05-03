package dev.noah.pluginlock.paper;

import dev.noah.pluginlock.core.PluginInstaller;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.LockedServer;
import dev.noah.pluginlock.core.model.PluginLock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class PaperPluginLockService {
    private final Path serverRoot;
    private final Path pluginsDirectory;

    PaperPluginLockService(Path serverRoot) {
        this.serverRoot = serverRoot.toAbsolutePath().normalize();
        this.pluginsDirectory = this.serverRoot.resolve("plugins");
    }

    Path lockfile() {
        return serverRoot.resolve(PluginLockFiles.LOCK_FILE);
    }

    PluginLock readLock() throws IOException {
        return PluginLockFiles.readLock(lockfile());
    }

    void install(PluginLock lock, PluginInstaller installer, Consumer<String> progress)
            throws IOException, InterruptedException {
        installer.install(lock, pluginsDirectory, (fileName, downloadedBytes, totalBytes) -> {
            if (downloadedBytes == 0) {
                progress.accept("Downloading " + fileName + "...");
            }
        });
    }

    List<String> listLines(PluginLock lock) {
        List<String> lines = new ArrayList<>();
        lines.add("Minecraft " + blank(lock.getMinecraftVersion()) + " / " + blank(lock.getLoader()));
        LockedServer server = lock.getServer();
        if (server != null) {
            lines.add("Server: " + blank(server.getProvider())
                    + " " + blank(server.getMinecraftVersion())
                    + " build " + blank(server.getBuild()));
        }
        if (lock.getPlugins().isEmpty()) {
            lines.add("No locked plugins.");
            return lines;
        }
        for (LockedPlugin plugin : lock.getPlugins()) {
            lines.add(pluginLabel(plugin) + " -> " + blank(plugin.getFileName()));
        }
        return lines;
    }

    List<DoctorCheck> doctor(PluginLock lock) throws IOException {
        List<DoctorCheck> checks = new ArrayList<>();
        checks.add(Files.exists(lockfile())
                ? DoctorCheck.ok(PluginLockFiles.LOCK_FILE + " exists")
                : DoctorCheck.error(PluginLockFiles.LOCK_FILE + " is missing"));
        checkServerJar(lock, checks);
        checkPlugins(lock, checks);
        lock.getPlugins().stream()
                .map(LockedPlugin::getCompatibilityWarning)
                .filter(warning -> warning != null && !warning.isBlank())
                .forEach(warning -> checks.add(DoctorCheck.warning(warning)));
        return checks;
    }

    private void checkServerJar(PluginLock lock, List<DoctorCheck> checks) throws IOException {
        LockedServer server = lock.getServer();
        if (server == null || server.getFileName() == null || server.getFileName().isBlank()) {
            checks.add(DoctorCheck.warning("No locked server jar recorded"));
            return;
        }
        Path jar = serverRoot.resolve(server.getFileName());
        if (Files.notExists(jar)) {
            checks.add(DoctorCheck.error("Missing server jar " + server.getFileName()));
            return;
        }
        if (server.getSha256() == null || server.getSha256().isBlank()) {
            checks.add(DoctorCheck.warning("Server jar exists but no SHA-256 is recorded"));
            return;
        }
        String actual = sha256(jar);
        checks.add(server.getSha256().equalsIgnoreCase(actual)
                ? DoctorCheck.ok("Server jar hash matches")
                : DoctorCheck.error("Server jar hash mismatch for " + server.getFileName()));
    }

    private void checkPlugins(PluginLock lock, List<DoctorCheck> checks) throws IOException {
        if (lock.getPlugins().isEmpty()) {
            checks.add(DoctorCheck.ok("No locked plugins"));
            return;
        }
        for (LockedPlugin plugin : lock.getPlugins()) {
            Path jar = pluginsDirectory.resolve(plugin.getFileName());
            if (Files.notExists(jar)) {
                checks.add(DoctorCheck.error("Missing plugin jar " + plugin.getFileName() + " for " + pluginLabel(plugin)));
                continue;
            }
            if (plugin.getSha512() != null && !plugin.getSha512().isBlank()) {
                checks.add(plugin.getSha512().equalsIgnoreCase(PluginInstaller.sha512(jar))
                        ? DoctorCheck.ok(pluginLabel(plugin) + " hash matches")
                        : DoctorCheck.error(pluginLabel(plugin) + " SHA-512 mismatch"));
            } else if (plugin.getSha256() != null && !plugin.getSha256().isBlank()) {
                checks.add(plugin.getSha256().equalsIgnoreCase(sha256(jar))
                        ? DoctorCheck.ok(pluginLabel(plugin) + " hash matches")
                        : DoctorCheck.error(pluginLabel(plugin) + " SHA-256 mismatch"));
            } else {
                checks.add(DoctorCheck.warning(pluginLabel(plugin) + " has no supported recorded hash"));
            }
        }
    }

    static String pluginLabel(LockedPlugin plugin) {
        return blank(plugin.getProvider()) + ":" + blank(plugin.getId());
    }

    private static String sha256(Path path) throws IOException {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var input = new java.security.DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    record DoctorCheck(Status status, String message) {
        static DoctorCheck ok(String message) {
            return new DoctorCheck(Status.OK, message);
        }

        static DoctorCheck warning(String message) {
            return new DoctorCheck(Status.WARNING, message);
        }

        static DoctorCheck error(String message) {
            return new DoctorCheck(Status.ERROR, message);
        }
    }

    enum Status {
        OK,
        WARNING,
        ERROR
    }
}
