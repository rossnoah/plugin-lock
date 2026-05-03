package dev.noah.pluginlock.core.catalog;

import dev.noah.pluginlock.core.PluginResolver;
import dev.noah.pluginlock.core.model.PluginInspection;
import dev.noah.pluginlock.core.model.PluginMetadata;
import dev.noah.pluginlock.core.model.PluginRequest;
import dev.noah.pluginlock.core.model.PluginResolutionCheck;
import dev.noah.pluginlock.core.provider.HangarProvider;
import dev.noah.pluginlock.core.provider.ModrinthProvider;
import dev.noah.pluginlock.core.provider.PluginNotFoundException;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

public final class PluginCatalog {
    private final ModrinthProvider modrinthProvider;
    private final HangarProvider hangarProvider;
    private final PluginResolver pluginResolver;

    public PluginCatalog() {
        this(HttpClient.newHttpClient());
    }

    public PluginCatalog(HttpClient httpClient) {
        this(new ModrinthProvider(httpClient), new HangarProvider(httpClient), new PluginResolver(httpClient));
    }

    public PluginCatalog(ModrinthProvider modrinthProvider, HangarProvider hangarProvider, PluginResolver pluginResolver) {
        this.modrinthProvider = modrinthProvider;
        this.hangarProvider = hangarProvider;
        this.pluginResolver = pluginResolver;
    }

    public PluginMetadata fetchMetadata(String provider, String id) throws Exception {
        if ("modrinth".equalsIgnoreCase(provider)) {
            return modrinthProvider.fetchMetadata(id);
        }
        if ("hangar".equalsIgnoreCase(provider)) {
            return hangarProvider.fetchMetadata(id);
        }
        throw new IllegalArgumentException("Unsupported provider: " + provider);
    }

    public List<PluginMetadata> search(String provider, String query, int limit) throws Exception {
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

    public List<PluginMetadata> findProviderMatches(String id) throws Exception {
        List<PluginMetadata> matches = new ArrayList<>();
        addIfFound(matches, "modrinth", id);
        addIfFound(matches, "hangar", id);
        return matches;
    }

    public PluginInspection inspect(PluginRequest request, String loader) throws Exception {
        return pluginResolver.inspect(request, loader);
    }

    public PluginResolutionCheck check(PluginRequest request, String minecraftVersion, String loader) {
        return pluginResolver.check(request, minecraftVersion, loader);
    }

    private void addIfFound(List<PluginMetadata> matches, String provider, String id) throws Exception {
        try {
            matches.add(fetchMetadata(provider, id));
        } catch (PluginNotFoundException ignored) {
        }
    }
}
