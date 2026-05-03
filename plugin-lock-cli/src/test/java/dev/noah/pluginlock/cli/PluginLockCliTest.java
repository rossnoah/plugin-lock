package dev.noah.pluginlock.cli;

import dev.noah.pluginlock.cli.io.Terminal;
import dev.noah.pluginlock.cli.output.CliOutput;
import dev.noah.pluginlock.cli.selection.PluginSelection;
import dev.noah.pluginlock.cli.selection.PluginSelectionController;
import dev.noah.pluginlock.cli.selection.PluginSelectionStatus;
import dev.noah.pluginlock.core.catalog.PluginCatalog;
import dev.noah.pluginlock.core.model.PluginInspection;
import dev.noah.pluginlock.core.model.PluginMetadata;
import dev.noah.pluginlock.core.model.PluginVersion;
import dev.noah.pluginlock.core.project.ProjectLocator;
import dev.noah.pluginlock.core.project.ProjectPaths;
import dev.noah.pluginlock.core.run.ServerRunCommand;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLockCliTest {
    @Test
    void metadataConfirmationSummaryIncludesFormattedDownloadCount() {
        TestTerminal terminal = new TestTerminal();
        new CliOutput(terminal, false, false).printMetadata(metadata("modrinth", "luckperms", "LuckPerms", 1234567, "A permissions plugin"));

        String summary = terminal.output();
        assertTrue(summary.contains("LuckPerms (modrinth:luckperms)"));
        assertTrue(summary.contains("Authors: Luck"));
        assertTrue(summary.contains("Downloads: 1,234,567"));
        assertTrue(summary.contains("Description: A permissions plugin"));
    }

    @Test
    void blankInstallActionInstallsDefaultProvider() {
        TestTerminal terminal = new TestTerminal("");
        PluginSelection selection = selectionController(terminal).selectAndConfirmProvider(List.of(
                metadata("modrinth", "viaversion"),
                metadata("hangar", "ViaVersion")
        ), "latest", null, null);

        assertEquals(PluginSelectionStatus.SELECTED, selection.status());
        assertEquals("modrinth", selection.request().getProvider());
        assertEquals("viaversion", selection.request().getId());
    }

    @Test
    void providerNumberSwitchesBeforeInstalling() {
        TestTerminal terminal = new TestTerminal("2", "");
        PluginSelection selection = selectionController(terminal).selectAndConfirmProvider(List.of(
                metadata("modrinth", "viaversion"),
                metadata("hangar", "ViaVersion")
        ), "latest", null, null);

        assertEquals(PluginSelectionStatus.SELECTED, selection.status());
        assertEquals("hangar", selection.request().getProvider());
        assertEquals("ViaVersion", selection.request().getId());

        String prompt = terminal.output();
        assertTrue(prompt.contains("Found 2 provider match(es) for viaversion:"));
        assertTrue(prompt.contains("1. modrinth:viaversion - viaversion"));
        assertTrue(prompt.contains("2. hangar:ViaVersion - ViaVersion (default)"));
        assertTrue(prompt.contains("Install hangar:ViaVersion [Y/n] or provider number:"));
    }

    @Test
    void yesInstallsDefaultProvider() {
        TestTerminal terminal = new TestTerminal("y");
        PluginSelection selection = selectionController(terminal).selectAndConfirmProvider(List.of(
                metadata("modrinth", "viaversion"),
                metadata("hangar", "ViaVersion")
        ), "latest", null, null);

        assertEquals(PluginSelectionStatus.SELECTED, selection.status());
        assertEquals("modrinth", selection.request().getProvider());
        assertEquals("viaversion", selection.request().getId());
    }

    @Test
    void noInstallActionSkipsPluginSelection() {
        TestTerminal terminal = new TestTerminal("n");
        PluginSelection selection = selectionController(terminal).selectAndConfirmProvider(List.of(
                metadata("modrinth", "viaversion"),
                metadata("hangar", "ViaVersion")
        ), "latest", null, null);

        assertEquals(PluginSelectionStatus.EXITED, selection.status());
    }

    @Test
    void exitInstallActionExitsCommand() {
        TestTerminal terminal = new TestTerminal("e");
        PluginSelection selection = selectionController(terminal).selectAndConfirmProvider(List.of(
                metadata("modrinth", "viaversion"),
                metadata("hangar", "ViaVersion")
        ), "latest", null, null);

        assertEquals(PluginSelectionStatus.EXITED, selection.status());
    }

    @Test
    void providerMatchesShowDefaultProviderBeforeActionPrompt() {
        TestTerminal terminal = new TestTerminal();
        selectionController(terminal).printProviderMatches("viaversion", List.of(
                metadata("modrinth", "viaversion"),
                metadata("hangar", "ViaVersion")
        ));

        String summary = terminal.output();
        assertTrue(summary.contains("1. modrinth:viaversion - viaversion (default)"));
        assertTrue(summary.contains("2. hangar:ViaVersion - ViaVersion"));
    }

    @Test
    void suggestionsMessageListsProviderIdsAndNames() {
        String message = PluginSelectionController.suggestionsMessage(List.of(
                metadata("hangar", "essentialsx-chat-module", "EssentialsX Chat"),
                metadata("modrinth", "essentialsx", "EssentialsX")
        ));

        assertTrue(message.contains("Did you mean:"));
        assertTrue(message.contains("hangar:essentialsx-chat-module (EssentialsX Chat)"));
        assertTrue(message.contains("modrinth:essentialsx"));
    }

    @Test
    void installPromptUsesYesNoConfirmation() {
        TestTerminal terminal = new TestTerminal("n");
        selectionController(terminal).selectAndConfirmProvider(List.of(
                metadata("modrinth", "viaversion"),
                metadata("hangar", "ViaVersion")
        ), "latest", null, null);

        assertTrue(terminal.output().contains("Install modrinth:viaversion [Y/n] or provider number:"));
    }

    @Test
    void detectProjectDirUsesDirectoryWithLockFiles() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("plugin-lock-test");
        try {
            java.nio.file.Files.writeString(tempDir.resolve(dev.noah.pluginlock.core.PluginLockFiles.LOCK_FILE), "{}");

            assertEquals(tempDir.toAbsolutePath().normalize(), ProjectLocator.detectProjectDir(tempDir));
        } finally {
            java.nio.file.Files.deleteIfExists(tempDir.resolve(dev.noah.pluginlock.core.PluginLockFiles.LOCK_FILE));
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void pluginsDirResolvesRelativePathAgainstEffectiveProjectDir() {
        Path projectRoot = Path.of(System.getProperty("java.io.tmpdir"), "plugin-lock-project")
                .toAbsolutePath()
                .normalize();
        Path absolutePluginsDir = projectRoot.resolveSibling("minecraft").resolve("plugins");
        ProjectPaths paths = new ProjectPaths(projectRoot);

        assertEquals(projectRoot.resolve("plugins"), paths.pluginsDir(Path.of("plugins")));
        assertEquals(absolutePluginsDir, paths.pluginsDir(absolutePluginsDir));
    }

    @Test
    void commandLineRegistersDiscoveryCommands() {
        CommandLine commandLine = PluginLockCli.commandLine(new PluginLockCli());

        assertTrue(commandLine.getSubcommands().containsKey("list"));
        assertTrue(commandLine.getSubcommands().containsKey("doctor"));
        assertTrue(commandLine.getSubcommands().containsKey("search"));
        assertTrue(commandLine.getSubcommands().containsKey("info"));
        assertTrue(commandLine.getSubcommands().containsKey("update"));
        assertTrue(commandLine.getSubcommands().containsKey("run"));
    }

    @Test
    void infoOutputSummarizesLongMinecraftVersionLists() {
        PluginInspection inspection = new PluginInspection();
        inspection.setMetadata(metadata("modrinth", "luckperms", "LuckPerms"));
        PluginVersion version = new PluginVersion();
        version.setName("v5.5.17-bukkit");
        version.setFileName("LuckPerms-Bukkit-5.5.17.jar");
        version.setMinecraftVersions(List.of("1.8.9", "1.12.2", "1.21.11", "26.1.1", "26.1.2"));
        version.setLoaders(List.of("bukkit", "paper", "spigot"));
        inspection.setVersions(List.of(version));

        TestTerminal terminal = new TestTerminal();
        new CliOutput(terminal, false, false).info(List.of(inspection), "26.1.2", "paper");

        String summary = terminal.output();
        assertTrue(summary.contains("OK v5.5.17-bukkit"));
        assertTrue(summary.contains("MC 1.8.9...26.1.2 (5)"));
        assertTrue(summary.contains("LuckPerms-Bukkit-5.5.17.jar"));
        assertTrue(!summary.contains("Minecraft:"));
        assertTrue(!summary.contains("Loaders:"));
    }

    @Test
    void runCommandNormalizesMemoryValues() {
        assertEquals("2048M", ServerRunCommand.normalizeMemory("2048"));
        assertEquals("2048M", ServerRunCommand.normalizeMemory("2048m"));
        assertEquals("2G", ServerRunCommand.normalizeMemory("2g"));
        assertEquals("2G", ServerRunCommand.normalizeMemory("2gb"));
        assertEquals("512M", ServerRunCommand.normalizeMemory("512mb"));
    }

    @Test
    void runCommandBuildsOptimizedServerCommand() {
        List<String> command = ServerRunCommand.build("java", "2048M", Path.of("server.jar"));

        assertEquals("java", command.getFirst());
        assertTrue(command.contains("-Xms2048M"));
        assertTrue(command.contains("-Xmx2048M"));
        assertTrue(command.contains("--add-modules=jdk.incubator.vector"));
        assertTrue(command.contains("-XX:+UseG1GC"));
        assertTrue(command.contains("-Dusing.aikars.flags=https://mcflags.emc.gs"));
        assertTrue(command.contains("-jar"));
        assertTrue(command.contains("server.jar"));
        assertEquals("--nogui", command.getLast());
    }

    @Test
    void runCommandReturnsWhenServerProcessStops() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("plugin-lock-run-test");
        try {
            java.nio.file.Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
            CommandLine commandLine = PluginLockCli.commandLine(new PluginLockCli());

            int exitCode = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> commandLine.execute(
                    "--project-dir", tempDir.toString(),
                    "run",
                    "--memory", "128M",
                    "--jar", tempDir.resolve("missing-server.jar").toString(),
                    "--java", javaExecutable.toString()
            ));

            assertEquals(1, exitCode);
        } finally {
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    private static PluginSelectionController selectionController(TestTerminal terminal) {
        return new PluginSelectionController(new PluginCatalog(), terminal, new CliOutput(terminal, false, false));
    }

    private static PluginMetadata metadata(String provider, String id) {
        return metadata(provider, id, id);
    }

    private static PluginMetadata metadata(String provider, String id, String name) {
        return metadata(provider, id, name, 0, null);
    }

    private static PluginMetadata metadata(String provider, String id, String name, long downloads, String description) {
        PluginMetadata metadata = new PluginMetadata();
        metadata.setProvider(provider);
        metadata.setId(id);
        metadata.setName(name);
        metadata.setDownloads(downloads);
        metadata.setDescription(description);
        metadata.setAuthors(List.of("Luck"));
        return metadata;
    }

    private static final class TestTerminal implements Terminal {
        private final Queue<String> answers = new ArrayDeque<>();
        private final StringBuilder output = new StringBuilder();

        TestTerminal(String... answers) {
            this.answers.addAll(List.of(answers));
        }

        @Override
        public String readLine(String prompt) {
            output.append(prompt);
            return answers.isEmpty() ? "" : answers.remove();
        }

        @Override
        public void print(String value) {
            output.append(value);
        }

        @Override
        public void println(String value) {
            output.append(value).append(System.lineSeparator());
        }

        @Override
        public void error(String value) {
            output.append(value).append(System.lineSeparator());
        }

        String output() {
            return output.toString();
        }
    }
}
