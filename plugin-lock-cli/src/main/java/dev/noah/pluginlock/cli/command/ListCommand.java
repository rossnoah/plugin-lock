package dev.noah.pluginlock.cli.command;

import dev.noah.pluginlock.cli.CliContext;
import dev.noah.pluginlock.cli.PluginLockCli;
import dev.noah.pluginlock.core.PluginLockFiles;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.util.concurrent.Callable;

@Command(name = "list", aliases = "ls", mixinStandardHelpOptions = true, description = "List locked plugins.")
public final class ListCommand implements Callable<Integer> {
    @ParentCommand
    PluginLockCli parent;

    @Override
    public Integer call() throws Exception {
        CliContext context = parent.context();
        if (Files.notExists(context.paths().lockPath())) {
            throw new IllegalStateException("No server-lock.lock.json found. Run `pl install` or `pl lock` first.");
        }
        context.output().list(PluginLockFiles.readLock(context.paths().lockPath()));
        return 0;
    }
}
