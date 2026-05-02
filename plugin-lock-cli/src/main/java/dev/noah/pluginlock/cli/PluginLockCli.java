package dev.noah.pluginlock.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.noah.pluginlock.core.DownloadProgress;
import dev.noah.pluginlock.core.PluginInstaller;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.PluginResolver;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.LockedServer;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginMetadata;
import dev.noah.pluginlock.core.model.PluginRequest;
import dev.noah.pluginlock.core.provider.HangarProvider;
import dev.noah.pluginlock.core.provider.ModrinthProvider;
import dev.noah.pluginlock.core.provider.PluginNotFoundException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Parameters;

import java.io.Console;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "pl",
        mixinStandardHelpOptions = true,
        version = "plugin-lock 0.1.0",
        description = "Lock and install Minecraft server plugins.",
        subcommands = {
                PluginLockCli.InitCommand.class,
                PluginLockCli.AddCommand.class,
                PluginLockCli.LockCommand.class,
                PluginLockCli.InstallCommand.class,
                PluginLockCli.CleanInstallCommand.class,
                PluginLockCli.RemoveCommand.class
        }
)
public final class PluginLockCli implements Callable<Integer> {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String DEFAULT_MINECRAFT_VERSION = "1.21.4";
    private static final String DEFAULT_LOADER = "paper";

    @Option(names = "--project-dir", defaultValue = ".", description = "Directory containing server-lock files.")
    Path projectDir;
    @Option(names = "--verbose", description = "Show detailed command output.")
    boolean verbose;
    @Option(names = "--json", description = "Emit machine-readable JSON events.")
    boolean json;
    private Path effectiveProjectDir;
    private final ModrinthProvider modrinthProvider = new ModrinthProvider(HttpClient.newHttpClient());
    private final HangarProvider hangarProvider = new HangarProvider(HttpClient.newHttpClient());
    private final ServerDownloads serverDownloads;
    private final LockResolver lockResolver;

    public PluginLockCli() {
        this(new ServerDownloads(HttpClient.newHttpClient()), new PluginResolver()::resolve);
    }

    PluginLockCli(ServerDownloads serverDownloads) {
        this(serverDownloads, new PluginResolver()::resolve);
    }

    PluginLockCli(ServerDownloads serverDownloads, LockResolver lockResolver) {
        this.serverDownloads = serverDownloads;
        this.lockResolver = lockResolver;
    }

    public static void main(String[] args) {
        int exitCode = commandLine(new PluginLockCli()).execute(args);
        System.exit(exitCode);
    }

    static CommandLine commandLine(PluginLockCli cli) {
        return new CommandLine(cli)
                .setExecutionExceptionHandler(new FriendlyExecutionExceptionHandler());
    }

    Output output() {
        return new Output(verbose, json);
    }

    @Override
    public Integer call() {
        new CommandLine(this).usage(System.out);
        return 0;
    }

    Path resolve(String fileName) {
        return effectiveProjectDir().resolve(fileName);
    }

    Path pluginsDir(Path configuredPluginsDir) {
        return configuredPluginsDir.isAbsolute() ? configuredPluginsDir : effectiveProjectDir().resolve(configuredPluginsDir);
    }

    Path effectiveProjectDir() {
        if (effectiveProjectDir == null) {
            effectiveProjectDir = detectProjectDir(projectDir);
        }
        return effectiveProjectDir;
    }

    static Path detectProjectDir(Path configuredProjectDir) {
        Path current = configuredProjectDir.toAbsolutePath().normalize();
        if (hasProjectFiles(current)) {
            return current;
        }
        Path fileName = current.getFileName();
        if (fileName != null && "plugins".equalsIgnoreCase(fileName.toString()) && current.getParent() != null) {
            return current.getParent();
        }
        Path pluginsChild = current.resolve("plugins");
        if (Files.isDirectory(pluginsChild)) {
            return current;
        }
        return current;
    }

