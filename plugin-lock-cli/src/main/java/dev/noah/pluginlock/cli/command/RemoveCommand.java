package dev.noah.pluginlock.cli.command;

import dev.noah.pluginlock.cli.CliContext;
import dev.noah.pluginlock.cli.PluginLockCli;
import dev.noah.pluginlock.core.workflow.RemoveWorkflow;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "remove", aliases = {"rm", "uninstall"}, mixinStandardHelpOptions = true, description = "Remove plugins recorded in server-lock files.")
public final class RemoveCommand implements Callable<Integer> {
    @ParentCommand
    PluginLockCli parent;

    @Parameters(arity = "1..*", description = "Plugin ids to remove.")
    List<String> ids;

    @Option(names = "--plugins-dir", defaultValue = "plugins", description = "Destination plugins directory.")
    Path pluginsDir;

    @Override
    public Integer call() throws Exception {
        CliContext context = parent.context();
        RemoveWorkflow.RemoveResult result = context.removeWorkflow().remove(ids, context.paths().pluginsDir(pluginsDir));
        context.output().success("remove", CommandSupport.removeMessage(result), CommandSupport.removeDetails(result));
        return 0;
    }
}
