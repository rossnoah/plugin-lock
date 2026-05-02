package dev.noah.pluginlock.cli;

import dev.noah.pluginlock.core.PluginInstaller;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.PluginResolver;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginMetadata;
import dev.noah.pluginlock.core.model.PluginRequest;
import dev.noah.pluginlock.core.provider.ModrinthProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Parameters;

import java.io.Console;
import java.io.BufferedReader;
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

    public static void main(String[] args) {
        int exitCode = new CommandLine(new PluginLockCli()).execute(args);
        System.exit(exitCode);
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
        if ("plugins".equalsIgnoreCase(current.getFileName().toString()) && current.getParent() != null) {
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
        if (!"modrinth".equalsIgnoreCase(provider)) {
            throw new IllegalArgumentException("Unsupported provider: " + provider);
        }
        return modrinthProvider.fetchMetadata(id);
    }

    boolean confirmPlugin(String provider, String id, boolean assumeYes) throws Exception {
        if (assumeYes) {
            return true;
        }
        PluginMetadata metadata = fetchMetadata(provider, id);
        printMetadata(metadata);
        String answer = readConfirmation("Install this plugin? [y/N] ");
        return "y".equalsIgnoreCase(answer) || "yes".equalsIgnoreCase(answer);
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
            if (!reader.ready()) {
                return "";
            }
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

        @Option(names = "--provider", defaultValue = "modrinth", description = "Plugin provider.")
        String provider;

        @Option(names = "--version", defaultValue = "latest", description = "Version id, version number, or latest.")
        String version;

        @Option(names = {"-y", "--yes"}, description = "Skip plugin metadata confirmation.")
        boolean yes;

        @Override
        public Integer call() throws Exception {
            PluginManifest manifest = parent.readManifestOrDefault();
            ensurePluginsList(manifest);
            if (!parent.confirmPlugin(provider, id, yes)) {
                System.out.println("Cancelled");
                return 1;
            }
            addOrReplace(manifest, id, provider, version);
            parent.writeManifest(manifest);
            System.out.println("Added " + provider + ":" + id + "@" + version);
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

        @Option(names = "--provider", defaultValue = "modrinth", description = "Plugin provider for new package arguments.")
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
                    if (!parent.confirmPlugin(provider, id, yes)) {
                        System.out.println("Cancelled");
                        return 1;
                    }
                    addOrReplace(manifest, id, provider, version);
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

    @Command(name = "remove", aliases = {"rm", "uninstall"}, mixinStandardHelpOptions = true, description = "Remove plugins from the manifest and lockfile.")
    static final class RemoveCommand implements Callable<Integer> {
        @ParentCommand
        PluginLockCli parent;

        @Parameters(arity = "1..*", description = "Plugin ids to remove.")
        List<String> ids;

        @Option(names = "--plugins-dir", defaultValue = "plugins", description = "Destination plugins directory.")
        Path pluginsDir;

        @Override
        public Integer call() throws Exception {
            PluginManifest manifest = PluginLockFiles.readManifest(parent.resolve(PluginLockFiles.MANIFEST_FILE));
            ensurePluginsList(manifest);
            Path lockfilePath = parent.resolve(PluginLockFiles.LOCK_FILE);
            PluginLock oldLock = Files.exists(lockfilePath) ? PluginLockFiles.readLock(lockfilePath) : new PluginLock();

            manifest.getPlugins().removeIf(plugin -> ids.contains(plugin.getId()));
            parent.writeManifest(manifest);

            PluginLock newLock = new PluginLock();
            newLock.setMinecraftVersion(manifest.getMinecraftVersion());
            newLock.setLoader(manifest.getLoader());
            if (!manifest.getPlugins().isEmpty()) {
                newLock = parent.resolveAndWriteLock(manifest);
            } else {
                PluginLockFiles.writeLock(lockfilePath, newLock);
            }

            Path resolvedPluginsDir = parent.pluginsDir(pluginsDir);
            for (dev.noah.pluginlock.core.model.LockedPlugin plugin : oldLock.getPlugins()) {
                if (ids.contains(plugin.getId())) {
                    Files.deleteIfExists(resolvedPluginsDir.resolve(plugin.getFileName()));
                }
            }

            System.out.println("Removed " + ids.size() + " plugin(s); " + newLock.getPlugins().size() + " remain locked");
            return 0;
        }
    }

    private static void addOrReplace(PluginManifest manifest, String id, String provider, String version) {
        manifest.getPlugins().removeIf(plugin -> id.equals(plugin.getId()) && provider.equals(plugin.getProvider()));
        manifest.getPlugins().add(new PluginRequest(id, provider, version));
    }
}
