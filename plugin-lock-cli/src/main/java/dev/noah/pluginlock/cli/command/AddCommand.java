package dev.noah.pluginlock.cli.command;

import dev.noah.pluginlock.cli.CliContext;
import dev.noah.pluginlock.cli.PluginLockCli;
import dev.noah.pluginlock.cli.selection.PluginSelection;
import dev.noah.pluginlock.core.manifest.ManifestEditor;
import dev.noah.pluginlock.core.manifest.RequestChange;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginRequest;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "add", mixinStandardHelpOptions = true, description = "Add a plugin request to the manifest.")
public final class AddCommand implements Callable<Integer> {
    @ParentCommand
    PluginLockCli parent;

    @Parameters(index = "0", description = "Provider project slug or id, for example luckperms.")
    String id;

    @Option(names = "--provider", defaultValue = "auto", description = "Plugin provider: auto, modrinth, or hangar.")
    String provider;

    @Option(names = "--version", defaultValue = "latest", description = "Version id, version number, or latest.")
    String version;

    @Option(names = {"-y", "--yes"}, description = "Skip plugin metadata confirmation.")
    boolean yes;

    @Override
    public Integer call() throws Exception {
        CliContext context = parent.context();
        PluginManifest manifest = context.projectService().readManifestOrDefault();
        ManifestEditor.ensurePluginsList(manifest);
        PluginSelection selection = context.pluginSelection().selectPlugin(provider, id, version, yes,
                manifest.getMinecraftVersion(), manifest.getLoader());
        if (selection.isExited()) {
            context.terminal().println("Cancelled");
            return 1;
        }
        PluginRequest request = selection.request();
        RequestChange change = ManifestEditor.applyRequest(manifest, request);
        if (!change.changed()) {
            context.output().success("add", "Already added " + request.getId(), Map.of(
                    "id", request.getId(),
                    "provider", request.getProvider(),
                    "version", request.getVersion(),
                    "changed", false
            ));
            return 0;
        }
        context.projectService().writeManifest(manifest);
        context.output().success("add", CommandSupport.addMessage(change), Map.of(
                "id", request.getId(),
                "provider", request.getProvider(),
                "version", request.getVersion(),
                "previous", change.replaced().stream().map(ManifestEditor::requestId).toList(),
                "changed", true
        ));
        return 0;
    }
}
