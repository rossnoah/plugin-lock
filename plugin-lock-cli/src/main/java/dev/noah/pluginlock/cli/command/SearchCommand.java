package dev.noah.pluginlock.cli.command;

import dev.noah.pluginlock.cli.CliContext;
import dev.noah.pluginlock.cli.PluginLockCli;
import dev.noah.pluginlock.core.model.PluginMetadata;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "search", mixinStandardHelpOptions = true, description = "Search plugin providers.")
public final class SearchCommand implements Callable<Integer> {
    @ParentCommand
    PluginLockCli parent;

    @Parameters(index = "0", description = "Search query.")
    String query;

    @Option(names = "--provider", defaultValue = "auto", description = "Plugin provider: auto, modrinth, or hangar.")
    String provider;

    @Option(names = "--limit", defaultValue = "10", description = "Maximum results to show.")
    int limit;

    @Override
    public Integer call() throws Exception {
        CliContext context = parent.context();
        List<PluginMetadata> results = context.pluginCatalog().search(provider, query, Math.max(1, limit));
        context.output().search(query, results);
        return 0;
    }
}
