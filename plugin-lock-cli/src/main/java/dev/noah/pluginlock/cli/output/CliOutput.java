package dev.noah.pluginlock.cli.output;

import dev.noah.pluginlock.cli.io.Ansi;
import dev.noah.pluginlock.cli.io.Terminal;
import dev.noah.pluginlock.core.doctor.DoctorCheck;
import dev.noah.pluginlock.core.doctor.DoctorReport;
import dev.noah.pluginlock.core.doctor.DoctorStatus;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginInspection;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginMetadata;
import dev.noah.pluginlock.core.model.PluginVersion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CliOutput {
    private final Terminal terminal;
    private final OutputRenderer renderer;
    private final boolean json;

    public CliOutput(Terminal terminal, boolean verbose, boolean json) {
        this.terminal = terminal;
        this.json = json;
        this.renderer = json ? new JsonOutputRenderer(terminal) : new TextOutputRenderer(terminal, verbose);
    }

    public void success(String command, String message, Map<String, ?> details) {
        renderer.render(new OutputEnvelope("success", command, message, details));
    }

    public void error(String command, String message) {
        renderer.render(new OutputEnvelope("error", command, message, Map.of()));
    }

    public void warning(String message) {
        renderer.warning(message);
    }

    public void list(PluginLock lock) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("minecraftVersion", nullToBlank(lock.getMinecraftVersion()));
        details.put("loader", nullToBlank(lock.getLoader()));
        details.put("server", serverDetails(lock));
        details.put("plugins", lock.getPlugins().stream().map(CliOutput::pluginDetails).toList());
        if (json) {
            success("list", "Listed " + lock.getPlugins().size() + " plugin(s)", details);
            return;
        }

        terminal.println(Ansi.bold("Minecraft: ") + nullToBlank(lock.getMinecraftVersion())
                + "  " + Ansi.bold("Loader: ") + nullToBlank(lock.getLoader()));
        if (lock.getServer() != null) {
            terminal.println(Ansi.bold("Server: ") + nullToBlank(lock.getServer().getProvider())
                    + " " + nullToBlank(lock.getServer().getMinecraftVersion())
                    + " build " + nullToBlank(lock.getServer().getBuild())
                    + " (" + nullToBlank(lock.getServer().getFileName()) + ")");
        }
        if (lock.getPlugins().isEmpty()) {
            terminal.println("No locked plugins.");
            return;
        }
        terminal.println("");
        for (LockedPlugin plugin : lock.getPlugins()) {
            terminal.println(Ansi.blue(plugin.getProvider() + ":" + plugin.getId())
                    + "  " + nullToBlank(plugin.getName())
                    + "  " + nullToBlank(plugin.getVersionName())
                    + "  " + Ansi.dim(nullToBlank(plugin.getFileName())));
            if (plugin.getCompatibilityWarning() != null && !plugin.getCompatibilityWarning().isBlank()) {
                terminal.println(Ansi.yellow("  Warning: ") + plugin.getCompatibilityWarning());
            }
        }
    }

    public void doctor(DoctorReport report) {
        List<DoctorCheck> checks = report.checks();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("checks", checks.stream()
                .map(check -> Map.of(
                        "status", check.status().name().toLowerCase(Locale.ROOT),
                        "check", check.check(),
                        "message", check.message()))
                .toList());
        if (json) {
            renderer.render(new OutputEnvelope(report.hasErrors() ? "error" : "success", "doctor",
                    report.hasErrors() ? "Doctor found errors" : "Doctor checks passed", details));
            return;
        }
        for (DoctorCheck check : checks) {
            String marker = switch (check.status()) {
                case OK -> Ansi.green("OK");
                case WARNING -> Ansi.yellow("WARN");
                case ERROR -> Ansi.red("FAIL");
            };
            terminal.println(marker + " " + check.message());
        }
    }

    public void search(String query, List<PluginMetadata> results) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("query", query);
        details.put("results", results.stream()
                .map(metadata -> Map.of(
                        "provider", metadata.getProvider(),
                        "id", metadata.getId(),
                        "name", metadata.getName(),
                        "description", metadata.getDescription() == null ? "" : metadata.getDescription(),
                        "downloads", metadata.getDownloads()))
                .toList());
        if (json) {
            success("search", "Found " + results.size() + " plugin match(es)", details);
            return;
        }
        if (results.isEmpty()) {
            terminal.println("No plugin matches found for " + query);
            return;
        }
        for (PluginMetadata metadata : results) {
            terminal.println(Ansi.blue(metadata.getProvider() + ":" + metadata.getId())
                    + "  " + metadata.getName()
                    + "  " + Ansi.dim(String.format(Locale.US, "%,d downloads", metadata.getDownloads())));
            if (metadata.getDescription() != null && !metadata.getDescription().isBlank()) {
                terminal.println(Ansi.dim("  " + metadata.getDescription()));
            }
        }
    }

    public void info(List<PluginInspection> infos, String minecraftVersion, String loader) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("minecraftVersion", nullToBlank(minecraftVersion));
        details.put("loader", nullToBlank(loader));
        details.put("plugins", infos.stream().map(CliOutput::inspectionDetails).toList());
        if (json) {
            success("info", "Inspected " + infos.size() + " plugin(s)", details);
            return;
        }
        for (PluginInspection info : infos) {
            printMetadata(info.getMetadata());
            if (info.getVersions().isEmpty()) {
                terminal.println("No versions found.");
                continue;
            }
            terminal.println(Ansi.bold("Versions:"));
            for (PluginVersion version : info.getVersions()) {
                String marker = supportsMinecraft(version, minecraftVersion) ? Ansi.green("OK") : Ansi.dim("--");
                terminal.println(marker + " " + version.getName()
                        + "  " + Ansi.dim(minecraftSummary(version.getMinecraftVersions()))
                        + loaderSummary(version.getLoaders(), loader)
                        + fileSummary(version));
            }
            terminal.println("");
        }
    }

    public void printMetadata(PluginMetadata metadata) {
        terminal.println("");
        terminal.println(Ansi.bold(metadata.getName()) + " (" + Ansi.blue(metadata.getProvider() + ":" + metadata.getId()) + ")");
        if (!metadata.getAuthors().isEmpty()) {
            terminal.println("Authors: " + String.join(", ", metadata.getAuthors()));
        }
        terminal.println("Downloads: " + String.format(Locale.US, "%,d", metadata.getDownloads()));
        if (metadata.getDescription() != null && !metadata.getDescription().isBlank()) {
            terminal.println("Description: " + metadata.getDescription());
        }
        terminal.println("");
    }

    public static String loaderSummary(List<String> loaders, String selectedLoader) {
        if (loaders == null || loaders.isEmpty()) {
            return "";
        }
        if (selectedLoader != null && !selectedLoader.isBlank()
                && loaders.stream().anyMatch(loader -> loader.equalsIgnoreCase(selectedLoader))) {
            return "";
        }
        return "  " + Ansi.dim(String.join("/", loaders));
    }

    public static String minecraftSummary(List<String> versions) {
        if (versions == null || versions.isEmpty()) {
            return "Minecraft: unknown";
        }
        if (versions.size() <= 4) {
            return "MC " + String.join(", ", versions);
        }
        return "MC " + versions.getFirst() + "..." + versions.getLast()
                + " (" + versions.size() + ")";
    }

    private static Map<String, ?> inspectionDetails(PluginInspection info) {
        Map<String, Object> details = new LinkedHashMap<>();
        PluginMetadata metadata = info.getMetadata();
        details.put("provider", metadata.getProvider());
        details.put("id", metadata.getId());
        details.put("name", metadata.getName());
        details.put("description", metadata.getDescription());
        details.put("downloads", metadata.getDownloads());
        details.put("authors", metadata.getAuthors());
        details.put("versions", info.getVersions().stream().map(CliOutput::versionDetails).toList());
        return details;
    }

    private static String fileSummary(PluginVersion version) {
        if (version.getFileName() == null || version.getFileName().isBlank()) {
            return "";
        }
        return "  " + Ansi.dim(version.getFileName());
    }

    private static Map<String, ?> versionDetails(PluginVersion version) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", nullToBlank(version.getId()));
        details.put("name", nullToBlank(version.getName()));
        details.put("minecraftVersions", version.getMinecraftVersions());
        details.put("loaders", version.getLoaders());
        details.put("fileName", nullToBlank(version.getFileName()));
        details.put("downloadable", version.isDownloadable());
        return details;
    }

    private static boolean supportsMinecraft(PluginVersion version, String minecraftVersion) {
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            return false;
        }
        return version.getMinecraftVersions().stream()
                .anyMatch(candidate -> dev.noah.pluginlock.core.provider.MinecraftVersions.supports(candidate, minecraftVersion));
    }

    private static Map<String, ?> serverDetails(PluginLock lock) {
        if (lock.getServer() == null) {
            return Map.of();
        }
        return Map.of(
                "provider", nullToBlank(lock.getServer().getProvider()),
                "minecraftVersion", nullToBlank(lock.getServer().getMinecraftVersion()),
                "build", nullToBlank(lock.getServer().getBuild()),
                "fileName", nullToBlank(lock.getServer().getFileName())
        );
    }

    private static Map<String, ?> pluginDetails(LockedPlugin plugin) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("provider", nullToBlank(plugin.getProvider()));
        details.put("id", nullToBlank(plugin.getId()));
        details.put("name", nullToBlank(plugin.getName()));
        details.put("version", nullToBlank(plugin.getVersionName()));
        details.put("versionId", nullToBlank(plugin.getVersionId()));
        details.put("fileName", nullToBlank(plugin.getFileName()));
        details.put("size", plugin.getSize());
        if (plugin.getCompatibilityWarning() != null && !plugin.getCompatibilityWarning().isBlank()) {
            details.put("compatibilityWarning", plugin.getCompatibilityWarning());
        }
        return details;
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
