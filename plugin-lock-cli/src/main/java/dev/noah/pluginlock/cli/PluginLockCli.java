package dev.noah.pluginlock.cli;

import dev.noah.pluginlock.core.PluginInstaller;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.PluginResolver;
import dev.noah.pluginlock.core.model.LockedPlugin;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    @Option(names = "--project-dir", defaultValue = ".", description = "Directory containing plugin-lock files.")
    Path projectDir;
    private Path effectiveProjectDir;
    private final ModrinthProvider modrinthProvider = new ModrinthProvider(HttpClient.newHttpClient());
    private final HangarProvider hangarProvider = new HangarProvider(HttpClient.newHttpClient());

    public static void main(String[] args) {
        int exitCode = commandLine(new PluginLockCli()).execute(args);
        System.exit(exitCode);
    }

    static CommandLine commandLine(PluginLockCli cli) {
        return new CommandLine(cli)
                .setExecutionExceptionHandler(new FriendlyExecutionExceptionHandler());
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

    PluginLock resolveAndWriteLock(PluginManifest manifest) throws Exception {
        PluginLock lock = new PluginResolver().resolve(manifest);
        PluginLockFiles.writeLock(resolve(PluginLockFiles.LOCK_FILE), lock);
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
        System.out.println("Found " + matches.size() + " provider match(es) for " + id + ":");
        for (int index = 0; index < matches.size(); index++) {
            PluginMetadata metadata = matches.get(index);
            String defaultLabel = index == 0 ? " (default)" : "";
            System.out.println((index + 1) + ". " + metadata.getProvider() + ":" + metadata.getId()
                    + " - " + metadata.getName() + defaultLabel);
            if (metadata.getDescription() != null && !metadata.getDescription().isBlank()) {
                System.out.println("   " + metadata.getDescription());
            }
            System.out.println("   Downloads: " + String.format(Locale.US, "%,d", metadata.getDownloads()));
        }
        System.out.println();
    }

    private static PluginMetadata confirmOrSelectProvider(List<PluginMetadata> matches) {
        PluginMetadata selected = matches.get(0);
        if (confirm("Install " + selected.getProvider() + ":" + selected.getId() + "? [Y/n] ")) {
            return selected;
        }
        selected = selectProvider(matches);
        printMetadata(selected);
        if (confirm("Install this plugin? [Y/n] ")) {
            return selected;
        }
        return null;
    }

    private static PluginMetadata selectProvider(List<PluginMetadata> matches) {
        String answer = readConfirmation("Select provider [1]: ");
        if (answer.isBlank()) {
            return matches.get(0);
        }
        try {
            int selected = Integer.parseInt(answer);
            if (selected >= 1 && selected <= matches.size()) {
                return matches.get(selected - 1);
            }
        } catch (NumberFormatException ignored) {
        }
        System.out.println("Invalid selection; using " + matches.get(0).getProvider() + ":" + matches.get(0).getId());
        return matches.get(0);
    }

    private static boolean confirm(String prompt) {
        String answer = readConfirmation(prompt);
        return answer.isBlank() || "y".equalsIgnoreCase(answer) || "yes".equalsIgnoreCase(answer);
    }

    static void printMetadata(PluginMetadata metadata) {
        System.out.println();
        System.out.println(metadata.getName() + " (" + metadata.getProvider() + ":" + metadata.getId() + ")");
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
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String answer = reader.readLine();
            return answer == null ? "" : answer.trim();
        } catch (Exception exception) {
            return "";
        }
    }

    @Command(name = "init", mixinStandardHelpOptions = true, description = "Create a plugin-lock.json manifest.")
    static final class InitCommand implements Callable<Integer> {
        @ParentCommand
        PluginLockCli parent;

        @Option(names = "--minecraft", defaultValue = "1.21.4", description = "Minecraft version.")
        String minecraftVersion;

        @Option(names = "--loader", defaultValue = "paper", description = "Server loader, for example paper.")
        String loader;

        @Override
        public Integer call() throws Exception {
            Path manifestPath = parent.resolve(PluginLockFiles.MANIFEST_FILE);
            if (Files.exists(manifestPath)) {
                throw new IllegalStateException(PluginLockFiles.MANIFEST_FILE + " already exists");
            }
            PluginManifest manifest = new PluginManifest();
            manifest.setMinecraftVersion(minecraftVersion);
            manifest.setLoader(loader);
            PluginLockFiles.writeManifest(manifestPath, manifest);
            System.out.println("Created " + PluginLockFiles.MANIFEST_FILE);
            return 0;
        }
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
            System.out.println("Added " + request.getProvider() + ":" + request.getId() + "@" + request.getVersion());
            return 0;
        }
    }

    @Command(name = "lock", hidden = true, mixinStandardHelpOptions = true, description = "Resolve plugin-lock.json into plugin-lock.lock.json.")
    static final class LockCommand implements Callable<Integer> {
        @ParentCommand
        PluginLockCli parent;

        @Override
        public Integer call() throws Exception {
            PluginManifest manifest = PluginLockFiles.readManifest(parent.resolve(PluginLockFiles.MANIFEST_FILE));
            PluginLock lock = parent.resolveAndWriteLock(manifest);
            System.out.println("Locked " + lock.getPlugins().size() + " plugin(s)");
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
                parent.writeManifest(manifest);
                lock = parent.resolveAndWriteLock(manifest);
            } else if (Files.exists(lockfilePath)) {
                lock = PluginLockFiles.readLock(lockfilePath);
            } else {
                throw new IllegalStateException("No plugin-lock.json or plugin-lock.lock.json found. Run `pl init` first.");
            }

            new PluginInstaller().install(lock, resolvedPluginsDir);
            System.out.println("Installed " + lock.getPlugins().size() + " plugin(s) into " + resolvedPluginsDir);
            return 0;
        }
    }

    @Command(name = "clean-install", aliases = "ci", mixinStandardHelpOptions = true, description = "Install exactly from plugin-lock.lock.json without resolving new versions.")
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
            new PluginInstaller().install(lock, resolvedPluginsDir);
            System.out.println("Clean installed " + lock.getPlugins().size() + " plugin(s) into " + resolvedPluginsDir);
            return 0;
        }
    }

    @Command(name = "remove", aliases = {"rm", "uninstall"}, mixinStandardHelpOptions = true, description = "Remove plugins recorded in plugin-lock files.")
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
                throw new IllegalStateException("No plugin-lock.json or plugin-lock.lock.json found. Run `pl init` first.");
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

            if (removedLockedPlugins.isEmpty()) {
                System.out.println("No locked plugins matched " + String.join(", ", ids));
            } else {
                System.out.println("Removed " + removedLockedPlugins.size() + " locked plugin(s): "
                        + String.join(", ", removedLockedPlugins.stream().map(LockedPlugin::getId).toList()));
            }
            if (!deleted.isEmpty()) {
                System.out.println("Deleted " + deleted.size() + " installed jar(s): "
                        + String.join(", ", deleted.stream().map(path -> path.getFileName().toString()).toList()));
            }
            System.out.println("Cleaned plugin-lock files; " + manifest.getPlugins().size()
                    + " manifest entry(s), " + lock.getPlugins().size() + " lock entry(s) remain");
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

            commandLine.getErr().println(commandLine.getColorScheme().errorText("Error: ") + friendlyMessage(exception));
            return commandLine.getCommandSpec().exitCodeOnExecutionException();
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
