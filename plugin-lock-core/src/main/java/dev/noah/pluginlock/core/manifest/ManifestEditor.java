package dev.noah.pluginlock.core.manifest;

import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ManifestEditor {
    private ManifestEditor() {
    }

    public static void ensurePluginsList(PluginManifest manifest) {
        if (manifest.getPlugins() == null) {
            manifest.setPlugins(new ArrayList<>());
        }
    }

    public static RequestChange applyRequest(PluginManifest manifest, PluginRequest request) {
        ensurePluginsList(manifest);
        List<PluginRequest> matched = manifest.getPlugins().stream()
                .filter(plugin -> sameLogicalPlugin(plugin, request))
                .toList();
        if (matched.size() == 1 && sameRequest(matched.getFirst(), request)) {
            return new RequestChange(request, matched, false);
        }
        manifest.getPlugins().removeIf(plugin -> sameLogicalPlugin(plugin, request));
        manifest.getPlugins().add(request);
        return new RequestChange(request, matched, true);
    }

    public static List<PluginRequest> removeRequests(
            PluginManifest manifest,
            List<String> ids,
            List<LockedPlugin> removedLockedPlugins
    ) {
        ensurePluginsList(manifest);
        List<PluginRequest> removed = manifest.getPlugins().stream()
                .filter(plugin -> matchesManifestPlugin(ids, removedLockedPlugins, plugin))
                .toList();
        manifest.getPlugins().removeIf(plugin -> matchesManifestPlugin(ids, removedLockedPlugins, plugin));
        return removed;
    }

    public static boolean matchesLockedPlugin(List<String> ids, LockedPlugin plugin) {
        return matchesAny(ids, plugin.getId())
                || matchesAny(ids, plugin.getName())
                || matchesAny(ids, plugin.getFileName())
                || matchesAny(ids, fileStem(plugin.getFileName()));
    }

    public static boolean matchesAny(List<String> ids, String value) {
        return value != null && ids.stream().anyMatch(id -> id.equalsIgnoreCase(value));
    }

    public static String requestId(PluginRequest request) {
        return request.getProvider() + ":" + request.getId();
    }

    public static boolean samePlugin(String leftProvider, String leftId, String rightProvider, String rightId) {
        return same(leftProvider, rightProvider) && same(leftId, rightId);
    }

    static boolean same(String left, String right) {
        return blank(left).equalsIgnoreCase(blank(right));
    }

    private static boolean sameRequest(PluginRequest existing, PluginRequest request) {
        return existing != null
                && samePlugin(existing.getProvider(), existing.getId(), request.getProvider(), request.getId())
                && same(existing.getVersion(), request.getVersion());
    }

    private static boolean sameLogicalPlugin(PluginRequest left, PluginRequest right) {
        return samePlugin(left.getProvider(), left.getId(), right.getProvider(), right.getId())
                || samePluginId(left.getId(), right.getId());
    }

    private static boolean samePluginId(String left, String right) {
        String leftKey = pluginIdKey(left);
        return !leftKey.isBlank() && leftKey.equals(pluginIdKey(right));
    }

    private static String pluginIdKey(String value) {
        String normalized = blank(value).trim().toLowerCase(Locale.ROOT);
        StringBuilder key = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                key.append(character);
            }
        }
        return key.toString();
    }

    private static boolean matchesManifestPlugin(List<String> ids, List<LockedPlugin> removedLockedPlugins, PluginRequest plugin) {
        return matchesAny(ids, plugin.getId())
                || removedLockedPlugins.stream().anyMatch(removed -> same(removed.getProvider(), plugin.getProvider())
                && same(removed.getId(), plugin.getId()));
    }

    private static String fileStem(String fileName) {
        if (fileName == null) {
            return null;
        }
        return fileName.toLowerCase(Locale.ROOT).endsWith(".jar")
                ? fileName.substring(0, fileName.length() - ".jar".length())
                : fileName;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
