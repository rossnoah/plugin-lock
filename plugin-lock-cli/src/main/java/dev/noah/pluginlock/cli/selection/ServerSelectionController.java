package dev.noah.pluginlock.cli.selection;

import dev.noah.pluginlock.cli.io.Terminal;
import dev.noah.pluginlock.core.PluginLockDefaults;

import java.util.List;
import java.util.Locale;

public final class ServerSelectionController {
    private final Terminal terminal;

    public ServerSelectionController(Terminal terminal) {
        this.terminal = terminal;
    }

    public String chooseServer(String explicit, String fallback, boolean assumeYes) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim().toLowerCase(Locale.ROOT);
        }
        if (assumeYes) {
            return fallback;
        }
        terminal.println("");
        terminal.println("Server software:");
        terminal.println("1. paper (default)");
        terminal.println("2. purpur");
        String answer = terminal.readLine("Select server [1]: ");
        if ("2".equals(answer) || "purpur".equalsIgnoreCase(answer)) {
            return "purpur";
        }
        return fallback;
    }

    public String chooseMinecraftVersion(String explicit, List<String> versions, boolean assumeYes) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        if (versions.isEmpty()) {
            return PluginLockDefaults.MINECRAFT_VERSION;
        }
        if (assumeYes) {
            return versions.getFirst();
        }
        terminal.println("");
        terminal.println("Minecraft versions:");
        int limit = Math.min(10, versions.size());
        for (int index = 0; index < limit; index++) {
            terminal.println((index + 1) + ". " + versions.get(index) + (index == 0 ? " (default)" : ""));
        }
        String answer = terminal.readLine("Select Minecraft version [1]: ");
        if (answer.isBlank()) {
            return versions.getFirst();
        }
        try {
            int selected = Integer.parseInt(answer);
            if (selected >= 1 && selected <= limit) {
                return versions.get(selected - 1);
            }
        } catch (NumberFormatException ignored) {
            for (String version : versions) {
                if (version.equalsIgnoreCase(answer)) {
                    return version;
                }
            }
        }
        terminal.println("Invalid selection; using " + versions.getFirst());
        return versions.getFirst();
    }
}
