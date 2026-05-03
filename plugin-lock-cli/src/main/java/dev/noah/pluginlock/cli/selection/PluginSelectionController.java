package dev.noah.pluginlock.cli.selection;

import dev.noah.pluginlock.cli.io.Ansi;
import dev.noah.pluginlock.cli.io.Terminal;
import dev.noah.pluginlock.cli.output.CliOutput;
import dev.noah.pluginlock.core.catalog.PluginCatalog;
import dev.noah.pluginlock.core.catalog.PluginCoordinate;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginMetadata;
import dev.noah.pluginlock.core.model.PluginRequest;
import dev.noah.pluginlock.core.model.PluginResolutionCheck;
import dev.noah.pluginlock.core.provider.PluginNotFoundException;

import java.util.List;
import java.util.Locale;

public final class PluginSelectionController {
    private final PluginCatalog catalog;
    private final Terminal terminal;
    private final CliOutput output;

    public PluginSelectionController(PluginCatalog catalog, Terminal terminal, CliOutput output) {
        this.catalog = catalog;
        this.terminal = terminal;
        this.output = output;
    }

    public PluginSelection selectPlugin(String provider, String id, String version, boolean assumeYes,
                                        String minecraftVersion, String loader) throws Exception {
        PluginCoordinate coordinate = PluginCoordinate.parse(id, provider, version);
        provider = coordinate.provider();
        id = coordinate.id();
        version = coordinate.version();
        if (!"auto".equalsIgnoreCase(provider)) {
            if (!assumeYes) {
                PluginMetadata metadata = catalog.fetchMetadata(provider, id);
                return selectAndConfirmProvider(List.of(metadata), version, minecraftVersion, loader);
            }
            return PluginSelection.selected(new PluginRequest(id, provider.toLowerCase(Locale.ROOT), version));
        }

        List<PluginMetadata> matches = catalog.findProviderMatches(id);
        if (matches.isEmpty()) {
            throw pluginNotFoundWithSuggestions(id);
        }
        PluginMetadata selected = matches.getFirst();
        if (matches.size() > 1 && assumeYes) {
            printProviderMatches(id, matches);
        }
        if (!assumeYes) {
            printProviderMatches(id, matches);
            return selectAndConfirmProvider(matches, version, minecraftVersion, loader);
        }
        return PluginSelection.selected(new PluginRequest(selected.getId(), selected.getProvider(), version));
    }

    public PluginSelection selectAndConfirmProvider(List<PluginMetadata> matches, String version,
                                                    String minecraftVersion, String loader) {
        int selectedIndex = 0;
        while (true) {
            PluginMetadata selected = matches.get(selectedIndex);
            output.printMetadata(selected);
            printVersionPreflight(selected, version, minecraftVersion, loader);
            String answer = terminal.readLine("Install " + selected.getProvider() + ":" + selected.getId() + " "
                    + Ansi.yellow("[Y/n]") + " or provider number: ");
            if (answer.isBlank() || isInstallAnswer(answer)) {
                return PluginSelection.selected(new PluginRequest(selected.getId(), selected.getProvider(), version));
            }
            if (isExitAnswer(answer)) {
                return PluginSelection.exited();
            }
            Integer selectedOption = parseOption(answer);
            if (selectedOption != null && selectedOption >= 1 && selectedOption <= matches.size()) {
                selectedIndex = selectedOption - 1;
                printProviderMatches(matches.getFirst().getId(), matches, selectedIndex);
                continue;
            }
            terminal.println(Ansi.yellow("Invalid selection; enter y to install, n to skip, or a provider number."));
        }
    }

    public void printProviderMatches(String id, List<PluginMetadata> matches) {
        printProviderMatches(id, matches, 0);
    }

