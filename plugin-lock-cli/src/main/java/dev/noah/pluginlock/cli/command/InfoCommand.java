package dev.noah.pluginlock.cli.command;

import dev.noah.pluginlock.cli.CliContext;
import dev.noah.pluginlock.cli.PluginLockCli;
import dev.noah.pluginlock.cli.selection.PluginSelectionController;
import dev.noah.pluginlock.core.catalog.PluginCoordinate;
import dev.noah.pluginlock.core.model.PluginInspection;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginMetadata;
import dev.noah.pluginlock.core.model.PluginRequest;
import dev.noah.pluginlock.core.provider.PluginNotFoundException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "info", mixinStandardHelpOptions = true, description = "Show plugin metadata and available versions.")
public final class InfoCommand implements Callable<Integer> {
    @ParentCommand
    PluginLockCli parent;

    @Parameters(index = "0", description = "Provider project slug or id, for example luckperms.")
    String id;

    @Option(names = "--provider", defaultValue = "auto", description = "Plugin provider: auto, modrinth, or hangar.")
    String provider;

    @Option(names = "--minecraft", description = "Only mark versions against this Minecraft version. Defaults to the manifest value.")
    String minecraftVersion;

    @Option(names = "--loader", description = "Loader to query, for example paper. Defaults to the manifest value.")
    String loader;

    @Option(names = "--limit", defaultValue = "12", description = "Maximum versions to show per provider.")
    int limit;

    @Override
    public Integer call() throws Exception {
        CliContext context = parent.context();
        PluginManifest manifest = context.projectService().readManifestOrDefault();
        String resolvedMinecraft = minecraftVersion == null || minecraftVersion.isBlank()
                ? manifest.getMinecraftVersion()
                : minecraftVersion.trim();
        String resolvedLoader = loader == null || loader.isBlank()
                ? manifest.getLoader()
                : loader.trim();
        PluginCoordinate coordinate = PluginCoordinate.parse(id, provider, "latest");
        List<PluginMetadata> matches = "auto".equalsIgnoreCase(coordinate.provider())
                ? context.pluginCatalog().findProviderMatches(coordinate.id())
                : List.of(context.pluginCatalog().fetchMetadata(coordinate.provider(), coordinate.id()));
        if (matches.isEmpty()) {
            throw pluginNotFoundWithSuggestions(context, coordinate.id());
        }
        List<PluginInspection> infos = new ArrayList<>();
        for (PluginMetadata metadata : matches) {
            PluginInspection inspection = context.pluginCatalog().inspect(
                    new PluginRequest(metadata.getId(), metadata.getProvider(), "latest"),
                    resolvedLoader);
            inspection.setVersions(inspection.getVersions().stream().limit(Math.max(1, limit)).toList());
            infos.add(inspection);
        }
        context.output().info(infos, resolvedMinecraft, resolvedLoader);
        return 0;
    }

    private static PluginNotFoundException pluginNotFoundWithSuggestions(CliContext context, String id) {
        try {
            List<PluginMetadata> suggestions = context.pluginCatalog().search("auto", id, 5);
            if (!suggestions.isEmpty()) {
                return new PluginNotFoundException("Modrinth or Hangar", id + PluginSelectionController.suggestionsMessage(suggestions));
            }
        } catch (Exception ignored) {
        }
        return new PluginNotFoundException("Modrinth or Hangar", id);
    }
}