    private static boolean hasProjectFiles(Path directory) {
        return Files.exists(directory.resolve(PluginLockFiles.MANIFEST_FILE))
                || Files.exists(directory.resolve(PluginLockFiles.LOCK_FILE));
    }

    PluginManifest readManifestOrDefault() throws Exception {
        Path manifestPath = resolve(PluginLockFiles.MANIFEST_FILE);
        return Files.exists(manifestPath) ? PluginLockFiles.readManifest(manifestPath) : new PluginManifest();
    }

    void writeManifest(PluginManifest manifest) throws Exception {
        PluginLockFiles.writeManifest(resolve(PluginLockFiles.MANIFEST_FILE), manifest);
    }

    PluginLock resolveLock(PluginManifest manifest) throws Exception {
        PluginLock lock = lockResolver.resolve(manifest);
        Path lockPath = resolve(PluginLockFiles.LOCK_FILE);
        if (Files.exists(lockPath)) {
            lock.setServer(PluginLockFiles.readLock(lockPath).getServer());
        }
        return lock;
    }

    interface LockResolver {
        PluginLock resolve(PluginManifest manifest) throws Exception;
    }

    void writeLock(PluginLock lock) throws Exception {
        PluginLockFiles.writeLock(resolve(PluginLockFiles.LOCK_FILE), lock);
        lock.getPlugins().stream()
                .map(LockedPlugin::getCompatibilityWarning)
                .filter(warning -> warning != null && !warning.isBlank())
                .forEach(output()::warning);
    }

    PluginLock resolveAndWriteLock(PluginManifest manifest) throws Exception {
        PluginLock lock = resolveLock(manifest);
        writeLock(lock);
        return lock;
    }

    static void ensurePluginsList(PluginManifest manifest) {
        if (manifest.getPlugins() == null) {
            manifest.setPlugins(new ArrayList<>());
        }
    }

    PluginMetadata fetchMetadata(String provider, String id) throws Exception {
        if ("modrinth".equalsIgnoreCase(provider)) {
            return modrinthProvider.fetchMetadata(id);
        }
        if ("hangar".equalsIgnoreCase(provider)) {
            return hangarProvider.fetchMetadata(id);
        }
        throw new IllegalArgumentException("Unsupported provider: " + provider);
    }

    PluginRequest selectPlugin(String provider, String id, String version, boolean assumeYes) throws Exception {
        if (!"auto".equalsIgnoreCase(provider)) {
            if (!assumeYes) {
                PluginMetadata metadata = fetchMetadata(provider, id);
                printMetadata(metadata);
                if (!confirm("Install this plugin? [y/N] ")) {
                    return null;
                }
                id = metadata.getId();
            }
            return new PluginRequest(id, provider.toLowerCase(Locale.ROOT), version);
        }

        List<PluginMetadata> matches = findProviderMatches(id);
        if (matches.isEmpty()) {
            throw new PluginNotFoundException("Modrinth or Hangar", id);
        }
        PluginMetadata selected = matches.get(0);
        if (matches.size() > 1 || !assumeYes) {
            printProviderMatches(id, matches);
        }
        if (matches.size() > 1 && !assumeYes) {
            selected = confirmOrSelectProvider(matches);
            if (selected == null) {
                return null;
            }
        }
        if (!assumeYes && matches.size() == 1 && !confirm("Install this plugin? [Y/n] ")) {
            return null;
        }
        return new PluginRequest(selected.getId(), selected.getProvider(), version);
    }

    private List<PluginMetadata> findProviderMatches(String id) throws Exception {
        List<PluginMetadata> matches = new ArrayList<>();
        addIfFound(matches, "modrinth", id);
        addIfFound(matches, "hangar", id);
        return matches;
    }

    private void addIfFound(List<PluginMetadata> matches, String provider, String id) throws Exception {
        try {
            matches.add(fetchMetadata(provider, id));
        } catch (PluginNotFoundException ignored) {
        }
    }

