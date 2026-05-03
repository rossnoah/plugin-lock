package dev.noah.pluginlock.paper;

import dev.noah.pluginlock.core.PluginInstaller;
import dev.noah.pluginlock.core.model.PluginLock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class PluginLockPaperPlugin extends JavaPlugin {
    private static final String PREFIX = "[PluginLock] ";
    private static final List<String> SUBCOMMANDS = List.of("install", "list", "doctor", "status", "help");
    private final AtomicBoolean installRunning = new AtomicBoolean();

    @Override
    public void onEnable() {
        getLogger().info("Plugin Lock is enabled. Run /pluginlock help for commands.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String subcommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "install" -> install(sender);
            case "list", "status" -> list(sender);
            case "doctor" -> doctor(sender);
            case "help" -> help(sender, label);
            default -> {
                send(sender, "Unknown command: " + args[0]);
                help(sender, label);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(subcommand -> subcommand.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private void install(CommandSender sender) {
        if (!sender.hasPermission("pluginlock.install")) {
            send(sender, "You do not have permission to install locked plugins.");
            return;
        }
        if (!installRunning.compareAndSet(false, true)) {
            send(sender, "An install is already running.");
            return;
        }
        send(sender, "Installing locked plugins from server-lock.lock.json...");
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                PaperPluginLockService service = service();
                PluginLock lock = service.readLock();
                Set<String> announcedDownloads = ConcurrentHashMap.newKeySet();
                service.install(lock, new PluginInstaller(), message -> {
                    if (announcedDownloads.add(message)) {
                        send(sender, message);
                    }
                });
                send(sender, "Installed " + lock.getPlugins().size() + " locked plugin(s). Restart the server to load new jars.");
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, "Failed to install locked plugins", exception);
                send(sender, "Install failed: " + message(exception));
            } finally {
                installRunning.set(false);
            }
        });
    }

    private void list(CommandSender sender) {
        if (!sender.hasPermission("pluginlock.list")) {
            send(sender, "You do not have permission to list locked plugins.");
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                PaperPluginLockService service = service();
                service.listLines(service.readLock()).forEach(line -> send(sender, line));
            } catch (Exception exception) {
                getLogger().log(Level.WARNING, "Failed to list locked plugins", exception);
                send(sender, "List failed: " + message(exception));
            }
        });
    }

    private void doctor(CommandSender sender) {
        if (!sender.hasPermission("pluginlock.doctor")) {
            send(sender, "You do not have permission to run Plugin Lock doctor.");
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                PaperPluginLockService service = service();
                List<PaperPluginLockService.DoctorCheck> checks = service.doctor(service.readLock());
                checks.forEach(check -> send(sender, doctorLine(check)));
                boolean failed = checks.stream().anyMatch(check -> check.status() == PaperPluginLockService.Status.ERROR);
                send(sender, failed ? "Doctor found problems." : "Doctor passed.");
            } catch (Exception exception) {
                getLogger().log(Level.WARNING, "Failed to run Plugin Lock doctor", exception);
                send(sender, "Doctor failed: " + message(exception));
            }
        });
    }

    private void help(CommandSender sender, String label) {
        send(sender, "Commands:");
        Arrays.asList(
                "/" + label + " install - install plugin jars from the lockfile",
                "/" + label + " list - show locked plugins",
                "/" + label + " doctor - check local jars and hashes",
                "/" + label + " status - alias for list"
        ).forEach(line -> send(sender, line));
    }

    private PaperPluginLockService service() {
        return new PaperPluginLockService(serverRoot());
    }

    private Path serverRoot() {
        return getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
    }

    private void send(CommandSender sender, String message) {
        Runnable sendMessage = () -> sender.sendMessage(PREFIX + message);
        if (getServer().isPrimaryThread()) {
            sendMessage.run();
        } else {
            getServer().getScheduler().runTask(this, sendMessage);
        }
    }

    private static String doctorLine(PaperPluginLockService.DoctorCheck check) {
        return switch (check.status()) {
            case OK -> "OK " + check.message();
            case WARNING -> "WARN " + check.message();
            case ERROR -> "FAIL " + check.message();
        };
    }

    private static String message(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