    public void printProviderMatches(String id, List<PluginMetadata> matches, int selectedIndex) {
        terminal.println("");
        terminal.println(Ansi.bold("Found " + matches.size() + " provider match(es) for " + id + ":"));
        for (int index = 0; index < matches.size(); index++) {
            PluginMetadata metadata = matches.get(index);
            String defaultLabel = index == selectedIndex ? Ansi.yellow(" (default)") : "";
            terminal.println((index + 1) + ". " + Ansi.blue(metadata.getProvider() + ":" + metadata.getId())
                    + " - " + metadata.getName() + defaultLabel);
            if (metadata.getDescription() != null && !metadata.getDescription().isBlank()) {
                terminal.println(Ansi.dim("   " + metadata.getDescription()));
            }
            terminal.println(Ansi.dim("   Downloads: " + String.format(Locale.US, "%,d", metadata.getDownloads())));
        }
        terminal.println("");
    }

    public static String suggestionsMessage(List<PluginMetadata> suggestions) {
        StringBuilder message = new StringBuilder();
        message.append(System.lineSeparator()).append("Did you mean:");
        for (PluginMetadata suggestion : suggestions) {
            message.append(System.lineSeparator())
                    .append("  ")
                    .append(suggestion.getProvider())
                    .append(":")
                    .append(suggestion.getId());
            if (suggestion.getName() != null && !suggestion.getName().isBlank()
                    && !suggestion.getName().equalsIgnoreCase(suggestion.getId())) {
                message.append(" (").append(suggestion.getName()).append(")");
            }
        }
        return message.toString();
    }

    public static Integer parseOption(String answer) {
        try {
            return Integer.parseInt(answer);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean isExitAnswer(String answer) {
        return "0".equals(answer)
                || "n".equalsIgnoreCase(answer)
                || "no".equalsIgnoreCase(answer)
                || "e".equalsIgnoreCase(answer)
                || "exit".equalsIgnoreCase(answer)
                || "q".equalsIgnoreCase(answer)
                || "quit".equalsIgnoreCase(answer)
                || "x".equalsIgnoreCase(answer)
                || "cancel".equalsIgnoreCase(answer);
    }

    private PluginNotFoundException pluginNotFoundWithSuggestions(String id) {
        try {
            List<PluginMetadata> suggestions = catalog.search("auto", id, 5);
            if (!suggestions.isEmpty()) {
                return new PluginNotFoundException("Modrinth or Hangar", id + suggestionsMessage(suggestions));
            }
        } catch (Exception ignored) {
        }
        return new PluginNotFoundException("Modrinth or Hangar", id);
    }

    private void printVersionPreflight(PluginMetadata metadata, String version, String minecraftVersion, String loader) {
        if (minecraftVersion == null || minecraftVersion.isBlank()
                || loader == null || loader.isBlank()
                || version == null || version.isBlank()
                || "latest".equalsIgnoreCase(version)) {
            return;
        }
        try {
            PluginResolutionCheck check = catalog.check(new PluginRequest(metadata.getId(), metadata.getProvider(), version),
                    minecraftVersion, loader);
            if (!check.isResolvable()) {
                terminal.println(Ansi.yellow("Version check: ") + check.getMessage());
                terminal.println("");
                return;
            }
            LockedPlugin resolved = check.getPlugin();
            terminal.println(Ansi.green("Version OK: ") + resolved.getVersionName()
                    + " supports " + loader + " " + minecraftVersion);
            if (resolved.getCompatibilityWarning() != null && !resolved.getCompatibilityWarning().isBlank()) {
                terminal.println(Ansi.yellow("Warning: ") + resolved.getCompatibilityWarning());
            }
            terminal.println("");
        } catch (Exception exception) {
            terminal.println(Ansi.yellow("Version check: ") + exception.getMessage());
            terminal.println("");
        }
    }

    private static boolean isInstallAnswer(String answer) {
        return "y".equalsIgnoreCase(answer)
                || "yes".equalsIgnoreCase(answer)
                || "i".equalsIgnoreCase(answer)
                || "install".equalsIgnoreCase(answer);
    }
}
