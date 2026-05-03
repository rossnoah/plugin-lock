package dev.noah.pluginlock.core.workflow;

import dev.noah.pluginlock.core.DownloadProgress;
import dev.noah.pluginlock.core.PluginInstaller;
import dev.noah.pluginlock.core.PluginInstaller.InstallResult;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.lock.LockSnapshots;
import dev.noah.pluginlock.core.manifest.ManifestEditor;
import dev.noah.pluginlock.core.manifest.RequestChange;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class InstallWorkflow {
    private final ProjectService projectService;
    private final PluginInstaller pluginInstaller;

    public InstallWorkflow(ProjectService projectService) {
        this(projectService, new PluginInstaller());
    }

    public InstallWorkflow(ProjectService projectService, PluginInstaller pluginInstaller) {
        this.projectService = projectService;
        this.pluginInstaller = pluginInstaller;
    }

    public InstallWorkflowResult install(List<PluginRequest> requests, Path pluginsDir, String minecraftVersion, DownloadProgress progress)
            throws Exception {
        boolean shouldUseManifest = !requests.isEmpty()
                || minecraftVersion != null && !minecraftVersion.isBlank()
                || Files.exists(projectService.paths().manifestPath());
        if (shouldUseManifest) {
            PluginManifest manifest = projectService.readManifestOrDefault();
            ManifestEditor.ensurePluginsList(manifest);
            boolean manifestChanged = applyInstallOptions(manifest, minecraftVersion);
            List<RequestChange> requestChanges = new ArrayList<>();
            for (PluginRequest request : requests) {
                RequestChange change = ManifestEditor.applyRequest(manifest, request);
                if (change.changed()) {
                    manifestChanged = true;
                }
                requestChanges.add(change);
            }
            PluginLock lock = projectService.resolveLock(manifest);
            boolean lockChanged = Files.notExists(projectService.paths().lockPath())
                    || !LockSnapshots.sameLock(PluginLockFiles.readLock(projectService.paths().lockPath()), lock);
            if (manifestChanged) {
                projectService.writeManifest(manifest);
            }
            if (lockChanged) {
                projectService.writeLock(lock);
            }
            InstallResult installResult = pluginInstaller.install(lock, pluginsDir, progress);
            return new InstallWorkflowResult(lock, requests, installResult, pluginsDir, manifestChanged, lockChanged, requestChanges);
        }
        if (Files.exists(projectService.paths().lockPath())) {
            PluginLock lock = projectService.readLock();
            InstallResult installResult = pluginInstaller.install(lock, pluginsDir, progress);
            return new InstallWorkflowResult(lock, List.of(), installResult, pluginsDir, false, false, List.of());
        }
        throw new IllegalStateException("No server-lock.json or server-lock.lock.json found. Run `pl init` first.");
    }

    public CleanInstallResult cleanInstall(Path pluginsDir, DownloadProgress progress) throws Exception {
        PluginLock lock = projectService.readLock();
        Files.createDirectories(pluginsDir);
        if (lock.getPlugins().isEmpty()) {
            return new CleanInstallResult(lock, new InstallResult(List.of(), List.of()), pluginsDir, false);
        }
        for (LockedPlugin plugin : lock.getPlugins()) {
            Files.deleteIfExists(pluginsDir.resolve(plugin.getFileName()));
        }
        InstallResult result = pluginInstaller.install(lock, pluginsDir, progress);
        return new CleanInstallResult(lock, result, pluginsDir, true);
    }

    private static boolean applyInstallOptions(PluginManifest manifest, String minecraftVersion) {
        if (minecraftVersion == null || minecraftVersion.isBlank()
                || same(manifest.getMinecraftVersion(), minecraftVersion.trim())) {
            return false;
        }
        manifest.setMinecraftVersion(minecraftVersion.trim());
        return true;
    }

    private static boolean same(String left, String right) {
        return blank(left).equalsIgnoreCase(blank(right));
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    public record InstallWorkflowResult(
            PluginLock lock,
            List<PluginRequest> requested,
            InstallResult installResult,
            Path pluginsDir,
            boolean manifestChanged,
            boolean lockChanged,
            List<RequestChange> requestChanges
    ) {
        public InstallWorkflowResult {
            requested = List.copyOf(requested);
            requestChanges = List.copyOf(requestChanges);
        }

        public boolean changed() {
            return manifestChanged || lockChanged || installResult.installedCount() > 0;
        }
    }

    public record CleanInstallResult(
            PluginLock lock,
            InstallResult installResult,
            Path pluginsDir,
            boolean changed
    ) {
    }
}
