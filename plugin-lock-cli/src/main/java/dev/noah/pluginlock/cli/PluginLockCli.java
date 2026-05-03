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
                PluginLockCli.RemoveCommand.class,
                PluginLockCli.ListCommand.class,
                PluginLockCli.DoctorCommand.class,
                PluginLockCli.UpdateCommand.class,
                PluginLockCli.SearchCommand.class
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

    LockedServer initializeProject(String server, String minecraftVersion, boolean yes) throws Exception {
        Path manifestPath = resolve(PluginLockFiles.MANIFEST_FILE);
        if (Files.exists(manifestPath)) {
            throw new IllegalStateException(PluginLockFiles.MANIFEST_FILE + " already exists");
        }
        String resolvedServer = chooseServer(server, DEFAULT_LOADER, yes);
        List<String> versions = serverDownloads.versions(resolvedServer);
        String resolvedMinecraftVersion = chooseMinecraftVersion(minecraftVersion, versions, yes);
        LockedServer lockedServer = serverDownloads.latest(resolvedServer, resolvedMinecraftVersion);
        serverDownloads.download(lockedServer, effectiveProjectDir(), downloadProgress());

        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion(resolvedMinecraftVersion);
        manifest.setLoader(ServerDownloads.pluginLoaderFor(resolvedServer));
        PluginLockFiles.writeManifest(manifestPath, manifest);

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion(manifest.getMinecraftVersion());
        lock.setLoader(manifest.getLoader());
        lock.setServer(lockedServer);
        PluginLockFiles.writeLock(resolve(PluginLockFiles.LOCK_FILE), lock);
        return lockedServer;
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

    List<PluginMetadata> searchPlugins(String provider, String query, int limit) throws Exception {
        List<PluginMetadata> results = new ArrayList<>();
        if ("auto".equalsIgnoreCase(provider) || "modrinth".equalsIgnoreCase(provider)) {
            results.addAll(modrinthProvider.search(query, limit));
        }
        if ("auto".equalsIgnoreCase(provider) || "hangar".equalsIgnoreCase(provider)) {
            results.addAll(hangarProvider.search(query, limit));
        }
        if (!"auto".equalsIgnoreCase(provider)
                && !"modrinth".equalsIgnoreCase(provider)
                && !"hangar".equalsIgnoreCase(provider)) {
            throw new IllegalArgumentException("Unsupported provider: " + provider);
        }
        return results.stream()
                .sorted((left, right) -> Long.compare(right.getDownloads(), left.getDownloads()))
                .limit(limit)
                .toList();
    }

    PluginRequest selectPlugin(String provider, String id, String version, boolean assumeYes) throws Exception {
        PluginSpec spec = PluginSpec.parse(id, provider, version);
        provider = spec.provider();
        id = spec.id();
        version = spec.version();
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

    private record PluginSpec(String provider, String id, String version) {
        static PluginSpec parse(String raw, String provider, String version) {
            String parsedProvider = provider;
            String parsedId = raw;
            String parsedVersion = version;
            int providerSeparator = raw.indexOf(':');
            if (providerSeparator > 0) {
                parsedProvider = raw.substring(0, providerSeparator);
                parsedId = raw.substring(providerSeparator + 1);
            }
            int versionSeparator = parsedId.lastIndexOf('@');
            if (versionSeparator > 0 && versionSeparator < parsedId.length() - 1) {
                parsedVersion = parsedId.substring(versionSeparator + 1);
                parsedId = parsedId.substring(0, versionSeparator);
            }
            if (parsedId.isBlank()) {
                throw new IllegalArgumentException("Plugin id cannot be blank");
            }
            return new PluginSpec(parsedProvider, parsedId, parsedVersion);
        }
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
            LockedServer lockedServer = parent.initializeProject(server, minecraftVersion, yes);
            PluginManifest manifest = PluginLockFiles.readManifest(manifestPath);

            parent.output().success("init", "Initialized " + manifest.getMinecraftVersion() + " " + lockedServer.getProvider(), Map.of(
                    "manifest", PluginLockFiles.MANIFEST_FILE,
                    "lockfile", PluginLockFiles.LOCK_FILE,
                    "server", lockedServer.getProvider(),
                    "minecraftVersion", manifest.getMinecraftVersion(),
                    "loader", manifest.getLoader(),
                    "build", lockedServer.getBuild(),
                    "serverJar", lockedServer.getFileName(),
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

        @Option(names = "--minecraft", description = "Minecraft version for auto-init when no project files exist.")
        String minecraftVersion;

        @Option(names = "--server", description = "Server provider for auto-init when no project files exist: paper or purpur.")
        String server;

        @Option(names = {"-y", "--yes"}, description = "Skip plugin metadata confirmation for new plugins.")
        boolean yes;

        @Override
        public Integer call() throws Exception {
            Path resolvedPluginsDir = parent.pluginsDir(pluginsDir);
            PluginLock lock;
            Path manifestPath = parent.resolve(PluginLockFiles.MANIFEST_FILE);
            Path lockfilePath = parent.resolve(PluginLockFiles.LOCK_FILE);

            if (!ids.isEmpty() && Files.notExists(manifestPath) && Files.notExists(lockfilePath)) {
                parent.initializeProject(server, minecraftVersion, yes);
            }

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

    @Command(name = "list", aliases = "ls", mixinStandardHelpOptions = true, description = "List locked plugins.")
    static final class ListCommand implements Callable<Integer> {
        @ParentCommand
        PluginLockCli parent;

        @Override
        public Integer call() throws Exception {
            Path lockfilePath = parent.resolve(PluginLockFiles.LOCK_FILE);
            if (Files.notExists(lockfilePath)) {
                throw new IllegalStateException("No server-lock.lock.json found. Run `pl install` or `pl lock` first.");
            }

            PluginLock lock = PluginLockFiles.readLock(lockfilePath);
            parent.output().list(lock);
            return 0;
        }
    }

    @Command(name = "doctor", mixinStandardHelpOptions = true, description = "Check project files, locked artifacts, and hashes.")
    static final class DoctorCommand implements Callable<Integer> {
        @ParentCommand
        PluginLockCli parent;

        @Option(names = "--plugins-dir", defaultValue = "plugins", description = "Destination plugins directory.")
        Path pluginsDir;

        @Override
        public Integer call() throws Exception {
            Path manifestPath = parent.resolve(PluginLockFiles.MANIFEST_FILE);
            Path lockfilePath = parent.resolve(PluginLockFiles.LOCK_FILE);
            Path resolvedPluginsDir = parent.pluginsDir(pluginsDir);
            List<DoctorCheck> checks = new ArrayList<>();

            PluginManifest manifest = null;
            PluginLock lock = null;
            if (Files.exists(manifestPath)) {
                manifest = PluginLockFiles.readManifest(manifestPath);
                checks.add(DoctorCheck.ok("manifest", PluginLockFiles.MANIFEST_FILE + " exists"));
            } else {
                checks.add(DoctorCheck.warning("manifest", PluginLockFiles.MANIFEST_FILE + " is missing"));
            }
            if (Files.exists(lockfilePath)) {
                lock = PluginLockFiles.readLock(lockfilePath);
                checks.add(DoctorCheck.ok("lockfile", PluginLockFiles.LOCK_FILE + " exists"));
            } else {
                checks.add(DoctorCheck.error("lockfile", PluginLockFiles.LOCK_FILE + " is missing"));
            }

            if (manifest != null && lock != null) {
                compareProjectMetadata(manifest, lock, checks);
                checkManifestLockCoverage(manifest, lock, checks);
            }
            if (lock != null) {
                checkServerJar(parent.effectiveProjectDir(), lock, checks);
                checkPluginJars(resolvedPluginsDir, lock, checks);
                lock.getPlugins().stream()
                        .map(LockedPlugin::getCompatibilityWarning)
                        .filter(warning -> warning != null && !warning.isBlank())
                        .forEach(warning -> checks.add(DoctorCheck.warning("compatibility", warning)));
            }

            parent.output().doctor(checks);
            return checks.stream().anyMatch(check -> "error".equals(check.status())) ? 1 : 0;
        }

        private static void compareProjectMetadata(PluginManifest manifest, PluginLock lock, List<DoctorCheck> checks) {
            if (same(manifest.getMinecraftVersion(), lock.getMinecraftVersion())) {
                checks.add(DoctorCheck.ok("minecraft", "Manifest and lockfile Minecraft versions match"));
            } else {
                checks.add(DoctorCheck.error("minecraft", "Manifest Minecraft version " + blank(manifest.getMinecraftVersion())
                        + " does not match lockfile " + blank(lock.getMinecraftVersion())));
            }
            if (same(manifest.getLoader(), lock.getLoader())) {
                checks.add(DoctorCheck.ok("loader", "Manifest and lockfile loaders match"));
            } else {
                checks.add(DoctorCheck.error("loader", "Manifest loader " + blank(manifest.getLoader())
                        + " does not match lockfile " + blank(lock.getLoader())));
            }
        }

        private static void checkManifestLockCoverage(PluginManifest manifest, PluginLock lock, List<DoctorCheck> checks) {
            List<String> missing = manifest.getPlugins().stream()
                    .filter(request -> lock.getPlugins().stream().noneMatch(plugin -> same(request.getProvider(), plugin.getProvider())
                            && same(request.getId(), plugin.getId())))
                    .map(request -> request.getProvider() + ":" + request.getId())
                    .toList();
            if (missing.isEmpty()) {
                checks.add(DoctorCheck.ok("plugins", "Lockfile covers all manifest plugins"));
            } else {
                checks.add(DoctorCheck.error("plugins", "Lockfile is missing manifest plugin(s): " + String.join(", ", missing)));
            }
        }

        private static void checkServerJar(Path projectDir, PluginLock lock, List<DoctorCheck> checks) throws Exception {
            LockedServer server = lock.getServer();
            if (server == null || server.getFileName() == null || server.getFileName().isBlank()) {
                checks.add(DoctorCheck.warning("server", "No locked server jar recorded"));
                return;
            }
            Path jar = projectDir.resolve(server.getFileName());
            if (Files.notExists(jar)) {
                checks.add(DoctorCheck.error("server", "Missing server jar " + server.getFileName()));
                return;
            }
            if (server.getSha256() == null || server.getSha256().isBlank()) {
                checks.add(DoctorCheck.warning("server", "Server jar exists but no SHA-256 is recorded"));
                return;
            }
            String actual = sha256(jar);
            if (server.getSha256().equalsIgnoreCase(actual)) {
                checks.add(DoctorCheck.ok("server", "Server jar hash matches"));
            } else {
                checks.add(DoctorCheck.error("server", "Server jar hash mismatch for " + server.getFileName()));
            }
        }

        private static void checkPluginJars(Path pluginsDir, PluginLock lock, List<DoctorCheck> checks) throws Exception {
            if (lock.getPlugins().isEmpty()) {
                checks.add(DoctorCheck.ok("plugins", "No locked plugins"));
                return;
            }
            for (LockedPlugin plugin : lock.getPlugins()) {
                Path jar = pluginsDir.resolve(plugin.getFileName());
                String label = plugin.getProvider() + ":" + plugin.getId();
                if (Files.notExists(jar)) {
                    checks.add(DoctorCheck.error("plugin", "Missing plugin jar " + plugin.getFileName() + " for " + label));
                    continue;
                }
                if (plugin.getSha512() != null && !plugin.getSha512().isBlank()) {
                    String actual = PluginInstaller.sha512(jar);
                    checks.add(plugin.getSha512().equalsIgnoreCase(actual)
                            ? DoctorCheck.ok("plugin", label + " hash matches")
                            : DoctorCheck.error("plugin", label + " SHA-512 mismatch"));
                } else if (plugin.getSha256() != null && !plugin.getSha256().isBlank()) {
                    String actual = sha256(jar);
                    checks.add(plugin.getSha256().equalsIgnoreCase(actual)
                            ? DoctorCheck.ok("plugin", label + " hash matches")
                            : DoctorCheck.error("plugin", label + " SHA-256 mismatch"));
                } else {
                    checks.add(DoctorCheck.warning("plugin", label + " has no supported recorded hash"));
                }
            }
        }

        private static String sha256(Path path) throws Exception {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var input = new java.security.DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        }

        private static boolean same(String left, String right) {
            return blank(left).equalsIgnoreCase(blank(right));
        }

        private static String blank(String value) {
            return value == null ? "" : value;
        }
    }

    private record DoctorCheck(String status, String check, String message) {
        static DoctorCheck ok(String check, String message) {
            return new DoctorCheck("ok", check, message);
        }

        static DoctorCheck warning(String check, String message) {
            return new DoctorCheck("warning", check, message);
        }

        static DoctorCheck error(String check, String message) {
            return new DoctorCheck("error", check, message);
        }
    }

    @Command(name = "update", mixinStandardHelpOptions = true, description = "Update locked plugin versions from the manifest.")
    static final class UpdateCommand implements Callable<Integer> {
        @ParentCommand
        PluginLockCli parent;

        @Parameters(arity = "0..*", description = "Optional plugin ids, names, or files to update.")
        List<String> ids = List.of();

        @Option(names = "--server", description = "Update the locked server build and download the server jar.")
        boolean server;

        @Override
        public Integer call() throws Exception {
            Path manifestPath = parent.resolve(PluginLockFiles.MANIFEST_FILE);
            if (Files.notExists(manifestPath)) {
                throw new IllegalStateException("No server-lock.json found. Run `pl init` first.");
            }
            PluginManifest manifest = PluginLockFiles.readManifest(manifestPath);
            PluginLock existing = Files.exists(parent.resolve(PluginLockFiles.LOCK_FILE))
                    ? PluginLockFiles.readLock(parent.resolve(PluginLockFiles.LOCK_FILE))
                    : null;
            PluginLock resolved = parent.resolveLock(manifest);
            PluginLock updated = ids.isEmpty() || existing == null
                    ? resolved
                    : mergeSelectedUpdates(existing, resolved, ids);
            if (server) {
                LockedServer lockedServer = latestServer(parent, manifest, existing);
                parent.serverDownloads.download(lockedServer, parent.effectiveProjectDir(), parent.downloadProgress());
                updated.setServer(lockedServer);
            }
            parent.writeLock(updated);
            parent.output().success("update", updateMessage(ids, server, updated), Map.of(
                    "count", updated.getPlugins().size(),
                    "updated", ids,
                    "server", server,
                    "plugins", updated.getPlugins().stream().map(LockedPlugin::getId).toList()
            ));
            return 0;
        }

        private static LockedServer latestServer(PluginLockCli parent, PluginManifest manifest, PluginLock existing)
                throws Exception {
            String provider = existing != null && existing.getServer() != null
                    ? existing.getServer().getProvider()
                    : manifest.getLoader();
            if (provider == null || provider.isBlank()) {
                provider = DEFAULT_LOADER;
            }
            return parent.serverDownloads.latest(provider, manifest.getMinecraftVersion());
        }

        private static PluginLock mergeSelectedUpdates(PluginLock existing, PluginLock resolved, List<String> ids) {
            Map<String, LockedPlugin> resolvedByKey = new LinkedHashMap<>();
            for (LockedPlugin plugin : resolved.getPlugins()) {
                resolvedByKey.put(pluginKey(plugin), plugin);
            }
            List<LockedPlugin> mergedPlugins = new ArrayList<>();
            for (LockedPlugin plugin : existing.getPlugins()) {
                LockedPlugin replacement = resolvedByKey.get(pluginKey(plugin));
                mergedPlugins.add(replacement != null && matchesLockedPlugin(ids, plugin) ? replacement : plugin);
            }
            for (LockedPlugin plugin : resolved.getPlugins()) {
                boolean alreadyPresent = mergedPlugins.stream().anyMatch(existingPlugin -> pluginKey(existingPlugin).equals(pluginKey(plugin)));
                if (!alreadyPresent && matchesLockedPlugin(ids, plugin)) {
                    mergedPlugins.add(plugin);
                }
            }
            existing.setMinecraftVersion(resolved.getMinecraftVersion());
            existing.setLoader(resolved.getLoader());
            existing.setPlugins(mergedPlugins);
            return existing;
        }

        private static String pluginKey(LockedPlugin plugin) {
            return blank(plugin.getProvider()).toLowerCase(Locale.ROOT) + ":" + blank(plugin.getId()).toLowerCase(Locale.ROOT);
        }

        private static String updateMessage(List<String> ids, boolean server, PluginLock lock) {
            if (server && ids.isEmpty()) {
                return "Updated server and " + lock.getPlugins().size() + " plugin(s)";
            }
            if (server) {
                return "Updated server and " + ids.size() + " requested plugin(s)";
            }
            return ids.isEmpty()
                    ? "Updated " + lock.getPlugins().size() + " plugin(s)"
                    : "Updated " + ids.size() + " requested plugin(s)";
        }

        private static String blank(String value) {
            return value == null ? "" : value;
        }
    }

    @Command(name = "search", mixinStandardHelpOptions = true, description = "Search plugin providers.")
    static final class SearchCommand implements Callable<Integer> {
        @ParentCommand
        PluginLockCli parent;

        @Parameters(index = "0", description = "Search query.")
        String query;

        @Option(names = "--provider", defaultValue = "auto", description = "Plugin provider: auto, modrinth, or hangar.")
        String provider;

        @Option(names = "--limit", defaultValue = "10", description = "Maximum results to show.")
        int limit;

        @Override
        public Integer call() throws Exception {
            List<PluginMetadata> results = parent.searchPlugins(provider, query, Math.max(1, limit));
            parent.output().search(query, results);
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

        void list(PluginLock lock) {
            if (json) {
                writeJson(Map.of(
                        "status", "success",
                        "command", "list",
                        "minecraftVersion", nullToBlank(lock.getMinecraftVersion()),
                        "loader", nullToBlank(lock.getLoader()),
                        "server", serverDetails(lock),
                        "plugins", lock.getPlugins().stream()
                                .map(Output::pluginDetails)
                                .toList()
                ));
                return;
            }

            System.out.println(Ansi.bold("Minecraft: ") + nullToBlank(lock.getMinecraftVersion())
                    + "  " + Ansi.bold("Loader: ") + nullToBlank(lock.getLoader()));
            if (lock.getServer() != null) {
                System.out.println(Ansi.bold("Server: ") + nullToBlank(lock.getServer().getProvider())
                        + " " + nullToBlank(lock.getServer().getMinecraftVersion())
                        + " build " + nullToBlank(lock.getServer().getBuild())
                        + " (" + nullToBlank(lock.getServer().getFileName()) + ")");
            }
            if (lock.getPlugins().isEmpty()) {
                System.out.println("No locked plugins.");
                return;
            }
            System.out.println();
            for (LockedPlugin plugin : lock.getPlugins()) {
                System.out.println(Ansi.blue(plugin.getProvider() + ":" + plugin.getId())
                        + "  " + nullToBlank(plugin.getName())
                        + "  " + nullToBlank(plugin.getVersionName())
                        + "  " + Ansi.dim(nullToBlank(plugin.getFileName())));
                if (plugin.getCompatibilityWarning() != null && !plugin.getCompatibilityWarning().isBlank()) {
                    System.out.println(Ansi.yellow("  Warning: ") + plugin.getCompatibilityWarning());
                }
            }
        }

        void doctor(List<DoctorCheck> checks) {
            if (json) {
                writeJson(Map.of(
                        "status", checks.stream().anyMatch(check -> "error".equals(check.status())) ? "error" : "success",
                        "command", "doctor",
                        "checks", checks.stream()
                                .map(check -> Map.of(
                                        "status", check.status(),
                                        "check", check.check(),
                                        "message", check.message()))
                                .toList()
                ));
                return;
            }
            for (DoctorCheck check : checks) {
                String marker = switch (check.status()) {
                    case "ok" -> Ansi.green("OK");
                    case "warning" -> Ansi.yellow("WARN");
                    default -> Ansi.red("FAIL");
                };
                System.out.println(marker + " " + check.message());
            }
        }

        void search(String query, List<PluginMetadata> results) {
            if (json) {
                writeJson(Map.of(
                        "status", "success",
                        "command", "search",
                        "query", query,
                        "results", results.stream()
                                .map(metadata -> Map.of(
                                        "provider", metadata.getProvider(),
                                        "id", metadata.getId(),
                                        "name", metadata.getName(),
                                        "description", metadata.getDescription() == null ? "" : metadata.getDescription(),
                                        "downloads", metadata.getDownloads()))
                                .toList()
                ));
                return;
            }
            if (results.isEmpty()) {
                System.out.println("No plugin matches found for " + query);
                return;
            }
            for (PluginMetadata metadata : results) {
                System.out.println(Ansi.blue(metadata.getProvider() + ":" + metadata.getId())
                        + "  " + metadata.getName()
                        + "  " + Ansi.dim(String.format(Locale.US, "%,d downloads", metadata.getDownloads())));
                if (metadata.getDescription() != null && !metadata.getDescription().isBlank()) {
                    System.out.println(Ansi.dim("  " + metadata.getDescription()));
                }
            }
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
