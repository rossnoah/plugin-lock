package dev.noah.pluginlock.cli.command;

import dev.noah.pluginlock.cli.CliContext;
import dev.noah.pluginlock.cli.PluginLockCli;
import dev.noah.pluginlock.core.PluginLockDefaults;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.workflow.ProjectService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "init", mixinStandardHelpOptions = true, description = "Create a server-lock.json manifest.")
public final class InitCommand implements Callable<Integer> {
    @ParentCommand
    PluginLockCli parent;

    @Option(names = "--minecraft", description = "Minecraft version.")
    String minecraftVersion;

    @Option(names = "--server", description = "Server provider: paper or purpur.")
    String server;

    @Option(names = {"-y", "--yes"}, description = "Accept detected/default values without prompting.")
    boolean yes;

    @Override
    public Integer call() throws Exception {
        CliContext context = parent.context();
        if (Files.exists(context.paths().manifestPath())) {
            PluginManifest manifest = PluginLockFiles.readManifest(context.paths().manifestPath());
            context.output().success("init", "Already initialized " + manifest.getMinecraftVersion() + " " + manifest.getLoader(), Map.of(
                    "manifest", PluginLockFiles.MANIFEST_FILE,
                    "lockfile", Files.exists(context.paths().lockPath()) ? PluginLockFiles.LOCK_FILE : "",
                    "minecraftVersion", manifest.getMinecraftVersion(),
                    "loader", manifest.getLoader(),
                    "changed", false
            ));
            return 0;
        }

        String resolvedServer = context.serverSelection().chooseServer(server, PluginLockDefaults.LOADER, yes);
        String resolvedMinecraftVersion = context.serverSelection().chooseMinecraftVersion(
                minecraftVersion,
                context.serverDownloads().versions(resolvedServer),
                yes
        );
        ProjectService.InitializeResult result = context.projectService()
                .initializeProject(resolvedServer, resolvedMinecraftVersion, context.downloadProgress());

        context.output().success("init", "Initialized " + result.manifest().getMinecraftVersion() + " " + result.server().getProvider(), Map.of(
                "manifest", PluginLockFiles.MANIFEST_FILE,
                "lockfile", PluginLockFiles.LOCK_FILE,
                "server", result.server().getProvider(),
                "minecraftVersion", result.manifest().getMinecraftVersion(),
                "loader", result.manifest().getLoader(),
                "build", result.server().getBuild(),
                "serverJar", result.server().getFileName(),
                "downloadUrl", result.server().getDownloadUrl(),
                "sha256", result.server().getSha256() == null ? "" : result.server().getSha256()
        ));
        return 0;
    }
}
