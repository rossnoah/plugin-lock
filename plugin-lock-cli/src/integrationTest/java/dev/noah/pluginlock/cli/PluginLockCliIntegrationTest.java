package dev.noah.pluginlock.cli;

import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLockCliIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void initCreatesManifestInWorkingDirectory() throws Exception {
        try (ApiServer server = apiServer()) {
            int exitCode = executeWithApi(server, tempDir, "init", "--minecraft", "1.21.4", "--server", "paper", "--yes");

            assertEquals(0, exitCode);
            PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
            PluginLock lock = PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE));
            assertEquals("1.21.4", manifest.getMinecraftVersion());
            assertEquals("paper", manifest.getLoader());
            assertEquals("paper", lock.getServer().getProvider());
            assertEquals("1.21.4", lock.getServer().getMinecraftVersion());
            assertEquals("101", lock.getServer().getBuild());
            assertEquals("paper 1.21.4 jar", Files.readString(tempDir.resolve("paper-1.21.4-101.jar")));
        }
    }

    @Test
    void detectsParentServerDirectoryWhenProjectDirIsPluginsDirectory() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);

        try (ApiServer server = apiServer()) {
            int exitCode = executeWithApi(server, pluginsDir, "init", "--minecraft", "1.21.4", "--server", "paper", "--yes");

            assertEquals(0, exitCode);
            assertTrue(Files.exists(tempDir.resolve(PluginLockFiles.MANIFEST_FILE)));
            assertTrue(Files.exists(tempDir.resolve(PluginLockFiles.LOCK_FILE)));
            assertTrue(Files.notExists(pluginsDir.resolve(PluginLockFiles.MANIFEST_FILE)));
        }
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
    void initAcceptsLatestPaperVersionFromApiWithYes() throws Exception {
        try (ApiServer server = apiServer()) {
            int exitCode = executeWithApi(server, tempDir, "init", "--yes");

            PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
            PluginLock lock = PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE));
            assertEquals(0, exitCode);
            assertEquals("26.1.2", manifest.getMinecraftVersion());
            assertEquals("paper", manifest.getLoader());
            assertEquals("paper-26.1.2-140.jar", lock.getServer().getFileName());
            assertEquals(sha256("paper latest jar"), lock.getServer().getSha256());
            assertEquals("paper latest jar", Files.readString(tempDir.resolve("paper-26.1.2-140.jar")));
        }
    }

    @Test
    void initSkipsServerDownloadWhenMatchingJarAlreadyExists() throws Exception {
        try (ApiServer server = apiServer()) {
            Files.writeString(tempDir.resolve("paper-26.1.2-140.jar"), "paper latest jar");

            int exitCode = executeWithApi(server, tempDir, "init", "--yes");

            assertEquals(0, exitCode);
            assertEquals(0, server.requestCount("/downloads/paper-latest.jar"));
            assertEquals("paper latest jar", Files.readString(tempDir.resolve("paper-26.1.2-140.jar")));
        }
    }

    @Test
    void initFailsCleanlyWhenServerDownloadHashMismatches() throws Exception {
        try (ApiServer server = apiServer()) {
            server.bytes("/downloads/paper-latest.jar", "tampered jar");

            CliResult result = executeCapturingWithApi(server, tempDir, "init", "--yes");

            assertEquals(1, result.exitCode());
            assertTrue(result.output().contains("Error: SHA-256 mismatch for server jar"));
            assertTrue(Files.notExists(tempDir.resolve("paper-26.1.2-140.jar")));
            assertFalse(result.output().contains("Exception"));
        }
    }

    @Test
    void initPromptsForServerAndVersionFromApis() throws Exception {
        try (ApiServer server = apiServer()) {
            int exitCode = executeWithApiAndInput(server, tempDir, "2\n2\n", "init");

            PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
            PluginLock lock = PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE));
            assertEquals(0, exitCode);
            assertEquals("1.21.11", manifest.getMinecraftVersion());
            assertEquals("paper", manifest.getLoader());
            assertEquals("purpur", lock.getServer().getProvider());
            assertEquals("3333", lock.getServer().getBuild());
            assertEquals("purpur 1.21.11 jar", Files.readString(tempDir.resolve("purpur-1.21.11-3333.jar")));
        }
    }

    @Test
    void initUsesDefaultsForBlankPromptAnswers() throws Exception {
        try (ApiServer server = apiServer()) {
            int exitCode = executeWithApiAndInput(server, tempDir, "\n\n", "init");

            PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
            assertEquals(0, exitCode);
            assertEquals("26.1.2", manifest.getMinecraftVersion());
            assertEquals("paper", manifest.getLoader());
        }
    }

    @Test
    void initReportsAlreadyInitializedWhenManifestExists() throws Exception {
        try (ApiServer server = apiServer()) {
            assertEquals(0, executeWithApi(server, tempDir, "init", "--yes"));

            CliResult result = executeCapturingWithApi(server, tempDir, "init", "--yes");

            assertEquals(0, result.exitCode());
            assertTrue(result.output().contains("Already initialized"));
            assertFalse(result.output().contains("Exception"));
        }
    }

    @Test
    void addCreatesManifestWhenMissingAndReplacesDuplicateProviderEntry() throws Exception {
        CliResult add = executeCapturing(tempDir, "add", "luckperms", "--version", "latest", "--yes");
        CliResult alreadyAdded = executeCapturing(tempDir, "add", "luckperms", "--version", "latest", "--yes");
        CliResult update = executeCapturing(tempDir, "add", "luckperms", "--version", "v5", "--yes");

        PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
        assertEquals(0, add.exitCode());
        assertEquals(0, alreadyAdded.exitCode());
        assertEquals(0, update.exitCode());
        assertTrue(add.output().contains("Added luckperms"));
        assertTrue(alreadyAdded.output().contains("Already added luckperms"));
        assertTrue(update.output().contains("Updated luckperms"));
        assertEquals(1, manifest.getPlugins().size());
        assertEquals("luckperms", manifest.getPlugins().getFirst().getId());
        assertEquals("v5", manifest.getPlugins().getFirst().getVersion());
    }

    @Test
    void addAcceptsProviderAndVersionShorthand() throws Exception {
        assertEquals(0, execute(tempDir, "add", "hangar:PlaceholderAPI@2.11.6", "--yes"));

        PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
        assertEquals(1, manifest.getPlugins().size());
        assertEquals("hangar", manifest.getPlugins().getFirst().getProvider());
        assertEquals("PlaceholderAPI", manifest.getPlugins().getFirst().getId());
        assertEquals("2.11.6", manifest.getPlugins().getFirst().getVersion());
    }

    @Test
    void addAcceptsVersionShorthandWithoutProvider() throws Exception {
        assertEquals(0, execute(tempDir, "add", "luckperms@5.4.0", "--provider", "modrinth", "--yes"));

        PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
        assertEquals("modrinth", manifest.getPlugins().getFirst().getProvider());
        assertEquals("luckperms", manifest.getPlugins().getFirst().getId());
        assertEquals("5.4.0", manifest.getPlugins().getFirst().getVersion());
    }

    @Test
    void installAcceptsVersionShorthandWithoutProvider() throws Exception {
        Path source = tempDir.resolve("source.jar");
        Files.writeString(source, "jar body");

        PluginLockCli cli = new PluginLockCli(new ServerDownloads(HttpClient.newHttpClient()), manifest -> {
            assertEquals("luckperms", manifest.getPlugins().getFirst().getId());
            assertEquals("modrinth", manifest.getPlugins().getFirst().getProvider());
            assertEquals("5.4.0", manifest.getPlugins().getFirst().getVersion());

            PluginLock lock = new PluginLock();
            lock.setMinecraftVersion("1.21.4");
            lock.setLoader("paper");
            lock.setPlugins(java.util.List.of(lockedPlugin("luckperms", "luckperms.jar", source)));
            return lock;
        });

        CliResult result = executeCapturing(cli, tempDir, "", "install", "luckperms@5.4.0",
                "--provider", "modrinth", "--minecraft", "1.21.4", "--server", "paper", "--yes");

        assertEquals(0, result.exitCode());
        PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
        assertEquals("luckperms", manifest.getPlugins().getFirst().getId());
        assertEquals("5.4.0", manifest.getPlugins().getFirst().getVersion());
        assertTrue(Files.exists(tempDir.resolve("plugins/luckperms.jar")));
    }

    @Test
    void addProviderShorthandOverridesProviderOption() throws Exception {
        assertEquals(0, execute(tempDir, "add", "hangar:Chunky", "--provider", "modrinth", "--yes"));

        PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
        assertEquals("hangar", manifest.getPlugins().getFirst().getProvider());
        assertEquals("Chunky", manifest.getPlugins().getFirst().getId());
    }

    @Test
    void addSwitchesProviderForSameNormalizedPluginId() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest("viaversion", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        CliResult result = executeCapturing(tempDir, "add", "hangar:ViaVersion", "--yes");

        PluginManifest updated = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Switched ViaVersion to hangar:ViaVersion"));
        assertEquals(1, updated.getPlugins().size());
        assertEquals("hangar", updated.getPlugins().getFirst().getProvider());
        assertEquals("ViaVersion", updated.getPlugins().getFirst().getId());
    }

    @Test
    void addRejectsBlankPluginIdFromProviderShorthand() {
        CliResult result = executeCapturing(tempDir, "add", "modrinth:", "--yes");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("Plugin id cannot be blank"));
        assertFalse(result.output().contains("Exception"));
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

        CliResult result = executeCapturing(tempDir, "install", "--plugins-dir", tempDir.resolve("plugins").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Installed 1 locked plugin(s)"));
        assertTrue(Files.exists(tempDir.resolve("plugins/local.jar")));
    }

    @Test
    void installReportsLockedPluginsAlreadyInstalled() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Path installed = pluginsDir.resolve("local.jar");
        Files.writeString(installed, "jar body");

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        lock.setPlugins(java.util.List.of(lockedPlugin("local", "local.jar", installed)));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        CliResult result = executeCapturing(tempDir, "install", "--plugins-dir", pluginsDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("All 1 locked plugin(s) already installed"));
    }

    @Test
    void installWithPluginReportsRequestedAndLockedCountsSeparately() throws Exception {
        Path existingSource = tempDir.resolve("existing-source.jar");
        Path requestedSource = tempDir.resolve("requested-source.jar");
        Files.writeString(existingSource, "existing jar body");
        Files.writeString(requestedSource, "requested jar body");

        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest("existing", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLockCli cli = new PluginLockCli(new ServerDownloads(HttpClient.newHttpClient()), ignored -> {
            PluginLock lock = new PluginLock();
            lock.setMinecraftVersion("1.21.4");
            lock.setLoader("paper");
            lock.setPlugins(java.util.List.of(
                    lockedPlugin("existing", "existing.jar", existingSource),
                    lockedPlugin("requested", "requested.jar", requestedSource)));
            return lock;
        });

        CliResult result = executeCapturing(cli, tempDir, "", "--json", "install", "requested", "--provider", "modrinth", "--yes");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Installed 1 requested plugin(s); 2 locked plugin(s) checked"));
        assertTrue(result.output().contains("\"count\":1"));
        assertTrue(result.output().contains("\"requestedCount\":1"));
        assertTrue(result.output().contains("\"lockedCount\":2"));
    }

    @Test
    void installWithPluginReportsRequestedPluginAlreadyInstalled() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Path requestedJar = pluginsDir.resolve("requested.jar");
        Files.writeString(requestedJar, "requested jar body");

        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest("requested", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLockCli cli = new PluginLockCli(new ServerDownloads(HttpClient.newHttpClient()), ignored -> {
            PluginLock lock = new PluginLock();
            lock.setMinecraftVersion("1.21.4");
            lock.setLoader("paper");
            lock.setPlugins(java.util.List.of(lockedPlugin("requested", "requested.jar", requestedJar)));
            return lock;
        });

        CliResult result = executeCapturing(cli, tempDir, "", "install", "requested", "--provider", "modrinth",
                "--plugins-dir", pluginsDir.toString(), "--yes");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Requested plugin(s) already installed; 1 locked plugin(s) checked"));
    }

    @Test
    void installSwitchesProviderInsteadOfDuplicatingSamePlugin() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Path requestedJar = pluginsDir.resolve("ViaVersion.jar");
        Files.writeString(requestedJar, "requested jar body");

        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest("viaversion", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLockCli cli = new PluginLockCli(new ServerDownloads(HttpClient.newHttpClient()), resolvedManifest -> {
            assertEquals(1, resolvedManifest.getPlugins().size());
            assertEquals("hangar", resolvedManifest.getPlugins().getFirst().getProvider());
            assertEquals("ViaVersion", resolvedManifest.getPlugins().getFirst().getId());

            PluginLock lock = new PluginLock();
            lock.setMinecraftVersion("1.21.4");
            lock.setLoader("paper");
            lock.setPlugins(java.util.List.of(lockedPlugin("ViaVersion", "hangar", "ViaVersion.jar", requestedJar)));
            return lock;
        });

        CliResult result = executeCapturing(cli, tempDir, "", "install", "hangar:ViaVersion",
                "--plugins-dir", pluginsDir.toString(), "--yes");

        PluginManifest updatedManifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
        PluginLock updatedLock = PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE));
        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Switched provider for 1 plugin(s); Requested plugin(s) already installed; 1 locked plugin(s) checked"));
        assertEquals(1, updatedManifest.getPlugins().size());
        assertEquals("hangar", updatedManifest.getPlugins().getFirst().getProvider());
        assertEquals(1, updatedLock.getPlugins().size());
        assertEquals("hangar", updatedLock.getPlugins().getFirst().getProvider());
    }

    @Test
    void installWithPluginAutoInitializesEmptyProject() throws Exception {
        Path source = tempDir.resolve("source.jar");
        Files.writeString(source, "jar body");

        try (ApiServer server = apiServer()) {
            PluginLockCli cli = new PluginLockCli(new ServerDownloads(HttpClient.newHttpClient(), server.paperBaseUri(), server.purpurBaseUri()), manifest -> {
                PluginLock lock = new PluginLock();
                lock.setMinecraftVersion(manifest.getMinecraftVersion());
                lock.setLoader(manifest.getLoader());
                lock.setPlugins(java.util.List.of(lockedPlugin("local", "local.jar", source)));
                return lock;
            });

            CliResult result = executeCapturing(cli, tempDir, "", "install", "local", "--provider", "modrinth",
                    "--minecraft", "1.21.4", "--server", "paper", "--yes");

            assertEquals(0, result.exitCode());
            assertTrue(Files.exists(tempDir.resolve(PluginLockFiles.MANIFEST_FILE)));
            assertTrue(Files.exists(tempDir.resolve(PluginLockFiles.LOCK_FILE)));
            assertEquals("paper 1.21.4 jar", Files.readString(tempDir.resolve("paper-1.21.4-101.jar")));
            assertTrue(Files.exists(tempDir.resolve("plugins/local.jar")));
        }
    }

    @Test
    void installMinecraftOnlyInitializesEmptyProject() throws Exception {
        try (ApiServer server = apiServer()) {
            CliResult result = executeCapturingWithApi(server, tempDir, "install", "--minecraft", "26.1.2", "--yes");

            PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
            PluginLock lock = PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE));
            assertEquals(0, result.exitCode());
            assertEquals("26.1.2", manifest.getMinecraftVersion());
            assertEquals("paper", manifest.getLoader());
            assertEquals("paper-26.1.2-140.jar", lock.getServer().getFileName());
            assertTrue(result.output().contains("No locked plugins to install"));
        }
    }

    @Test
    void installDoesNotWriteStagedPluginWhenResolutionFails() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        PluginLockCli cli = new PluginLockCli(new ServerDownloads(HttpClient.newHttpClient()), ignored -> {
            throw new IllegalArgumentException("resolver failed");
        });

        CliResult result = executeCapturing(cli, tempDir, "", "install", "broken", "--provider", "modrinth", "--yes");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("resolver failed"));
        assertTrue(PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE)).getPlugins().isEmpty());
        assertTrue(PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE)).getPlugins().isEmpty());
    }

    @Test
    void lockReportsAlreadyLockedWhenLockfileMatchesManifestResolution() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest("local", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLock resolved = new PluginLock();
        resolved.setMinecraftVersion("1.21.4");
        resolved.setLoader("paper");
        resolved.setPlugins(java.util.List.of(pluginRecord("local", "1.0.0")));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), resolved);

        PluginLockCli cli = new PluginLockCli(new ServerDownloads(HttpClient.newHttpClient()), ignored -> {
            PluginLock lock = new PluginLock();
            lock.setMinecraftVersion("1.21.4");
            lock.setLoader("paper");
            lock.setPlugins(java.util.List.of(pluginRecord("local", "1.0.0")));
            return lock;
        });

        CliResult result = executeCapturing(cli, tempDir, "", "lock");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Already locked 1 plugin(s)"));
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
    void cleanInstallReportsWhenThereAreNoLockedPlugins() throws Exception {
        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        CliResult result = executeCapturing(tempDir, "ci");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("No locked plugins to clean install"));
    }

    @Test
    void listShowsLockedServerAndPlugins() throws Exception {
        Path source = tempDir.resolve("source.jar");
        Files.writeString(source, "jar body");

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        dev.noah.pluginlock.core.model.LockedServer server = new dev.noah.pluginlock.core.model.LockedServer();
        server.setProvider("paper");
        server.setMinecraftVersion("1.21.4");
        server.setBuild("101");
        server.setFileName("paper-1.21.4-101.jar");
        lock.setServer(server);
        LockedPlugin plugin = lockedPlugin("luckperms", "LuckPerms-Bukkit-5.5.17.jar", source);
        plugin.setName("LuckPerms");
        plugin.setVersionName("5.5.17");
        lock.setPlugins(java.util.List.of(plugin));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        CliResult result = executeCapturing(tempDir, "list");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Minecraft: 1.21.4"));
        assertTrue(result.output().contains("Server: paper 1.21.4 build 101 (paper-1.21.4-101.jar)"));
        assertTrue(result.output().contains("modrinth:luckperms"));
        assertTrue(result.output().contains("LuckPerms"));
        assertTrue(result.output().contains("5.5.17"));
        assertTrue(result.output().contains("LuckPerms-Bukkit-5.5.17.jar"));
    }

    @Test
    void runPromptsForMemoryOnceAndStoresItInManifest() throws Exception {
        writeLockedServer("paper.jar");

        CliResult firstRun = executeCapturingWithInput(tempDir, "3gb\n", "run", "--dry-run");
        PluginManifest manifest = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
        CliResult secondRun = executeCapturingWithInput(tempDir, "", "run", "--dry-run");

        assertEquals(0, firstRun.exitCode());
        assertTrue(firstRun.output().contains("Server memory [2G]:"));
        assertTrue(firstRun.output().contains("-Xms3G"));
        assertEquals("3G", manifest.getRunMemory());
        assertEquals(0, secondRun.exitCode());
        assertFalse(secondRun.output().contains("Server memory [2G]:"));
        assertTrue(secondRun.output().contains("-Xms3G"));
    }

    @Test
    void runMemoryOptionDoesNotOverwriteStoredMemory() throws Exception {
        writeLockedServer("paper.jar");
        PluginManifest manifest = new PluginManifest();
        manifest.setRunMemory("2G");
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        CliResult result = executeCapturing(tempDir, "run", "--memory", "4G", "--dry-run");
        PluginManifest restored = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));

        assertEquals(0, result.exitCode());
        assertFalse(result.output().contains("Server memory [2G]:"));
        assertTrue(result.output().contains("-Xms4G"));
        assertEquals("2G", restored.getRunMemory());
    }

    @Test
    void listJsonIncludesLockedPlugins() throws Exception {
        writeSingleLockedPlugin("local", "local.jar");

        CliResult result = executeCapturing(tempDir, "--json", "list");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("\"status\":\"success\""));
        assertTrue(result.output().contains("\"command\":\"list\""));
        assertTrue(result.output().contains("\"plugins\":["));
        assertTrue(result.output().contains("\"id\":\"local\""));
        assertTrue(result.output().contains("\"fileName\":\"local.jar\""));
    }

    @Test
    void listFailsCleanlyWhenLockfileIsMissing() {
        CliResult result = executeCapturing(tempDir, "list");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("No server-lock.lock.json found"));
        assertFalse(result.output().contains("Exception"));
    }

    @Test
    void listShowsEmptyLockedPluginSet() throws Exception {
        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        CliResult result = executeCapturing(tempDir, "list");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("No locked plugins."));
    }

    @Test
    void doctorPassesWhenLockfileArtifactsMatch() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Files.writeString(pluginsDir.resolve("local.jar"), "jar body");

        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest("local", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        lock.setPlugins(java.util.List.of(lockedPlugin("local", "local.jar", pluginsDir.resolve("local.jar"))));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        CliResult result = executeCapturing(tempDir, "doctor");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("OK server-lock.json exists"));
        assertTrue(result.output().contains("OK modrinth:local hash matches"));
        assertFalse(result.output().contains("FAIL"));
    }

    @Test
    void doctorFailsWhenLockedPluginJarIsMissing() throws Exception {
        writeSingleLockedPlugin("local", "local.jar");
        Files.delete(tempDir.resolve("plugins/local.jar"));

        CliResult result = executeCapturing(tempDir, "doctor");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("FAIL Missing plugin jar local.jar for modrinth:local"));
    }

    @Test
    void doctorFailsWhenManifestAndLockfileMinecraftVersionDiffer() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.20.6");
        lock.setLoader("paper");
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        CliResult result = executeCapturing(tempDir, "doctor");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("FAIL Manifest Minecraft version 1.21.4 does not match lockfile 1.20.6"));
    }

    @Test
    void doctorFailsWhenPluginHashMismatches() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Path source = pluginsDir.resolve("local.jar");
        Files.writeString(source, "original jar");
        PluginLock lock = new PluginLock();
        lock.setPlugins(java.util.List.of(lockedPlugin("local", "local.jar", source)));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);
        Files.writeString(source, "tampered jar");

        CliResult result = executeCapturing(tempDir, "doctor");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("FAIL modrinth:local SHA-512 mismatch"));
    }

    @Test
    void doctorJsonIncludesFailureStatusAndChecks() throws Exception {
        writeSingleLockedPlugin("local", "local.jar");
        Files.delete(tempDir.resolve("plugins/local.jar"));

        CliResult result = executeCapturing(tempDir, "--json", "doctor");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("\"status\":\"error\""));
        assertTrue(result.output().contains("\"command\":\"doctor\""));
        assertTrue(result.output().contains("\"check\":\"plugin\""));
    }

    @Test
    void doctorFailsWhenServerJarHashMismatches() throws Exception {
        Files.writeString(tempDir.resolve("paper.jar"), "tampered");
        PluginLock lock = new PluginLock();
        dev.noah.pluginlock.core.model.LockedServer server = new dev.noah.pluginlock.core.model.LockedServer();
        server.setProvider("paper");
        server.setMinecraftVersion("1.21.4");
        server.setBuild("101");
        server.setFileName("paper.jar");
        server.setSha256(sha256("expected"));
        lock.setServer(server);
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        CliResult result = executeCapturing(tempDir, "doctor");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("FAIL Server jar hash mismatch for paper.jar"));
    }

    @Test
    void updateRewritesLockfileFromManifest() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest("local", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        LockedPlugin resolvedPlugin = new LockedPlugin();
        resolvedPlugin.setId("local");
        resolvedPlugin.setProvider("modrinth");
        resolvedPlugin.setName("Local");
        resolvedPlugin.setVersionName("2.0.0");
        PluginLockCli cli = new PluginLockCli(new ServerDownloads(HttpClient.newHttpClient()), ignored -> {
            PluginLock lock = new PluginLock();
            lock.setMinecraftVersion("1.21.4");
            lock.setLoader("paper");
            lock.setPlugins(java.util.List.of(resolvedPlugin));
            return lock;
        });

        CliResult result = executeCapturing(cli, tempDir, "", "update");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Updated 1 plugin(s)"));
        assertEquals("2.0.0", PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE)).getPlugins().getFirst().getVersionName());
    }

    @Test
    void updateReportsAlreadyUpToDateWhenResolvedLockMatches() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest("local", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLock existing = new PluginLock();
        existing.setMinecraftVersion("1.21.4");
        existing.setLoader("paper");
        existing.setPlugins(java.util.List.of(pluginRecord("local", "1.0.0")));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), existing);

        PluginLockCli cli = new PluginLockCli(new ServerDownloads(HttpClient.newHttpClient()), ignored -> {
            PluginLock lock = new PluginLock();
            lock.setMinecraftVersion("1.21.4");
            lock.setLoader("paper");
            lock.setPlugins(java.util.List.of(pluginRecord("local", "1.0.0")));
            return lock;
        });

        CliResult result = executeCapturing(cli, tempDir, "", "update");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("1 plugin(s) already up to date"));
    }

    @Test
    void updateSelectedPluginPreservesUnselectedLockedPlugin() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        manifest.setPlugins(java.util.List.of(
                new dev.noah.pluginlock.core.model.PluginRequest("update-me", "modrinth", "latest"),
                new dev.noah.pluginlock.core.model.PluginRequest("keep-me", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        LockedPlugin oldUpdate = pluginRecord("update-me", "1.0.0");
        LockedPlugin oldKeep = pluginRecord("keep-me", "1.0.0");
        PluginLock existing = new PluginLock();
        existing.setPlugins(java.util.List.of(oldUpdate, oldKeep));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), existing);

        PluginLockCli cli = new PluginLockCli(new ServerDownloads(HttpClient.newHttpClient()), ignored -> {
            PluginLock lock = new PluginLock();
            lock.setMinecraftVersion("1.21.4");
            lock.setLoader("paper");
            lock.setPlugins(java.util.List.of(pluginRecord("update-me", "2.0.0"), pluginRecord("keep-me", "2.0.0")));
            return lock;
        });

        CliResult result = executeCapturing(cli, tempDir, "", "update", "update-me");
        PluginLock updated = PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE));

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Updated 1 selected plugin(s)"));
        assertEquals("2.0.0", updated.getPlugins().get(0).getVersionName());
        assertEquals("1.0.0", updated.getPlugins().get(1).getVersionName());
    }

    @Test
    void updateSelectedPluginReportsWhenNothingMatches() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest("local", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLock existing = new PluginLock();
        existing.setPlugins(java.util.List.of(pluginRecord("local", "1.0.0")));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), existing);

        PluginLockCli cli = new PluginLockCli(new ServerDownloads(HttpClient.newHttpClient()), ignored -> {
            PluginLock lock = new PluginLock();
            lock.setMinecraftVersion("1.21.4");
            lock.setLoader("paper");
            lock.setPlugins(java.util.List.of(pluginRecord("local", "2.0.0")));
            return lock;
        });

        CliResult result = executeCapturing(cli, tempDir, "", "update", "missing");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("No locked plugins matched missing"));
    }

    @Test
    void updateInteractiveUpdatesSelectedPluginOnly() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        manifest.setPlugins(java.util.List.of(
                new dev.noah.pluginlock.core.model.PluginRequest("update-me", "modrinth", "latest"),
                new dev.noah.pluginlock.core.model.PluginRequest("keep-me", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLock existing = new PluginLock();
        existing.setPlugins(java.util.List.of(pluginRecord("update-me", "1.0.0"), pluginRecord("keep-me", "1.0.0")));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), existing);

        PluginLockCli cli = new PluginLockCli(new ServerDownloads(HttpClient.newHttpClient()), ignored -> {
            PluginLock lock = new PluginLock();
            lock.setMinecraftVersion("1.21.4");
            lock.setLoader("paper");
            lock.setPlugins(java.util.List.of(pluginRecord("update-me", "2.0.0"), pluginRecord("keep-me", "2.0.0")));
            return lock;
        });

        CliResult result = executeCapturing(cli, tempDir, "1\n", "update", "--interactive");
        PluginLock updated = PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE));

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Select plugins to update:"));
        assertTrue(result.output().contains("Updated 1 selected plugin(s)"));
        assertEquals("2.0.0", updated.getPlugins().get(0).getVersionName());
        assertEquals("1.0.0", updated.getPlugins().get(1).getVersionName());
    }

    @Test
    void updateFailsCleanlyWhenManifestIsMissing() {
        CliResult result = executeCapturing(tempDir, "update");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("No server-lock.json found"));
        assertFalse(result.output().contains("Exception"));
    }

    @Test
    void updateServerRefreshesLockedServerAndDownloadsJar() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), new PluginLock());

        try (ApiServer server = apiServer()) {
            CliResult result = executeCapturingWithApi(server, tempDir, "update", "--server");

            PluginLock lock = PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE));
            assertEquals(0, result.exitCode());
            assertEquals("101", lock.getServer().getBuild());
            assertEquals("paper 1.21.4 jar", Files.readString(tempDir.resolve("paper-1.21.4-101.jar")));
            assertTrue(result.output().contains("Updated server and 0 plugin(s)"));
        }
    }

    @Test
    void updateServerReportsAlreadyUpToDateWhenJarAndLockMatchLatest() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.4");
        manifest.setLoader("paper");
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        dev.noah.pluginlock.core.model.LockedServer lockedServer = new dev.noah.pluginlock.core.model.LockedServer();
        lockedServer.setProvider("paper");
        lockedServer.setMinecraftVersion("1.21.4");
        lockedServer.setBuild("101");
        lockedServer.setFileName("paper-1.21.4-101.jar");
        lockedServer.setSha256(sha256("paper 1.21.4 jar"));
        lockedServer.setSize(100);
        Files.writeString(tempDir.resolve("paper-1.21.4-101.jar"), "paper 1.21.4 jar");

        try (ApiServer server = apiServer()) {
            lockedServer.setDownloadUrl(server.uri("/downloads/paper-1.21.4.jar").toString());
            PluginLock existing = new PluginLock();
            existing.setMinecraftVersion("1.21.4");
            existing.setLoader("paper");
            existing.setServer(lockedServer);
            PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), existing);

            CliResult result = executeCapturingWithApi(server, tempDir, "update", "--server");

            assertEquals(0, result.exitCode());
            assertTrue(result.output().contains("Server and 0 plugin(s) already up to date"), result.output());
            assertEquals(0, server.requestCount("/downloads/paper-1.21.4.jar"));
        }
    }

    @Test
    void updateServerUsesExistingLockedServerProvider() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion("1.21.11");
        manifest.setLoader("paper");
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);
        PluginLock existing = new PluginLock();
        dev.noah.pluginlock.core.model.LockedServer lockedServer = new dev.noah.pluginlock.core.model.LockedServer();
        lockedServer.setProvider("purpur");
        existing.setServer(lockedServer);
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), existing);

        try (ApiServer server = apiServer()) {
            CliResult result = executeCapturingWithApi(server, tempDir, "update", "--server");

            PluginLock lock = PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE));
            assertEquals(0, result.exitCode());
            assertEquals("purpur", lock.getServer().getProvider());
            assertEquals("3333", lock.getServer().getBuild());
            assertEquals("purpur 1.21.11 jar", Files.readString(tempDir.resolve("purpur-1.21.11-3333.jar")));
        }
    }

    @Test
    void searchRejectsUnsupportedProviderWithoutNetwork() {
        CliResult result = executeCapturing(tempDir, "search", "luckperms", "--provider", "unknown");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("Unsupported provider: unknown"));
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
    void removeMatchesPluginNameRecordedInLockfile() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Files.writeString(pluginsDir.resolve("renamed-artifact.jar"), "jar body");

        PluginManifest manifest = new PluginManifest();
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest("provider-slug", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion("1.21.4");
        lock.setLoader("paper");
        LockedPlugin plugin = new LockedPlugin();
        plugin.setId("provider-slug");
        plugin.setProvider("modrinth");
        plugin.setName("RuntimeName");
        plugin.setFileName("renamed-artifact.jar");
        lock.setPlugins(java.util.List.of(plugin));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        int exitCode = execute(tempDir, "rm", "runtimename", "--plugins-dir", pluginsDir.toString());

        assertEquals(0, exitCode);
        assertTrue(Files.notExists(pluginsDir.resolve("renamed-artifact.jar")));
        assertTrue(PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE)).getPlugins().isEmpty());
        assertTrue(PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE)).getPlugins().isEmpty());
    }

    @Test
    void removeMatchesFileNameRecordedInLockfile() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Files.writeString(pluginsDir.resolve("artifact-name.jar"), "jar body");

        PluginLock lock = new PluginLock();
        LockedPlugin plugin = new LockedPlugin();
        plugin.setId("provider-slug");
        plugin.setProvider("modrinth");
        plugin.setName("Provider Slug");
        plugin.setFileName("artifact-name.jar");
        lock.setPlugins(java.util.List.of(plugin));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        int exitCode = execute(tempDir, "rm", "artifact-name", "--plugins-dir", pluginsDir.toString());

        assertEquals(0, exitCode);
        assertTrue(Files.notExists(pluginsDir.resolve("artifact-name.jar")));
        assertTrue(PluginLockFiles.readLock(tempDir.resolve(PluginLockFiles.LOCK_FILE)).getPlugins().isEmpty());
    }

    @Test
    void removeCleansManifestWhenLockfileIsMissing() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setPlugins(java.util.List.of(
                new dev.noah.pluginlock.core.model.PluginRequest("keep", "modrinth", "latest"),
                new dev.noah.pluginlock.core.model.PluginRequest("remove-me", "hangar", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        CliResult result = executeCapturing(tempDir, "rm", "REMOVE-ME");

        PluginManifest restored = PluginLockFiles.readManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE));
        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Removed 1 plugin request(s)"));
        assertEquals(1, restored.getPlugins().size());
        assertEquals("keep", restored.getPlugins().getFirst().getId());
    }

    @Test
    void removeReportsAlreadyRemovedWhenNothingMatchesExistingProject() throws Exception {
        PluginManifest manifest = new PluginManifest();
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest("keep", "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLock lock = new PluginLock();
        lock.setPlugins(java.util.List.of(pluginRecord("keep", "1.0.0")));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);

        CliResult result = executeCapturing(tempDir, "rm", "missing");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Already removed missing"));
    }

    @Test
    void removeFailsCleanlyWhenPluginLockFilesAreMissing() {
        CliResult result = executeCapturing(tempDir, "rm", "missing");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("Error: No server-lock.json or server-lock.lock.json found"));
        assertFalse(result.output().contains("Exception"));
    }

    @Test
    void defaultOutputUsesShortPredictableSuccessMessages() throws Exception {
        writeSingleLockedPlugin("local", "local.jar");

        CliResult result = executeCapturing(tempDir, "rm", "local");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Removed 1 plugin(s)"));
        assertFalse(result.output().contains("deletedFiles:"));
        assertFalse(result.output().contains("\"status\""));
    }

    @Test
    void verboseOutputIncludesStructuredDetailsAsText() throws Exception {
        writeSingleLockedPlugin("local", "local.jar");

        CliResult result = executeCapturing(tempDir, "--verbose", "rm", "local");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Removed 1 plugin(s)"));
        assertTrue(result.output().contains("removed: [local]"));
        assertTrue(result.output().contains("lockRemaining: 0"));
    }

    @Test
    void jsonOutputIncludesStableSuccessEnvelope() throws Exception {
        writeSingleLockedPlugin("local", "local.jar");

        CliResult result = executeCapturing(tempDir, "--json", "rm", "local");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("\"status\":\"success\""));
        assertTrue(result.output().contains("\"command\":\"remove\""));
        assertTrue(result.output().contains("\"removed\":[\"local\"]"));
    }

    @Test
    void jsonOutputIncludesStableErrorEnvelope() {
        CliResult result = executeCapturing(tempDir, "--json", "rm", "missing");

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("\"status\":\"error\""));
        assertTrue(result.output().contains("\"message\":\"No server-lock.json or server-lock.lock.json found. Run `pl init` first.\""));
        assertFalse(result.output().contains("Error:"));
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

    private static int executeWithInput(Path workingDirectory, String input, String... args) {
        return executeCapturingWithInput(workingDirectory, input, args).exitCode();
    }

    private static int executeWithApi(ApiServer server, Path workingDirectory, String... args) {
        return executeCapturingWithApi(server, workingDirectory, args).exitCode();
    }

    private static int executeWithApiAndInput(ApiServer server, Path workingDirectory, String input, String... args) {
        return executeCapturingWithApiAndInput(server, workingDirectory, input, args).exitCode();
    }

    private static CliResult executeCapturing(Path workingDirectory, String... args) {
        return executeCapturingWithInput(workingDirectory, "", args);
    }

    private static CliResult executeCapturingWithApi(ApiServer server, Path workingDirectory, String... args) {
        return executeCapturingWithApiAndInput(server, workingDirectory, "", args);
    }

    private static CliResult executeCapturingWithInput(Path workingDirectory, String input, String... args) {
        return executeCapturing(new PluginLockCli(), workingDirectory, input, args);
    }

    private static CliResult executeCapturingWithApiAndInput(ApiServer server, Path workingDirectory, String input, String... args) {
        ServerDownloads downloads = new ServerDownloads(HttpClient.newHttpClient(), server.paperBaseUri(), server.purpurBaseUri());
        return executeCapturing(new PluginLockCli(downloads), workingDirectory, input, args);
    }

    private static CliResult executeCapturing(PluginLockCli cli, Path workingDirectory, String input, String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = PluginLockCli.commandLine(cli);
        commandLine.setOut(new PrintWriter(output, true));
        commandLine.setErr(new PrintWriter(output, true));
        String[] commandArgs = new String[args.length + 2];
        commandArgs[0] = "--project-dir";
        commandArgs[1] = workingDirectory.toString();
        System.arraycopy(args, 0, commandArgs, 2, args.length);
        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(output));
            System.setErr(new PrintStream(output));
            return new CliResult(commandLine.execute(commandArgs), output.toString());
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private record CliResult(int exitCode, String output) {
    }

    private void writeLockedServer(String fileName) throws Exception {
        PluginLock lock = new PluginLock();
        dev.noah.pluginlock.core.model.LockedServer server = new dev.noah.pluginlock.core.model.LockedServer();
        server.setProvider("paper");
        server.setMinecraftVersion("1.21.4");
        server.setBuild("101");
        server.setFileName(fileName);
        lock.setServer(server);
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);
    }

    private static LockedPlugin lockedPlugin(String id, String fileName, Path source) throws Exception {
        return lockedPlugin(id, "modrinth", fileName, source);
    }

    private static LockedPlugin lockedPlugin(String id, String provider, String fileName, Path source) throws Exception {
        LockedPlugin plugin = new LockedPlugin();
        plugin.setId(id);
        plugin.setProvider(provider);
        plugin.setName("Local");
        plugin.setFileName(fileName);
        plugin.setDownloadUrl(source.toUri().toString());
        plugin.setSha512(dev.noah.pluginlock.core.PluginInstaller.sha512(source));
        return plugin;
    }

    private static LockedPlugin pluginRecord(String id, String version) {
        LockedPlugin plugin = new LockedPlugin();
        plugin.setId(id);
        plugin.setProvider("modrinth");
        plugin.setName(id);
        plugin.setVersionName(version);
        plugin.setFileName(id + ".jar");
        return plugin;
    }

    private void writeSingleLockedPlugin(String id, String fileName) throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Files.writeString(pluginsDir.resolve(fileName), "jar body");

        PluginManifest manifest = new PluginManifest();
        manifest.setPlugins(java.util.List.of(new dev.noah.pluginlock.core.model.PluginRequest(id, "modrinth", "latest")));
        PluginLockFiles.writeManifest(tempDir.resolve(PluginLockFiles.MANIFEST_FILE), manifest);

        PluginLock lock = new PluginLock();
        LockedPlugin plugin = new LockedPlugin();
        plugin.setId(id);
        plugin.setProvider("modrinth");
        plugin.setName("Local");
        plugin.setFileName(fileName);
        lock.setPlugins(java.util.List.of(plugin));
        PluginLockFiles.writeLock(tempDir.resolve(PluginLockFiles.LOCK_FILE), lock);
    }

    private static ApiServer apiServer() throws IOException {
        ApiServer server = new ApiServer();
        server.json("/paper/projects/paper", """
                {
                  "project":{"id":"paper","name":"Paper"},
                  "versions":{"26.1":["26.1.2"],"1.21":["1.21.11","1.21.10"],"1.20":["1.20.6"]}
                }
                """);
        server.json("/paper/projects/paper/versions/26.1.2/builds", """
                [{
                  "id":140,
                  "channel":"STABLE",
                  "downloads":{"server:default":{"name":"paper-26.1.2-140.jar","checksums":{"sha256":"%s"},"size":123,"url":"%s"}}
                }]
                """.formatted(sha256("paper latest jar"), server.uri("/downloads/paper-latest.jar")));
        server.json("/paper/projects/paper/versions/1.21.4/builds", """
                [{
                  "id":101,
                  "channel":"STABLE",
                  "downloads":{"server:default":{"name":"paper-1.21.4-101.jar","checksums":{"sha256":"%s"},"size":100,"url":"%s"}}
                }]
                """.formatted(sha256("paper 1.21.4 jar"), server.uri("/downloads/paper-1.21.4.jar")));
        server.json("/purpur/", """
                {"project":"purpur","versions":["1.20.6","26.1.2","1.21.10","1.21.11"]}
                """);
        server.json("/purpur/1.21.10", """
                {"project":"purpur","version":"1.21.10","builds":{"latest":"2222","all":["2221","2222"]}}
                """);
        server.json("/purpur/1.21.11", """
                {"project":"purpur","version":"1.21.11","builds":{"latest":"3333","all":["3333"]}}
                """);
        server.bytes("/downloads/paper-latest.jar", "paper latest jar");
        server.bytes("/downloads/paper-1.21.4.jar", "paper 1.21.4 jar");
        server.bytes("/purpur/1.21.10/2222/download", "purpur 1.21.10 jar");
        server.bytes("/purpur/1.21.11/3333/download", "purpur 1.21.11 jar");
        return server;
    }

    private static String sha256(String body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class ApiServer implements AutoCloseable {
        private final HttpServer server;
        private final Map<String, byte[]> responses = new HashMap<>();
        private final Map<String, Integer> requestCounts = new HashMap<>();

        ApiServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        URI paperBaseUri() {
            return baseUri().resolve("paper/");
        }

        URI purpurBaseUri() {
            return baseUri().resolve("purpur/");
        }

        void json(String path, String body) {
            responses.put(path, body.getBytes(StandardCharsets.UTF_8));
        }

        void bytes(String path, String body) {
            responses.put(path, body.getBytes(StandardCharsets.UTF_8));
        }

        int requestCount(String path) {
            return requestCounts.getOrDefault(path, 0);
        }

        URI uri(String path) {
            return baseUri().resolve(path.substring(1));
        }

        private URI baseUri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        private void handle(HttpExchange exchange) throws IOException {
            requestCounts.merge(exchange.getRequestURI().toString(), 1, Integer::sum);
            byte[] body = responses.get(exchange.getRequestURI().toString());
            if (body == null) {
                body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, body.length);
            } else {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
            }
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
