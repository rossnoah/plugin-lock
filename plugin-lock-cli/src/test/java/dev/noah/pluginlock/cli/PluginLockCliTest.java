package dev.noah.pluginlock.cli;

import dev.noah.pluginlock.core.model.PluginMetadata;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLockCliTest {
    @Test
    void metadataConfirmationSummaryIncludesFormattedDownloadCount() {
        PluginMetadata metadata = new PluginMetadata();
        metadata.setId("luckperms");
        metadata.setProvider("modrinth");
        metadata.setName("LuckPerms");
        metadata.setAuthors(List.of("Luck"));
        metadata.setDownloads(1234567);
        metadata.setDescription("A permissions plugin");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output));
            PluginLockCli.printMetadata(metadata);
        } finally {
            System.setOut(originalOut);
        }

        String summary = output.toString();
        assertTrue(summary.contains("LuckPerms (modrinth:luckperms)"));
        assertTrue(summary.contains("Authors: Luck"));
        assertTrue(summary.contains("Downloads: 1,234,567"));
        assertTrue(summary.contains("Description: A permissions plugin"));
    }

    @Test
    void blankInstallActionInstallsDefaultProvider() {
        InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream("\n".getBytes(StandardCharsets.UTF_8)));

            PluginLockCli.PluginSelection selection = PluginLockCli.selectAndConfirmProvider(List.of(
                    metadata("modrinth", "viaversion"),
                    metadata("hangar", "ViaVersion")
            ), "latest");

            assertEquals(PluginLockCli.PluginSelectionStatus.SELECTED, selection.status());
            assertEquals("modrinth", selection.request().getProvider());
            assertEquals("viaversion", selection.request().getId());
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    void providerNumberSwitchesBeforeInstalling() {
        InputStream originalIn = System.in;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setIn(new ByteArrayInputStream("2\n\n".getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(output));

            PluginLockCli.PluginSelection selection = PluginLockCli.selectAndConfirmProvider(List.of(
                    metadata("modrinth", "viaversion"),
                    metadata("hangar", "ViaVersion")
            ), "latest");

            assertEquals(PluginLockCli.PluginSelectionStatus.SELECTED, selection.status());
            assertEquals("hangar", selection.request().getProvider());
            assertEquals("ViaVersion", selection.request().getId());
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }

        String prompt = output.toString();
        assertTrue(prompt.contains("Found 2 provider match(es) for viaversion:"));
        assertTrue(prompt.contains("1. modrinth:viaversion - viaversion"));
        assertTrue(prompt.contains("2. hangar:ViaVersion - ViaVersion (default)"));
        assertTrue(prompt.contains("Install hangar:ViaVersion [Y/n] or provider number:"));
    }

    @Test
    void yesInstallsDefaultProvider() {
        InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream("y\n".getBytes(StandardCharsets.UTF_8)));

            PluginLockCli.PluginSelection selection = PluginLockCli.selectAndConfirmProvider(List.of(
                    metadata("modrinth", "viaversion"),
                    metadata("hangar", "ViaVersion")
            ), "latest");

            assertEquals(PluginLockCli.PluginSelectionStatus.SELECTED, selection.status());
            assertEquals("modrinth", selection.request().getProvider());
            assertEquals("viaversion", selection.request().getId());
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    void noInstallActionSkipsPluginSelection() {
        InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream("n\n".getBytes(StandardCharsets.UTF_8)));

            PluginLockCli.PluginSelection selection = PluginLockCli.selectAndConfirmProvider(List.of(
                    metadata("modrinth", "viaversion"),
                    metadata("hangar", "ViaVersion")
            ), "latest");

            assertEquals(PluginLockCli.PluginSelectionStatus.EXITED, selection.status());
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    void exitInstallActionExitsCommand() {
        InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream("e\n".getBytes(StandardCharsets.UTF_8)));

            PluginLockCli.PluginSelection selection = PluginLockCli.selectAndConfirmProvider(List.of(
                    metadata("modrinth", "viaversion"),
                    metadata("hangar", "ViaVersion")
            ), "latest");

            assertEquals(PluginLockCli.PluginSelectionStatus.EXITED, selection.status());
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    void providerMatchesShowDefaultProviderBeforeActionPrompt() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output));
            PluginLockCli.printProviderMatches("viaversion", List.of(
                    metadata("modrinth", "viaversion"),
                    metadata("hangar", "ViaVersion")
            ));
        } finally {
            System.setOut(originalOut);
        }

        String summary = output.toString();
        assertTrue(summary.contains("1. modrinth:viaversion - viaversion (default)"));
        assertTrue(summary.contains("2. hangar:ViaVersion - ViaVersion"));
    }

    @Test
    void suggestionsMessageListsProviderIdsAndNames() {
        String message = PluginLockCli.suggestionsMessage(List.of(
                metadata("hangar", "essentialsx-chat-module", "EssentialsX Chat"),
                metadata("modrinth", "essentialsx", "EssentialsX")
        ));

        assertTrue(message.contains("Did you mean:"));
        assertTrue(message.contains("hangar:essentialsx-chat-module (EssentialsX Chat)"));
        assertTrue(message.contains("modrinth:essentialsx"));
    }

    @Test
    void installPromptUsesYesNoConfirmation() {
        InputStream originalIn = System.in;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setIn(new ByteArrayInputStream("n\n".getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(output));

            PluginLockCli.selectAndConfirmProvider(List.of(
                    metadata("modrinth", "viaversion"),
                    metadata("hangar", "ViaVersion")
            ), "latest");
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }

        String prompt = output.toString();
        assertTrue(prompt.contains("Install modrinth:viaversion [Y/n] or provider number:"));
    }

    @Test
    void detectProjectDirUsesDirectoryWithLockFiles() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("plugin-lock-test");
        try {
            java.nio.file.Files.writeString(tempDir.resolve(dev.noah.pluginlock.core.PluginLockFiles.LOCK_FILE), "{}");

            assertEquals(tempDir.toAbsolutePath().normalize(), PluginLockCli.detectProjectDir(tempDir));
        } finally {
            java.nio.file.Files.deleteIfExists(tempDir.resolve(dev.noah.pluginlock.core.PluginLockFiles.LOCK_FILE));
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void pluginsDirResolvesRelativePathAgainstEffectiveProjectDir() {
        PluginLockCli cli = new PluginLockCli();
        cli.projectDir = Path.of("/tmp/plugin-lock-project");

        assertEquals(Path.of("/tmp/plugin-lock-project/plugins"), cli.pluginsDir(Path.of("plugins")));
        assertEquals(Path.of("/var/minecraft/plugins"), cli.pluginsDir(Path.of("/var/minecraft/plugins")));
    }

    @Test
    void commandLineRegistersDiscoveryCommands() {
        CommandLine commandLine = PluginLockCli.commandLine(new PluginLockCli());

        assertTrue(commandLine.getSubcommands().containsKey("list"));
        assertTrue(commandLine.getSubcommands().containsKey("doctor"));
        assertTrue(commandLine.getSubcommands().containsKey("search"));
        assertTrue(commandLine.getSubcommands().containsKey("update"));
        assertTrue(commandLine.getSubcommands().containsKey("run"));
    }

    @Test
    void runCommandNormalizesMemoryValues() {
        assertEquals("2048M", PluginLockCli.RunCommand.normalizeMemory("2048"));
        assertEquals("2048M", PluginLockCli.RunCommand.normalizeMemory("2048m"));
        assertEquals("2G", PluginLockCli.RunCommand.normalizeMemory("2g"));
        assertEquals("2G", PluginLockCli.RunCommand.normalizeMemory("2gb"));
        assertEquals("512M", PluginLockCli.RunCommand.normalizeMemory("512mb"));
    }

    @Test
    void runCommandBuildsOptimizedServerCommand() {
        List<String> command = PluginLockCli.RunCommand.serverCommand("java", "2048M", Path.of("server.jar"));

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

    private static PluginMetadata metadata(String provider, String id) {
        return metadata(provider, id, id);
    }

    private static PluginMetadata metadata(String provider, String id, String name) {
        PluginMetadata metadata = new PluginMetadata();
        metadata.setProvider(provider);
        metadata.setId(id);
        metadata.setName(name);
        return metadata;
    }
}
