package dev.noah.pluginlock.cli.command;

import dev.noah.pluginlock.cli.CliContext;
import dev.noah.pluginlock.cli.PluginLockCli;
import dev.noah.pluginlock.cli.io.Ansi;
import dev.noah.pluginlock.cli.selection.PluginSelectionController;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.workflow.UpdateWorkflow;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "update", mixinStandardHelpOptions = true, description = "Update locked plugin versions from the manifest.")
public final class UpdateCommand implements Callable<Integer> {
    @ParentCommand
    PluginLockCli parent;

    @Parameters(arity = "0..*", description = "Optional plugin ids, names, or files to update.")
    List<String> ids = List.of();

    @Option(names = "--server", description = "Update the locked server build and download the server jar.")
    boolean server;

    @Option(names = "--interactive", description = "Choose which locked plugins to update.")
    boolean interactive;

    @Override
    public Integer call() throws Exception {
        CliContext context = parent.context();
        List<String> requestedIds = interactive ? selectInteractiveUpdates(context) : ids;
        if (requestedIds == null) {
            context.output().success("update", "No selected plugins to update", Map.of(
                    "count", 0,
                    "requestedCount", 0,
                    "updated", List.of(),
                    "interactive", true,
                    "server", server,
                    "changed", false,
                    "lockChanged", false,
                    "serverDownloaded", false,
                    "plugins", List.of()
            ));
            return 0;
        }
        UpdateWorkflow.UpdateResult result = context.updateWorkflow().update(requestedIds, server, context.downloadProgress());
        if (result.lockChanged()) {
            CommandSupport.emitCompatibilityWarnings(context, result.lock());
        }
        context.output().success("update", CommandSupport.updateMessage(result), CommandSupport.updateDetails(result, interactive));
        return 0;
    }

    private static List<String> selectInteractiveUpdates(CliContext context) throws Exception {
        if (Files.notExists(context.paths().manifestPath())) {
            throw new IllegalStateException("No server-lock.json found. Run `pl init` first.");
        }
        PluginManifest manifest = PluginLockFiles.readManifest(context.paths().manifestPath());
        PluginLock existing = Files.exists(context.paths().lockPath())
                ? PluginLockFiles.readLock(context.paths().lockPath())
                : null;
        PluginLock resolved = context.projectService().resolveLock(manifest);
        List<LockedPlugin> plugins = existing != null && !existing.getPlugins().isEmpty()
                ? existing.getPlugins()
                : resolved.getPlugins();
        if (plugins.isEmpty()) {
            return List.of();
        }
        context.terminal().println("");
        context.terminal().println(Ansi.bold("Select plugins to update:"));
        for (int index = 0; index < plugins.size(); index++) {
            LockedPlugin plugin = plugins.get(index);
            context.terminal().println((index + 1) + ". " + Ansi.blue(plugin.getProvider() + ":" + plugin.getId())
                    + "  " + blank(plugin.getName())
                    + "  " + Ansi.dim(blank(plugin.getVersionName())));
        }
        String answer = context.terminal().readLine("Update plugins [all]: ");
        if (answer.isBlank() || "all".equalsIgnoreCase(answer) || "*".equals(answer)) {
            return List.of();
        }
        if (PluginSelectionController.isExitAnswer(answer)) {
            return null;
        }
        List<String> selected = new ArrayList<>();
        for (String token : answer.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            Integer option = PluginSelectionController.parseOption(trimmed);
            if (option != null && option >= 1 && option <= plugins.size()) {
                selected.add(plugins.get(option - 1).getId());
            } else {
                selected.add(trimmed);
            }
        }
        return selected;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
