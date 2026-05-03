package dev.noah.pluginlock.cli.command;

import dev.noah.pluginlock.cli.CliContext;
import dev.noah.pluginlock.cli.PluginLockCli;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.lock.LockSnapshots;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "lock", hidden = true, mixinStandardHelpOptions = true, description = "Resolve server-lock.json into server-lock.lock.json.")
public final class LockCommand implements Callable<Integer> {
    @ParentCommand
    PluginLockCli parent;

    @Override
    public Integer call() throws Exception {
        CliContext context = parent.context();
        PluginManifest manifest = PluginLockFiles.readManifest(context.paths().manifestPath());
        PluginLock lock = context.projectService().resolveLock(manifest);
        if (Files.exists(context.paths().lockPath()) && LockSnapshots.sameLock(PluginLockFiles.readLock(context.paths().lockPath()), lock)) {
            context.output().success("lock", "Already locked " + lock.getPlugins().size() + " plugin(s)", Map.of(
                    "count", lock.getPlugins().size(),
                    "plugins", lock.getPlugins().stream().map(LockedPlugin::getName).toList(),
                    "changed", false
            ));
            return 0;
        }
        context.projectService().writeLock(lock);
        CommandSupport.emitCompatibilityWarnings(context, lock);
        context.output().success("lock", "Locked " + lock.getPlugins().size() + " plugin(s)", Map.of(
                "count", lock.getPlugins().size(),
                "plugins", lock.getPlugins().stream().map(LockedPlugin::getName).toList(),
                "changed", true
        ));
        return 0;
    }
}
