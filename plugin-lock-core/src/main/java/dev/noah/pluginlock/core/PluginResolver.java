package dev.noah.pluginlock.core;

import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginRequest;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.provider.ModrinthProvider;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PluginResolver {
    private final ModrinthProvider modrinthProvider;

    public PluginResolver() {
        this(HttpClient.newHttpClient());
    }

    public PluginResolver(HttpClient httpClient) {
        this.modrinthProvider = new ModrinthProvider(httpClient);
    }

    public PluginLock resolve(PluginManifest manifest) throws IOException, InterruptedException {
        List<LockedPlugin> lockedPlugins = new ArrayList<>();
        for (PluginRequest request : manifest.getPlugins()) {
            if (!"modrinth".equalsIgnoreCase(request.getProvider())) {
                throw new IllegalArgumentException("Unsupported provider: " + request.getProvider());
            }
            lockedPlugins.add(modrinthProvider.resolve(request, manifest.getMinecraftVersion(), manifest.getLoader()));
        }

        PluginLock lock = new PluginLock();
        lock.setMinecraftVersion(manifest.getMinecraftVersion());
        lock.setLoader(manifest.getLoader());
        lock.setGeneratedAt(Instant.now().toString());
        lock.setPlugins(lockedPlugins);
        return lock;
    }
}
