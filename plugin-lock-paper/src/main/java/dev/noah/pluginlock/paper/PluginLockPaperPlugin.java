package dev.noah.pluginlock.paper;

import dev.noah.pluginlock.core.PluginInstaller;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.model.PluginLock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

public final class PluginLockPaperPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Plugin Lock is enabled. Run /pluginlock install to install locked plugins.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "install".equalsIgnoreCase(args[0])) {
            install(sender);
            return true;
        }
        sender.sendMessage("Usage: /" + label + " install");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("install");
        }
        return List.of();
    }

    private void install(CommandSender sender) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                Path serverRoot = getDataFolder().toPath().getParent().getParent();
                Path lockfile = serverRoot.resolve(PluginLockFiles.LOCK_FILE);
                Path pluginsDirectory = serverRoot.resolve("plugins");
                PluginLock lock = PluginLockFiles.readLock(lockfile);
                new PluginInstaller().install(lock, pluginsDirectory);
                sender.sendMessage("Installed " + lock.getPlugins().size() + " locked plugin(s). Restart the server to load new jars.");
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, "Failed to install locked plugins", exception);
                sender.sendMessage("Plugin Lock install failed: " + exception.getMessage());
            }
        });
    }
}