    private static void printProviderMatches(String id, List<PluginMetadata> matches) {
        System.out.println();
        System.out.println(Ansi.bold("Found " + matches.size() + " provider match(es) for " + id + ":"));
        for (int index = 0; index < matches.size(); index++) {
            PluginMetadata metadata = matches.get(index);
            String defaultLabel = index == 0 ? Ansi.yellow(" (default)") : "";
            System.out.println((index + 1) + ". " + Ansi.blue(metadata.getProvider() + ":" + metadata.getId())
                    + " - " + metadata.getName() + defaultLabel);
            if (metadata.getDescription() != null && !metadata.getDescription().isBlank()) {
                System.out.println(Ansi.dim("   " + metadata.getDescription()));
            }
            System.out.println(Ansi.dim("   Downloads: " + String.format(Locale.US, "%,d", metadata.getDownloads())));
        }
        System.out.println();
    }

    private static PluginMetadata confirmOrSelectProvider(List<PluginMetadata> matches) {
        PluginMetadata selected = matches.get(0);
        if (confirm("Install " + selected.getProvider() + ":" + selected.getId() + "? " + Ansi.yellow("[Y/n]") + " ")) {
            return selected;
        }
        int fallbackIndex = matches.size() > 1 ? 1 : 0;
        selected = selectProvider(matches, fallbackIndex);
        printMetadata(selected);
        if (confirm("Install this plugin? " + Ansi.yellow("[Y/n]") + " ")) {
            return selected;
        }
        return null;
    }

    static PluginMetadata selectProvider(List<PluginMetadata> matches, int fallbackIndex) {
        String answer = readConfirmation("Select provider " + Ansi.yellow("[" + (fallbackIndex + 1) + "]") + ": ");
        if (answer.isBlank()) {
            return matches.get(fallbackIndex);
        }
        try {
            int selected = Integer.parseInt(answer);
            if (selected >= 1 && selected <= matches.size()) {
                return matches.get(selected - 1);
            }
        } catch (NumberFormatException ignored) {
        }
        PluginMetadata fallback = matches.get(fallbackIndex);
        System.out.println(Ansi.yellow("Invalid selection; using " + fallback.getProvider() + ":" + fallback.getId()));
        return fallback;
    }

    private static boolean confirm(String prompt) {
        String answer = readConfirmation(prompt);
        return answer.isBlank() || "y".equalsIgnoreCase(answer) || "yes".equalsIgnoreCase(answer);
    }

    static void printMetadata(PluginMetadata metadata) {
        System.out.println();
        System.out.println(Ansi.bold(metadata.getName()) + " (" + Ansi.blue(metadata.getProvider() + ":" + metadata.getId()) + ")");
        if (!metadata.getAuthors().isEmpty()) {
            System.out.println("Authors: " + String.join(", ", metadata.getAuthors()));
        }
        System.out.println("Downloads: " + String.format(Locale.US, "%,d", metadata.getDownloads()));
        if (metadata.getDescription() != null && !metadata.getDescription().isBlank()) {
            System.out.println("Description: " + metadata.getDescription());
        }
        System.out.println();
    }

    private static String readConfirmation(String prompt) {
        Console console = System.console();
        if (console != null) {
            String answer = console.readLine(prompt);
            return answer == null ? "" : answer.trim();
        }
        System.out.print(prompt);
        try {
            StringBuilder answer = new StringBuilder();
            while (true) {
                int next = System.in.read();
                if (next == -1 || next == '\n') {
                    break;
                }
                if (next != '\r') {
                    answer.append((char) next);
                }
            }
            return answer.toString().trim();
        } catch (Exception exception) {
            return "";
        }
    }

    @Command(name = "init", mixinStandardHelpOptions = true, description = "Create a server-lock.json manifest.")
    static final class InitCommand implements Callable<Integer> {
        @ParentCommand
        PluginLockCli parent;

        @Option(names = "--minecraft", description = "Minecraft version.")
        String minecraftVersion;

