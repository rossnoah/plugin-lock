package dev.noah.pluginlock.core;

import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginRequest;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.provider.HangarProvider;
import dev.noah.pluginlock.core.provider.ModrinthProvider;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PluginResolver {
    private final ModrinthProvider modrinthProvider;
    private final HangarProvider hangarProvider;

    public PluginResolver() {
        this(HttpClient.newHttpClient());
    }

    public PluginResolver(HttpClient httpClient) {
        this.modrinthProvider = new ModrinthProvider(httpClient);
        this.hangarProvider = new HangarProvider(httpClient);
    }

    public PluginLock resolve(PluginManifest manifest) throws IOException, InterruptedException {
        List<LockedPlugin> lockedPlugins = new ArrayList<>();
        for (PluginRequest request : manifest.getPlugins()) {
            lockedPlugins.add(resolve(request, manifest.getMinecraftVersion(), manifest.getLoader()));
        }

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion(manifest.getMinecraftVersion());
        lock.setLoader(manifest.getLoader());
        lock.setGeneratedAt(Instant.now().toString());
        lock.setPlugins(lockedPlugins);
        return lock;
    }

    private LockedPlugin resolve(PluginRequest request, String minecraftVersion, String loader)
            throws IOException, InterruptedException {
        if ("modrinth".equalsIgnoreCase(request.getProvider())) {
            return modrinthProvider.resolve(request, minecraftVersion, loader);
        }
        if ("hangar".equalsIgnoreCase(request.getProvider())) {
            return hangarProvider.resolve(request, minecraftVersion, loader);
        }
        throw new IllegalArgumentException("Unsupported provider: " + request.getProvider());
    }
}
