package dev.noah.pluginlock.cli.command;

import dev.noah.pluginlock.cli.CliContext;
import dev.noah.pluginlock.cli.PluginLockCli;
import dev.noah.pluginlock.core.workflow.InstallWorkflow;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "clean-install", aliases = "ci", mixinStandardHelpOptions = true, description = "Install exactly from server-lock.lock.json without resolving new versions.")
public final class CleanInstallCommand implements Callable<Integer> {
    @ParentCommand
    PluginLockCli parent;

    @Option(names = "--plugins-dir", defaultValue = "plugins", description = "Destination plugins directory.")
    Path pluginsDir;

    @Override
    public Integer call() throws Exception {
        CliContext context = parent.context();
        InstallWorkflow.CleanInstallResult result = context.installWorkflow()
                .cleanInstall(context.paths().pluginsDir(pluginsDir), context.downloadProgress());
        String message = result.lock().getPlugins().isEmpty()
                ? "No locked plugins to clean install"
                : "Clean installed " + result.lock().getPlugins().size() + " plugin(s)";
        context.output().success("clean-install", message, CommandSupport.cleanInstallDetails(result));
        return 0;
    }
}