        @Option(names = "--server", description = "Server provider: paper or purpur.")
        String server;

        @Option(names = {"-y", "--yes"}, description = "Accept detected/default values without prompting.")
        boolean yes;

        @Override
        public Integer call() throws Exception {
            Path manifestPath = parent.resolve(PluginLockFiles.MANIFEST_FILE);
            if (Files.exists(manifestPath)) {
                throw new IllegalStateException(PluginLockFiles.MANIFEST_FILE + " already exists");
            }
            String resolvedServer = chooseServer(server, DEFAULT_LOADER, yes);
            List<String> versions = parent.serverDownloads.versions(resolvedServer);
            String resolvedMinecraftVersion = chooseMinecraftVersion(minecraftVersion, versions, yes);
            LockedServer lockedServer = parent.serverDownloads.latest(resolvedServer, resolvedMinecraftVersion);
            Path serverJar = parent.serverDownloads.download(lockedServer, parent.effectiveProjectDir(), parent.downloadProgress());

            PluginManifest manifest = new PluginManifest();
            manifest.setMinecraftVersion(resolvedMinecraftVersion);
            manifest.setLoader(ServerDownloads.pluginLoaderFor(resolvedServer));
            PluginLockFiles.writeManifest(manifestPath, manifest);

            PluginLock lock = new PluginLock();
            lock.setMinecraftVersion(manifest.getMinecraftVersion());
            lock.setLoader(manifest.getLoader());
            lock.setServer(lockedServer);
            PluginLockFiles.writeLock(parent.resolve(PluginLockFiles.LOCK_FILE), lock);

            parent.output().success("init", "Initialized " + manifest.getMinecraftVersion() + " " + lockedServer.getProvider(), Map.of(
                    "manifest", PluginLockFiles.MANIFEST_FILE,
                    "lockfile", PluginLockFiles.LOCK_FILE,
                    "server", lockedServer.getProvider(),
                    "minecraftVersion", manifest.getMinecraftVersion(),
                    "loader", manifest.getLoader(),
                    "build", lockedServer.getBuild(),
                    "serverJar", serverJar.getFileName().toString(),
                    "downloadUrl", lockedServer.getDownloadUrl(),
                    "sha256", lockedServer.getSha256() == null ? "" : lockedServer.getSha256()
            ));
            return 0;
        }
    }

