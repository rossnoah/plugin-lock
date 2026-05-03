package dev.noah.pluginlock.cli.command;

import dev.noah.pluginlock.cli.CliContext;
import dev.noah.pluginlock.cli.PluginLockCli;
import dev.noah.pluginlock.cli.selection.PluginSelection;
import dev.noah.pluginlock.core.PluginLockDefaults;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginRequest;
import dev.noah.pluginlock.core.workflow.InstallWorkflow;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "install", aliases = "i", mixinStandardHelpOptions = true, description = "Install plugins and update the lockfile when a manifest is present.")
public final class InstallCommand implements Callable<Integer> {
    @ParentCommand
    PluginLockCli parent;

    @Parameters(arity = "0..*", description = "Optional provider project slugs or ids to add before installing.")
    List<String> ids = List.of();

    @Option(names = "--provider", defaultValue = "auto", description = "Plugin provider for new package arguments: auto, modrinth, or hangar.")
    String provider;

    @Option(names = "--version", defaultValue = "latest", description = "Version id, version number, or latest for new package arguments.")
    String version;

    @Option(names = "--plugins-dir", defaultValue = "plugins", description = "Destination plugins directory.")
    Path pluginsDir;

    @Option(names = "--minecraft", description = "Minecraft version for auto-init when no project files exist.")
    String minecraftVersion;

    @Option(names = "--server", description = "Server provider for auto-init when no project files exist: paper or purpur.")
    String server;

    @Option(names = {"-y", "--yes"}, description = "Skip plugin metadata confirmation for new plugins.")
    boolean yes;

    @Override
    public Integer call() throws Exception {
        CliContext context = parent.context();
        Path resolvedPluginsDir = context.paths().pluginsDir(pluginsDir);

        if ((shouldInitialize(minecraftVersion, server) || !ids.isEmpty())
                && Files.notExists(context.paths().manifestPath()) && Files.notExists(context.paths().lockPath())) {
            String resolvedServer = context.serverSelection().chooseServer(server, PluginLockDefaults.LOADER, yes);
            String resolvedMinecraftVersion = context.serverSelection().chooseMinecraftVersion(
                    minecraftVersion,
                    context.serverDownloads().versions(resolvedServer),
                    yes
            );
            context.projectService().initializeProject(resolvedServer, resolvedMinecraftVersion, context.downloadProgress());
        }

        List<PluginRequest> requested = new ArrayList<>();
        if (!ids.isEmpty() || shouldInitialize(minecraftVersion, server) || Files.exists(context.paths().manifestPath())) {
            PluginManifest manifest = context.projectService().readManifestOrDefault();
            for (String id : ids) {
                PluginSelection selection = context.pluginSelection().selectPlugin(provider, id, version, yes,
                        manifest.getMinecraftVersion(), manifest.getLoader());
                if (selection.isExited()) {
                    context.terminal().println("Skipped " + id);
                    continue;
                }
                requested.add(selection.request());
            }
        }

        InstallWorkflow.InstallWorkflowResult result = context.installWorkflow()
                .install(requested, resolvedPluginsDir, minecraftVersion, context.downloadProgress());
        if (result.lockChanged()) {
            CommandSupport.emitCompatibilityWarnings(context, result.lock());
        }
        context.output().success("install", CommandSupport.installMessage(result), CommandSupport.installDetails(result));
        return 0;
    }

    private static boolean shouldInitialize(String minecraftVersion, String server) {
        return minecraftVersion != null && !minecraftVersion.isBlank()
                || server != null && !server.isBlank();
    }
}
