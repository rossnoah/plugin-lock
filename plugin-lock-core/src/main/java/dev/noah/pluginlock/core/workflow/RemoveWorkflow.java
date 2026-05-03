package dev.noah.pluginlock.core.workflow;

import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.manifest.ManifestEditor;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RemoveWorkflow {
    private final ProjectService projectService;

    public RemoveWorkflow(ProjectService projectService) {
        this.projectService = projectService;
    }

    public RemoveResult remove(List<String> ids, Path pluginsDir) throws Exception {
        if (Files.notExists(projectService.paths().manifestPath()) && Files.notExists(projectService.paths().lockPath())) {
            throw new IllegalStateException("No server-lock.json or server-lock.lock.json found. Run `pl init` first.");
        }

        List<LockedPlugin> removedLockedPlugins;
        PluginLock lock = new PluginLock();
        if (Files.exists(projectService.paths().lockPath())) {
            lock = PluginLockFiles.readLock(projectService.paths().lockPath());
            removedLockedPlugins = lock.getPlugins().stream()
                    .filter(plugin -> ManifestEditor.matchesLockedPlugin(ids, plugin))
                    .toList();
            lock.getPlugins().removeIf(plugin -> ManifestEditor.matchesLockedPlugin(ids, plugin));
            PluginLockFiles.writeLock(projectService.paths().lockPath(), lock);
        } else {
            removedLockedPlugins = List.of();
        }

        List<Path> deleted = new ArrayList<>();
        for (LockedPlugin plugin : removedLockedPlugins) {
            Path jar = pluginsDir.resolve(plugin.getFileName());
            if (Files.deleteIfExists(jar)) {
                deleted.add(jar);
            }
        }

        PluginManifest manifest = new PluginManifest();
        List<PluginRequest> removedManifestRequests = List.of();
        if (Files.exists(projectService.paths().manifestPath())) {
            manifest = PluginLockFiles.readManifest(projectService.paths().manifestPath());
            removedManifestRequests = ManifestEditor.removeRequests(manifest, ids, removedLockedPlugins);
            projectService.writeManifest(manifest);
        }

        return new RemoveResult(ids, removedLockedPlugins, removedManifestRequests, deleted, manifest, lock);
    }

    public record RemoveResult(
            List<String> requested,
            List<LockedPlugin> removedLockedPlugins,
            List<PluginRequest> removedManifestRequests,
            List<Path> deletedFiles,
            PluginManifest manifest,
            PluginLock lock
    ) {
        public RemoveResult {
            requested = List.copyOf(requested);
            removedLockedPlugins = List.copyOf(removedLockedPlugins);
            removedManifestRequests = List.copyOf(removedManifestRequests);
            deletedFiles = List.copyOf(deletedFiles);
        }
    }
}