    private static String chooseServer(String explicit, String fallback, boolean assumeYes) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim().toLowerCase(Locale.ROOT);
        }
        if (assumeYes) {
            return fallback;
        }
        System.out.println();
        System.out.println("Server software:");
        System.out.println("1. paper (default)");
        System.out.println("2. purpur");
        String answer = readConfirmation("Select server [1]: ");
        if ("2".equals(answer) || "purpur".equalsIgnoreCase(answer)) {
            return "purpur";
        }
        return fallback;
    }

    private static String chooseMinecraftVersion(String explicit, List<String> versions, boolean assumeYes) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        if (versions.isEmpty()) {
            return DEFAULT_MINECRAFT_VERSION;
        }
        if (assumeYes) {
            return versions.getFirst();
        }
        System.out.println();
        System.out.println("Minecraft versions:");
        int limit = Math.min(10, versions.size());
        for (int index = 0; index < limit; index++) {
            System.out.println((index + 1) + ". " + versions.get(index) + (index == 0 ? " (default)" : ""));
        }
        String answer = readConfirmation("Select Minecraft version [1]: ");
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
        System.out.println("Invalid selection; using " + versions.getFirst());
        return versions.getFirst();
    }

    @Command(name = "add", mixinStandardHelpOptions = true, description = "Add a plugin request to the manifest.")
    static final class AddCommand implements Callable<Integer> {
        @ParentCommand
        PluginLockCli parent;

        @Parameters(index = "0", description = "Provider project slug or id, for example luckperms.")
        String id;

        @Option(names = "--provider", defaultValue = "auto", description = "Plugin provider: auto, modrinth, or hangar.")
        String provider;

        @Option(names = "--version", defaultValue = "latest", description = "Version id, version number, or latest.")
        String version;

        @Option(names = {"-y", "--yes"}, description = "Skip plugin metadata confirmation.")
        boolean yes;

        @Override
        public Integer call() throws Exception {
            PluginManifest manifest = parent.readManifestOrDefault();
            ensurePluginsList(manifest);
            PluginRequest request = parent.selectPlugin(provider, id, version, yes);
            if (request == null) {
                System.out.println("Cancelled");
                return 1;
            }
            addOrReplace(manifest, request);
            parent.writeManifest(manifest);
            parent.output().success("add", "Added " + request.getId(), Map.of(
                    "id", request.getId(),
                    "provider", request.getProvider(),
                    "version", request.getVersion()
            ));
            return 0;
        }
    }

    @Command(name = "lock", hidden = true, mixinStandardHelpOptions = true, description = "Resolve server-lock.json into server-lock.lock.json.")
    static final class LockCommand implements Callable<Integer> {
        @ParentCommand
        PluginLockCli parent;

        @Override
        public Integer call() throws Exception {
            PluginManifest manifest = PluginLockFiles.readManifest(parent.resolve(PluginLockFiles.MANIFEST_FILE));
            PluginLock lock = parent.resolveAndWriteLock(manifest);
            parent.output().success("lock", "Locked " + lock.getPlugins().size() + " plugin(s)", Map.of(
                    "count", lock.getPlugins().size(),
                    "plugins", lock.getPlugins().stream().map(LockedPlugin::getName).toList()
            ));
            return 0;
        }
    }

    @Command(name = "install", aliases = "i", mixinStandardHelpOptions = true, description = "Install plugins and update the lockfile when a manifest is present.")
    static final class InstallCommand implements Callable<Integer> {
        @ParentCommand
        PluginLockCli parent;

        @Parameters(arity = "0..*", description = "Optional provider project slugs or ids to add before installing.")
        List<String> ids = List.of();

        @Option(names = "--provider", defaultValue = "auto", description = "Plugin provider for new package arguments: auto, modrinth, or hangar.")
        String provider;

        @Option(names = "--version", defaultValue = "latest", description = "Version id, version number, or latest for new package arguments.")
        String version;

        @Option(names = "--plugins-dir", defaultValue = "plugins", description = "Destination plugins directory.")
        Path pluginsDir;

        @Option(names = {"-y", "--yes"}, description = "Skip plugin metadata confirmation for new plugins.")
        boolean yes;

        @Override
        public Integer call() throws Exception {
            Path resolvedPluginsDir = parent.pluginsDir(pluginsDir);
            PluginLock lock;
            Path manifestPath = parent.resolve(PluginLockFiles.MANIFEST_FILE);
            Path lockfilePath = parent.resolve(PluginLockFiles.LOCK_FILE);

            if (!ids.isEmpty() || Files.exists(manifestPath)) {
                PluginManifest manifest = parent.readManifestOrDefault();
                ensurePluginsList(manifest);
                for (String id : ids) {
                    PluginRequest request = parent.selectPlugin(provider, id, version, yes);
                    if (request == null) {
                        System.out.println("Cancelled");
                        return 1;
                    }
                    addOrReplace(manifest, request);
                }
                lock = parent.resolveLock(manifest);
                parent.writeManifest(manifest);
                parent.writeLock(lock);
            } else if (Files.exists(lockfilePath)) {
                lock = PluginLockFiles.readLock(lockfilePath);
            } else {
                throw new IllegalStateException("No server-lock.json or server-lock.lock.json found. Run `pl init` first.");
            }

            new PluginInstaller().install(lock, resolvedPluginsDir, parent.downloadProgress());
            parent.output().success("install", "Installed " + lock.getPlugins().size() + " plugin(s)", Map.of(
                    "count", lock.getPlugins().size(),
                    "pluginsDir", resolvedPluginsDir.toString(),
                    "files", lock.getPlugins().stream().map(LockedPlugin::getFileName).toList()
            ));
            return 0;
        }
    }

    @Command(name = "clean-install", aliases = "ci", mixinStandardHelpOptions = true, description = "Install exactly from server-lock.lock.json without resolving new versions.")
    static final class CleanInstallCommand implements Callable<Integer> {
        @ParentCommand
        PluginLockCli parent;

        @Option(names = "--plugins-dir", defaultValue = "plugins", description = "Destination plugins directory.")
        Path pluginsDir;

        @Override
        public Integer call() throws Exception {
            Path resolvedPluginsDir = parent.pluginsDir(pluginsDir);
            PluginLock lock = PluginLockFiles.readLock(parent.resolve(PluginLockFiles.LOCK_FILE));
            Files.createDirectories(resolvedPluginsDir);
            for (dev.noah.pluginlock.core.model.LockedPlugin plugin : lock.getPlugins()) {
                Files.deleteIfExists(resolvedPluginsDir.resolve(plugin.getFileName()));
            }
            new PluginInstaller().install(lock, resolvedPluginsDir, parent.downloadProgress());
            parent.output().success("clean-install", "Clean installed " + lock.getPlugins().size() + " plugin(s)", Map.of(
                    "count", lock.getPlugins().size(),
                    "pluginsDir", resolvedPluginsDir.toString(),
                    "files", lock.getPlugins().stream().map(LockedPlugin::getFileName).toList()
            ));
            return 0;
        }
    }

    @Command(name = "remove", aliases = {"rm", "uninstall"}, mixinStandardHelpOptions = true, description = "Remove plugins recorded in server-lock files.")
    static final class RemoveCommand implements Callable<Integer> {
        @ParentCommand
        PluginLockCli parent;

        @Parameters(arity = "1..*", description = "Plugin ids to remove.")
        List<String> ids;

        @Option(names = "--plugins-dir", defaultValue = "plugins", description = "Destination plugins directory.")
        Path pluginsDir;

        @Override
        public Integer call() throws Exception {
            Path manifestPath = parent.resolve(PluginLockFiles.MANIFEST_FILE);
            Path lockfilePath = parent.resolve(PluginLockFiles.LOCK_FILE);
            if (Files.notExists(manifestPath) && Files.notExists(lockfilePath)) {
                throw new IllegalStateException("No server-lock.json or server-lock.lock.json found. Run `pl init` first.");
            }

            Path resolvedPluginsDir = parent.pluginsDir(pluginsDir);
            List<LockedPlugin> removedLockedPlugins;
            PluginLock lock = new PluginLock();
            if (Files.exists(lockfilePath)) {
                lock = PluginLockFiles.readLock(lockfilePath);
                removedLockedPlugins = lock.getPlugins().stream()
                        .filter(plugin -> matchesLockedPlugin(ids, plugin))
                        .toList();
                lock.getPlugins().removeIf(plugin -> matchesLockedPlugin(ids, plugin));
                PluginLockFiles.writeLock(lockfilePath, lock);
            } else {
                removedLockedPlugins = List.of();
            }

            List<Path> deleted = new ArrayList<>();
            for (LockedPlugin plugin : removedLockedPlugins) {
                Path jar = resolvedPluginsDir.resolve(plugin.getFileName());
                if (Files.deleteIfExists(jar)) {
                    deleted.add(jar);
                }
            }

            PluginManifest manifest = new PluginManifest();
            if (Files.exists(manifestPath)) {
                manifest = PluginLockFiles.readManifest(manifestPath);
                ensurePluginsList(manifest);
                manifest.getPlugins().removeIf(plugin -> matchesAny(ids, plugin.getId())
                        || removedLockedPlugins.stream().anyMatch(removed -> removed.getProvider().equalsIgnoreCase(plugin.getProvider())
                        && removed.getId().equalsIgnoreCase(plugin.getId())));
                parent.writeManifest(manifest);
            }

            String message = removedLockedPlugins.isEmpty()
                    ? "No locked plugins matched " + String.join(", ", ids)
                    : "Removed " + removedLockedPlugins.size() + " plugin(s)";
            parent.output().success("remove", message, Map.of(
                    "requested", ids,
                    "removed", removedLockedPlugins.stream().map(LockedPlugin::getId).toList(),
                    "deletedFiles", deleted.stream().map(path -> path.getFileName().toString()).toList(),
                    "manifestRemaining", manifest.getPlugins().size(),
                    "lockRemaining", lock.getPlugins().size()
            ));
            return 0;
        }
    }

    private static boolean matchesAny(List<String> ids, String value) {
        return value != null && ids.stream().anyMatch(id -> id.equalsIgnoreCase(value));
    }

    private static boolean matchesLockedPlugin(List<String> ids, LockedPlugin plugin) {
        return matchesAny(ids, plugin.getId())
                || matchesAny(ids, plugin.getName())
                || matchesAny(ids, plugin.getFileName())
                || matchesAny(ids, fileStem(plugin.getFileName()));
    }

    private static String fileStem(String fileName) {
        if (fileName == null) {
            return null;
        }
        return fileName.toLowerCase(Locale.ROOT).endsWith(".jar")
                ? fileName.substring(0, fileName.length() - ".jar".length())
                : fileName;
    }

    private record Output(boolean verbose, boolean json) {
        void success(String command, String message, Map<String, ?> details) {
            if (json) {
                writeJson(Map.of(
                        "status", "success",
                        "command", command,
                        "message", message,
                        "details", details
                ));
                return;
            }
            System.out.println(Ansi.green(message));
            if (verbose && details != null && !details.isEmpty()) {
                details.forEach((key, value) -> System.out.println(Ansi.dim(key + ": " + value)));
            }
        }

        void warning(String message) {
            if (json) {
                writeJson(Map.of(
                        "status", "warning",
                        "message", message
                ));
                return;
            }
            System.err.println(Ansi.yellow("Warning: ") + message);
        }

        static void error(boolean json, String message) {
            if (json) {
                writeJson(Map.of(
                        "status", "error",
                        "message", message
                ));
                return;
            }
            System.err.println(Ansi.red("Error: ") + message);
        }

        private static void writeJson(Map<String, ?> payload) {
            try {
                System.out.println(JSON.writeValueAsString(payload));
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to write JSON output", exception);
            }
        }
    }

    DownloadProgress downloadProgress() {
        return json ? DownloadProgress.NONE : new ProgressBar();
    }

    private static final class ProgressBar implements DownloadProgress {
        private static final int WIDTH = 28;
        private static final int MIN_FILE_NAME_LENGTH = 12;
        private static final int DEFAULT_TERMINAL_COLUMNS = 80;
        private String currentFile;
        private int lastPercent = -1;
        private int lastLineLength;
        private final int columns = terminalColumns();

        @Override
        public void update(String fileName, long downloadedBytes, long totalBytes) {
            boolean newFile = !fileName.equals(currentFile);
            int percent = totalBytes > 0 ? (int) Math.min(100, downloadedBytes * 100 / totalBytes) : -1;
            if (newFile && lastLineLength > 0) {
                System.out.println();
                lastLineLength = 0;
                lastPercent = -1;
            }
            if (!newFile && percent == lastPercent && downloadedBytes != totalBytes) {
                return;
            }
            currentFile = fileName;
            lastPercent = percent;

            String suffix = totalBytes > 0
                    ? " " + bar(percent) + " " + percent + "% " + formatBytes(downloadedBytes) + "/" + formatBytes(totalBytes)
                    : " " + formatBytes(downloadedBytes);
            String displayName = truncateFileName(fileName, Math.max(MIN_FILE_NAME_LENGTH, columns - suffix.length()));
            String line = displayName + suffix;
            int padding = Math.max(0, lastLineLength - line.length());
            System.out.print("\r\u001B[2K" + line + " ".repeat(padding));
            lastLineLength = line.length();
            if (totalBytes > 0 && downloadedBytes >= totalBytes) {
                System.out.println();
                lastLineLength = 0;
            }
        }

        private static String truncateFileName(String fileName, int maxLength) {
            if (fileName.length() <= maxLength) {
                return fileName;
            }
            int suffixLength = Math.max(1, maxLength - 3);
            return "..." + fileName.substring(fileName.length() - suffixLength);
        }

        private static String bar(int percent) {
            int filled = Math.min(WIDTH, Math.max(0, percent * WIDTH / 100));
            return "[" + "#".repeat(filled) + "-".repeat(WIDTH - filled) + "]";
        }

        private static String formatBytes(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            }
            double kib = bytes / 1024.0;
            if (kib < 1024) {
                return String.format(Locale.US, "%.1f KiB", kib);
            }
            return String.format(Locale.US, "%.1f MiB", kib / 1024.0);
        }

        private static int terminalColumns() {
            String columns = System.getenv("COLUMNS");
            if (columns != null) {
                try {
                    int parsed = Integer.parseInt(columns);
                    if (parsed > 0) {
                        return parsed;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return DEFAULT_TERMINAL_COLUMNS;
        }
    }

    private static final class Ansi {
        private static final String RESET = "\u001B[0m";
        private static final String BOLD = "\u001B[1m";
        private static final String DIM = "\u001B[2m";
        private static final String RED = "\u001B[31m";
        private static final String GREEN = "\u001B[32m";
        private static final String YELLOW = "\u001B[33m";
        private static final String BLUE = "\u001B[34m";

        private Ansi() {
        }

        static String bold(String text) {
            return color(BOLD, text);
        }

        static String dim(String text) {
            return color(DIM, text);
        }

        static String red(String text) {
            return color(RED, text);
        }

        static String green(String text) {
            return color(GREEN, text);
        }

        static String yellow(String text) {
            return color(YELLOW, text);
        }

        static String blue(String text) {
            return color(BLUE, text);
        }

        private static String color(String code, String text) {
            if (!enabled()) {
                return text;
            }
            return code + text + RESET;
        }

        private static boolean enabled() {
            return System.console() != null && System.getenv("NO_COLOR") == null;
        }
    }

    private static void addOrReplace(PluginManifest manifest, String id, String provider, String version) {
        addOrReplace(manifest, new PluginRequest(id, provider, version));
    }

    private static void addOrReplace(PluginManifest manifest, PluginRequest request) {
        manifest.getPlugins().removeIf(plugin -> request.getId().equals(plugin.getId())
                && request.getProvider().equals(plugin.getProvider()));
        manifest.getPlugins().add(request);
    }

    private static final class FriendlyExecutionExceptionHandler implements CommandLine.IExecutionExceptionHandler {
        @Override
        public int handleExecutionException(Exception exception, CommandLine commandLine, ParseResult parseResult) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            boolean json = rootCli(commandLine).json;
            Output.error(json, friendlyMessage(exception));
            return commandLine.getCommandSpec().exitCodeOnExecutionException();
        }

        private static PluginLockCli rootCli(CommandLine commandLine) {
            CommandLine current = commandLine;
            while (current.getParent() != null) {
                current = current.getParent();
            }
            return (PluginLockCli) current.getCommandSpec().userObject();
        }

        private static String friendlyMessage(Exception exception) {
            if (exception instanceof PluginNotFoundException
                    || exception instanceof IllegalArgumentException
                    || exception instanceof IllegalStateException
                    || exception instanceof IOException) {
                return messageOrFallback(exception);
            }
            if (exception instanceof InterruptedException) {
                return "Operation interrupted";
            }
            return messageOrFallback(exception);
        }

        private static String messageOrFallback(Exception exception) {
            String message = exception.getMessage();
            return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
        }
    }
}
