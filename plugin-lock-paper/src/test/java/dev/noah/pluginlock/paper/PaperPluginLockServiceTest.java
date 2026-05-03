package dev.noah.pluginlock.paper;

import dev.noah.pluginlock.core.PluginInstaller;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.LockedServer;
import dev.noah.pluginlock.core.model.PluginLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperPluginLockServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void listLinesSummarizeServerAndLockedPlugins() {
        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        LockedServer server = new LockedServer();
        server.setProvider("paper");
        server.setMinecraftVersion("1.21.4");
        server.setBuild("101");
        lock.setServer(server);
        LockedPlugin plugin = plugin("luckperms", "LuckPerms.jar");
        lock.setPlugins(List.of(plugin));

        List<String> lines = new PaperPluginLockService(tempDir).listLines(lock);

        assertEquals("Minecraft 1.21.4 / paper", lines.get(0));
        assertEquals("Server: paper 1.21.4 build 101", lines.get(1));
        assertEquals("modrinth:luckperms -> LuckPerms.jar", lines.get(2));
    }

    @Test
    void listLinesReportsEmptyPluginSet() {
        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");

        List<String> lines = new PaperPluginLockService(tempDir).listLines(lock);

        assertTrue(lines.contains("No locked plugins."));
    }

    @Test
    void doctorPassesWhenFilesAndHashesMatch() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Files.writeString(tempDir.resolve("paper.jar"), "server");
        Path pluginJar = pluginsDir.resolve("LuckPerms.jar");
        Files.writeString(pluginJar, "plugin");

        PluginLock lock = new PluginLock();
        LockedServer server = new LockedServer();
        server.setFileName("paper.jar");
        server.setSha256(sha256("server"));
        lock.setServer(server);
        LockedPlugin plugin = plugin("luckperms", "LuckPerms.jar");
        plugin.setSha512(PluginInstaller.sha512(pluginJar));
        lock.setPlugins(List.of(plugin));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        List<PaperPluginLockService.DoctorCheck> checks = new PaperPluginLockService(tempDir).doctor(lock);

        assertTrue(checks.contains(PaperPluginLockService.DoctorCheck.ok("server-lock.lock.json exists")));
        assertTrue(checks.contains(PaperPluginLockService.DoctorCheck.ok("Server jar hash matches")));
        assertTrue(checks.contains(PaperPluginLockService.DoctorCheck.ok("modrinth:luckperms hash matches")));
    }

    @Test
    void doctorReportsMissingPluginJar() throws Exception {
        PluginLock lock = new PluginLock();
        LockedPlugin plugin = plugin("luckperms", "LuckPerms.jar");
        lock.setPlugins(List.of(plugin));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        List<PaperPluginLockService.DoctorCheck> checks = new PaperPluginLockService(tempDir).doctor(lock);

        assertTrue(checks.contains(PaperPluginLockService.DoctorCheck.error(
                "Missing plugin jar LuckPerms.jar for modrinth:luckperms")));
    }

    @Test
    void doctorReportsPluginHashMismatch() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Path pluginJar = pluginsDir.resolve("LuckPerms.jar");
        Files.writeString(pluginJar, "expected");
        LockedPlugin plugin = plugin("luckperms", "LuckPerms.jar");
        plugin.setSha512(PluginInstaller.sha512(pluginJar));
        Files.writeString(pluginJar, "tampered");
        PluginLock lock = new PluginLock();
        lock.setPlugins(List.of(plugin));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        List<PaperPluginLockService.DoctorCheck> checks = new PaperPluginLockService(tempDir).doctor(lock);

        assertTrue(checks.contains(PaperPluginLockService.DoctorCheck.error("modrinth:luckperms SHA-512 mismatch")));
    }

    @Test
    void doctorReportsCompatibilityWarnings() throws Exception {
        LockedPlugin plugin = plugin("demo", "demo.jar");
        plugin.setCompatibilityWarning("Demo does not explicitly list this Minecraft version.");
        PluginLock lock = new PluginLock();
        lock.setPlugins(List.of(plugin));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        List<PaperPluginLockService.DoctorCheck> checks = new PaperPluginLockService(tempDir).doctor(lock);

        assertTrue(checks.contains(PaperPluginLockService.DoctorCheck.warning(
                "Demo does not explicitly list this Minecraft version.")));
    }

    private static LockedPlugin plugin(String id, String fileName) {
        LockedPlugin plugin = new LockedPlugin();
        plugin.setId(id);
        plugin.setProvider("modrinth");
        plugin.setName(id);
        plugin.setFileName(fileName);
        return plugin;
    }

    private static String sha256(String body) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(body.getBytes(StandardCharsets.UTF_8)));
    }
}
