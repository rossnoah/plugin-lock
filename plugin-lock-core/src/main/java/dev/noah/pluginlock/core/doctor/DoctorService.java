package dev.noah.pluginlock.core.doctor;

import dev.noah.pluginlock.core.PluginInstaller;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.manifest.ManifestEditor;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.LockedServer;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.project.ProjectPaths;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class DoctorService {
    public DoctorReport check(ProjectPaths paths, Path pluginsDir) throws Exception {
        Path manifestPath = paths.manifestPath();
        Path lockfilePath = paths.lockPath();
        List<DoctorCheck> checks = new ArrayList<>();

        PluginManifest manifest = null;
        PluginLock lock = null;
        if (Files.exists(manifestPath)) {
            manifest = PluginLockFiles.readManifest(manifestPath);
            checks.add(DoctorCheck.ok("manifest", PluginLockFiles.MANIFEST_FILE + " exists"));
        } else {
            checks.add(DoctorCheck.warning("manifest", PluginLockFiles.MANIFEST_FILE + " is missing"));
        }
        if (Files.exists(lockfilePath)) {
            lock = PluginLockFiles.readLock(lockfilePath);
            checks.add(DoctorCheck.ok("lockfile", PluginLockFiles.LOCK_FILE + " exists"));
        } else {
            checks.add(DoctorCheck.error("lockfile", PluginLockFiles.LOCK_FILE + " is missing"));
        }

        if (manifest != null && lock != null) {
            compareProjectMetadata(manifest, lock, checks);
            checkManifestLockCoverage(manifest, lock, checks);
        }
        if (lock != null) {
            checkServerJar(paths.root(), lock, checks);
            checkPluginJars(pluginsDir, lock, checks);
            lock.getPlugins().stream()
                    .map(LockedPlugin::getCompatibilityWarning)
                    .filter(warning -> warning != null && !warning.isBlank())
                    .forEach(warning -> checks.add(DoctorCheck.warning("compatibility", warning)));
        }

        return new DoctorReport(checks);
    }

    private static void compareProjectMetadata(PluginManifest manifest, PluginLock lock, List<DoctorCheck> checks) {
        if (same(manifest.getMinecraftVersion(), lock.getMinecraftVersion())) {
            checks.add(DoctorCheck.ok("minecraft", "Manifest and lockfile Minecraft versions match"));
        } else {
            checks.add(DoctorCheck.error("minecraft", "Manifest Minecraft version " + blank(manifest.getMinecraftVersion())
                    + " does not match lockfile " + blank(lock.getMinecraftVersion())));
        }
        if (same(manifest.getLoader(), lock.getLoader())) {
            checks.add(DoctorCheck.ok("loader", "Manifest and lockfile loaders match"));
        } else {
            checks.add(DoctorCheck.error("loader", "Manifest loader " + blank(manifest.getLoader())
                    + " does not match lockfile " + blank(lock.getLoader())));
        }
    }

    private static void checkManifestLockCoverage(PluginManifest manifest, PluginLock lock, List<DoctorCheck> checks) {
        List<String> missing = manifest.getPlugins().stream()
                .filter(request -> lock.getPlugins().stream().noneMatch(plugin -> ManifestEditor.samePlugin(
                        request.getProvider(), request.getId(), plugin.getProvider(), plugin.getId())))
                .map(request -> request.getProvider() + ":" + request.getId())
                .toList();
        if (missing.isEmpty()) {
            checks.add(DoctorCheck.ok("plugins", "Lockfile covers all manifest plugins"));
        } else {
            checks.add(DoctorCheck.error("plugins", "Lockfile is missing manifest plugin(s): " + String.join(", ", missing)));
        }
    }

    private static void checkServerJar(Path projectDir, PluginLock lock, List<DoctorCheck> checks) throws Exception {
        LockedServer server = lock.getServer();
        if (server == null || server.getFileName() == null || server.getFileName().isBlank()) {
            checks.add(DoctorCheck.warning("server", "No locked server jar recorded"));
            return;
        }
        Path jar = projectDir.resolve(server.getFileName());
        if (Files.notExists(jar)) {
            checks.add(DoctorCheck.error("server", "Missing server jar " + server.getFileName()));
            return;
        }
        if (server.getSha256() == null || server.getSha256().isBlank()) {
            checks.add(DoctorCheck.warning("server", "Server jar exists but no SHA-256 is recorded"));
            return;
        }
        String actual = sha256(jar);
        if (server.getSha256().equalsIgnoreCase(actual)) {
            checks.add(DoctorCheck.ok("server", "Server jar hash matches"));
        } else {
            checks.add(DoctorCheck.error("server", "Server jar hash mismatch for " + server.getFileName()));
        }
    }

    private static void checkPluginJars(Path pluginsDir, PluginLock lock, List<DoctorCheck> checks) throws Exception {
        if (lock.getPlugins().isEmpty()) {
            checks.add(DoctorCheck.ok("plugins", "No locked plugins"));
            return;
        }
        for (LockedPlugin plugin : lock.getPlugins()) {
            Path jar = pluginsDir.resolve(plugin.getFileName());
            String label = plugin.getProvider() + ":" + plugin.getId();
            if (Files.notExists(jar)) {
                checks.add(DoctorCheck.error("plugin", "Missing plugin jar " + plugin.getFileName() + " for " + label));
                continue;
            }
            if (plugin.getSha512() != null && !plugin.getSha512().isBlank()) {
                String actual = PluginInstaller.sha512(jar);
                checks.add(plugin.getSha512().equalsIgnoreCase(actual)
                        ? DoctorCheck.ok("plugin", label + " hash matches")
                        : DoctorCheck.error("plugin", label + " SHA-512 mismatch"));
            } else if (plugin.getSha256() != null && !plugin.getSha256().isBlank()) {
                String actual = sha256(jar);
                checks.add(plugin.getSha256().equalsIgnoreCase(actual)
                        ? DoctorCheck.ok("plugin", label + " hash matches")
                        : DoctorCheck.error("plugin", label + " SHA-256 mismatch"));
            } else {
                checks.add(DoctorCheck.warning("plugin", label + " has no supported recorded hash"));
            }
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static boolean same(String left, String right) {
        return blank(left).equalsIgnoreCase(blank(right));
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
