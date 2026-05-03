package dev.noah.pluginlock.core.workflow;

import dev.noah.pluginlock.core.DownloadProgress;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.PluginResolver;
import dev.noah.pluginlock.core.model.LockedServer;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.project.ProjectPaths;
import dev.noah.pluginlock.core.server.ServerDownloads;

import java.nio.file.Files;

public final class ProjectService {
    private final ProjectPaths paths;
    private final ServerDownloads serverDownloads;
    private final LockResolver lockResolver;

    public ProjectService(ProjectPaths paths, ServerDownloads serverDownloads) {
        this(paths, serverDownloads, new PluginResolver()::resolve);
    }

    public ProjectService(ProjectPaths paths, ServerDownloads serverDownloads, LockResolver lockResolver) {
        this.paths = paths;
        this.serverDownloads = serverDownloads;
        this.lockResolver = lockResolver;
    }

    public ProjectPaths paths() {
        return paths;
    }

    public ServerDownloads serverDownloads() {
        return serverDownloads;
    }

    public PluginManifest readManifestOrDefault() throws Exception {
        return Files.exists(paths.manifestPath())
                ? PluginLockFiles.readManifest(paths.manifestPath())
                : new PluginManifest();
    }

    public PluginLock readLock() throws Exception {
        return PluginLockFiles.readLock(paths.lockPath());
    }

    public void writeManifest(PluginManifest manifest) throws Exception {
        PluginLockFiles.writeManifest(paths.manifestPath(), manifest);
    }

    public void writeLock(PluginLock lock) throws Exception {
        PluginLockFiles.writeLock(paths.lockPath(), lock);
    }

    public PluginLock resolveLock(PluginManifest manifest) throws Exception {
        PluginLock lock = lockResolver.resolve(manifest);
        if (Files.exists(paths.lockPath())) {
            lock.setServer(PluginLockFiles.readLock(paths.lockPath()).getServer());
        }
        return lock;
    }

    public PluginLock resolveAndWriteLock(PluginManifest manifest) throws Exception {
        PluginLock lock = resolveLock(manifest);
        writeLock(lock);
        return lock;
    }

    public InitializeResult initializeProject(String server, String minecraftVersion, DownloadProgress progress) throws Exception {
        if (Files.exists(paths.manifestPath())) {
            throw new IllegalStateException(PluginLockFiles.MANIFEST_FILE + " already exists");
        }
        LockedServer lockedServer = serverDownloads.latest(server, minecraftVersion);
        ServerDownloads.DownloadResult download = serverDownloads.download(lockedServer, paths.root(), progress);

        PluginManifest manifest = new PluginManifest();
        manifest.setMinecraftVersion(minecraftVersion);
        manifest.setLoader(ServerDownloads.pluginLoaderFor(server));
        PluginLockFiles.writeManifest(paths.manifestPath(), manifest);

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion(manifest.getMinecraftVersion());
        lock.setLoader(manifest.getLoader());
        lock.setServer(lockedServer);
        PluginLockFiles.writeLock(paths.lockPath(), lock);

        return new InitializeResult(manifest, lock, lockedServer, download);
    }

    @FunctionalInterface
    public interface LockResolver {
        PluginLock resolve(PluginManifest manifest) throws Exception;
    }

    public record InitializeResult(
            PluginManifest manifest,
            PluginLock lock,
            LockedServer server,
            ServerDownloads.DownloadResult serverDownload
    ) {
    }
}
