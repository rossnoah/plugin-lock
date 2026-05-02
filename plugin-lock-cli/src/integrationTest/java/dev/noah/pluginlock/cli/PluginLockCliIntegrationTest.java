package dev.noah.pluginlock.cli;

import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLockCliIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void initCreatesManifestInWorkingDirectory() throws Exception {
        int exitCode = execute(tempDir, "init", "--minecraft", "1.21.4", "--loader", "paper");

        assertEquals(0, exitCode);
        PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
        assertEquals("1.21.4", manifest.getMinecraftVersion());
        assertEquals("paper", manifest.getLoader());
    }

    @Test
    void detectsParentServerDirectoryWhenProjectDirIsPluginsDirectory() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);

        int exitCode = execute(pluginsDir, "init", "--minecraft", "1.21.4", "--loader", "paper");

        assertEquals(0, exitCode);
        assertTrue(Files.exists(tempDir.resolve(PluginLockFiles.MANIFEST_FILE)));
        assertTrue(Files.notExists(pluginsDir.resolve(PluginLockFiles.MANIFEST_FILE)));
    }

    @Test
    void installsIntoDetectedPluginsDirectoryWhenRunFromPluginsDirectory() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Path source = tempDir.resolve("source.jar");
        Files.writeString(source, "jar body");

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        lock.setPlugins(java.util.List.of(lockedPlugin("local", "local.jar", source)));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        int exitCode = execute(pluginsDir, "ci");

        assertEquals(0, exitCode);
        assertTrue(Files.exists(pluginsDir.resolve("local.jar")));
        assertTrue(Files.notExists(pluginsDir.resolve("plugins/local.jar")));
    }

    @Test
    void keepsExplicitNonPluginsProjectDirectoryAsServerRoot() throws Exception {
        Path serverRoot = tempDir.resolve("server");
        Files.createDirectories(serverRoot.resolve("plugins"));

        assertEquals(serverRoot.toAbsolutePath().normalize(), PluginLockCli.detectProjectDir(serverRoot));
    }

    @Test
    void addCreatesManifestWhenMissingAndReplacesDuplicateProviderEntry() throws Exception {
        assertEquals(0, execute(tempDir, "add", "luckperms", "--version", "latest", "--yes"));
        assertEquals(0, execute(tempDir, "add", "luckperms", "--version", "v5", "--yes"));

        PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
        assertEquals(1, manifest.getPlugins().size());
        assertEquals("luckperms", manifest.getPlugins().getFirst().getId());
        assertEquals("v5", manifest.getPlugins().getFirst().getVersion());
    }

    @Test
    void installReadsLockfileAndWritesPluginDirectory() throws Exception {
        Path source = tempDir.resolve("source.jar");
        Files.writeString(source, "jar body");

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        lock.setPlugins(java.util.List.of(lockedPlugin("local", "local.jar", source)));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        int exitCode = execute(tempDir, "install", "--plugins-dir", tempDir.resolve("plugins").toString());

        assertEquals(0, exitCode);
        assertTrue(Files.exists(tempDir.resolve("plugins/local.jar")));
    }

    @Test
    void cleanInstallAliasReinstallsFromLockfileOnly() throws Exception {
        Path source = tempDir.resolve("source.jar");
        Files.writeString(source, "clean jar body");
        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        lock.setPlugins(java.util.List.of(lockedPlugin("local", "local.jar", source)));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        Path installed = tempDir.resolve("plugins/local.jar");
        Files.createDirectories(installed.getParent());
        Files.writeString(installed, "stale");

        int exitCode = execute(tempDir, "ci", "--plugins-dir", tempDir.resolve("plugins").toString());

        assertEquals(0, exitCode);
        assertEquals("clean jar body", Files.readString(installed));
    }

    @Test
    void removeDeletesManifestEntryLockEntryAndInstalledJar() throws Exception {
        Path source = tempDir.resolve("source.jar");
        Files.writeString(source, "jar body");
        PluginManifest manifest = new PluginManifest();
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest("local", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        lock.setPlugins(java.util.List.of(lockedPlugin("local", "local.jar", source)));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);
        Files.createDirectories(tempDir.resolve("plugins"));
        Files.writeString(tempDir.resolve("plugins/local.jar"), "jar body");

        int exitCode = execute(tempDir, "rm", "local", "--plugins-dir", tempDir.resolve("plugins").toString());

        assertEquals(0, exitCode);
        assertTrue(PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE)).getPlugins().isEmpty());
        assertTrue(PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE)).getPlugins().isEmpty());
        assertTrue(Files.notExists(tempDir.resolve("plugins/local.jar")));
    }

    @Test
    void executionErrorsAreReportedWithoutStackTraces() {
        CliResult result = executeCapturing(tempDir, "install", "luckperms", "--provider", "unknown", "--yes");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("Error: Unsupported provider: unknown"));
        assertFalse(result.output().contains("Exception"));
        assertFalse(result.output().contains("at dev.noah"));
    }

    private static int execute(Path workingDirectory, String... args) {
        return executeCapturing(workingDirectory, args).exitCode();
    }

    private static CliResult executeCapturing(Path workingDirectory, String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = PluginLockCli.commandLine(new PluginLockCli());
        commandLine.setOut(new PrintWriter(output, true));
        commandLine.setErr(new PrintWriter(output, true));
        String[] commandArgs = new String[args.length + 2];
        commandArgs[0] = "--project-dir";
        commandArgs[1] = workingDirectory.toString();
        System.arraycopy(args, 0, commandArgs, 2, args.length);
        return new CliResult(commandLine.execute(commandArgs), output.toString());
    }

    private record CliResult(int exitCode, String output) {
    }

    private static LockedPlugin lockedPlugin(String id, String fileName, Path source) throws Exception {
        LockedPlugin plugin = new LockedPlugin();
        plugin.setId(id);
        plugin.setProvider("modrinth");
        plugin.setName("Local");
        plugin.setFileName(fileName);
        plugin.setDownloadUrl(source.toUri().toString());
        plugin.setSha512(dev.noah.pluginlock.core.PluginInstaller.sha512(source));
        return plugin;
    }
}
