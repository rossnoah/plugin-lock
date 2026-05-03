package dev.noah.pluginlock.core.workflow;

import dev.noah.pluginlock.core.DownloadProgress;
import dev.noah.pluginlock.core.PluginLockDefaults;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.lock.LockSnapshots;
import dev.noah.pluginlock.core.manifest.ManifestEditor;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.LockedServer;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.server.ServerDownloads;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UpdateWorkflow {
    private final ProjectService projectService;

    public UpdateWorkflow(ProjectService projectService) {
        this.projectService = projectService;
    }

    public UpdateResult update(List<String> requestedIds, boolean updateServer, DownloadProgress progress) throws Exception {
        if (Files.notExists(projectService.paths().manifestPath())) {
            throw new IllegalStateException("No server-lock.json found. Run `pl init` first.");
        }
        PluginManifest manifest = PluginLockFiles.readManifest(projectService.paths().manifestPath());
        PluginLock existing = Files.exists(projectService.paths().lockPath())
                ? PluginLockFiles.readLock(projectService.paths().lockPath())
                : null;
        var existingState = existing == null ? null : LockSnapshots.stableLock(existing);
        PluginLock resolved = projectService.resolveLock(manifest);
        List<String> selectedUpdateKeys = requestedIds.isEmpty() ? List.of() : selectedPluginKeys(existing, resolved, requestedIds);
        PluginLock updated = requestedIds.isEmpty() || existing == null
                ? resolved
                : mergeSelectedUpdates(existing, resolved, requestedIds);
        int updatedPluginCount = requestedIds.isEmpty() ? updated.getPlugins().size() : selectedUpdateKeys.size();
        ServerDownloads.DownloadResult serverDownload = null;
        if (updateServer) {
            LockedServer lockedServer = latestServer(manifest, existing);
            serverDownload = projectService.serverDownloads().download(lockedServer, projectService.paths().root(), progress);
            updated.setServer(lockedServer);
        }
        boolean lockChanged = existingState == null || !existingState.equals(LockSnapshots.stableLock(updated));
        boolean serverDownloaded = serverDownload != null && serverDownload.downloaded();
        if (lockChanged) {
            projectService.writeLock(updated);
        }
        return new UpdateResult(
                requestedIds,
                updateServer,
                updated,
                updatedPluginCount,
                lockChanged,
                serverDownloaded,
                serverDownload,
                selectedUpdateKeys
        );
    }

    public static List<String> selectedPluginKeys(PluginLock existing, PluginLock resolved, List<String> ids) {
        List<String> selectedKeys = new ArrayList<>();
        if (existing != null) {
            addSelectedPluginKeys(selectedKeys, existing.getPlugins(), ids);
        }
        addSelectedPluginKeys(selectedKeys, resolved.getPlugins(), ids);
        return selectedKeys;
    }

    public static PluginLock mergeSelectedUpdates(PluginLock existing, PluginLock resolved, List<String> ids) {
        Map<String, LockedPlugin> resolvedByKey = new LinkedHashMap<>();
        for (LockedPlugin plugin : resolved.getPlugins()) {
            resolvedByKey.put(pluginKey(plugin), plugin);
        }
        List<LockedPlugin> mergedPlugins = new ArrayList<>();
        for (LockedPlugin plugin : existing.getPlugins()) {
            LockedPlugin replacement = resolvedByKey.get(pluginKey(plugin));
            mergedPlugins.add(replacement != null && ManifestEditor.matchesLockedPlugin(ids, plugin) ? replacement : plugin);
        }
        for (LockedPlugin plugin : resolved.getPlugins()) {
            boolean alreadyPresent = mergedPlugins.stream().anyMatch(existingPlugin -> pluginKey(existingPlugin).equals(pluginKey(plugin)));
            if (!alreadyPresent && ManifestEditor.matchesLockedPlugin(ids, plugin)) {
                mergedPlugins.add(plugin);
            }
        }
        existing.setMinecraftVersion(resolved.getMinecraftVersion());
        existing.setLoader(resolved.getLoader());
        existing.setPlugins(mergedPlugins);
        return existing;
    }

    public static String pluginKey(LockedPlugin plugin) {
        return blank(plugin.getProvider()).toLowerCase(java.util.Locale.ROOT) + ":"
                + blank(plugin.getId()).toLowerCase(java.util.Locale.ROOT);
    }

    private LockedServer latestServer(PluginManifest manifest, PluginLock existing) throws Exception {
        String provider = existing != null && existing.getServer() != null
                ? existing.getServer().getProvider()
                : manifest.getLoader();
        if (provider == null || provider.isBlank()) {
            provider = PluginLockDefaults.LOADER;
        }
        return projectService.serverDownloads().latest(provider, manifest.getMinecraftVersion());
    }

    private static void addSelectedPluginKeys(List<String> selectedKeys, List<LockedPlugin> plugins, List<String> ids) {
        for (LockedPlugin plugin : plugins) {
            if (ManifestEditor.matchesLockedPlugin(ids, plugin)) {
                String key = pluginKey(plugin);
                if (!selectedKeys.contains(key)) {
                    selectedKeys.add(key);
                }
            }
        }
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    public record UpdateResult(
            List<String> requestedIds,
            boolean server,
            PluginLock lock,
            int updatedPluginCount,
            boolean lockChanged,
            boolean serverDownloaded,
            ServerDownloads.DownloadResult serverDownload,
            List<String> selectedUpdateKeys
    ) {
        public UpdateResult {
            requestedIds = List.copyOf(requestedIds);
            selectedUpdateKeys = List.copyOf(selectedUpdateKeys);
        }

        public boolean changed() {
            return lockChanged || serverDownloaded;
        }
    }
}
