package dev.noah.pluginlock.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PluginLock {
    private int lockfileVersion = 1;
    private LockedServer server;
    private String minecraftVersion;
    private String loader;
    private String generatedAt = Instant.now().toString();
    private List<LockedPlugin> plugins = new ArrayList<>();

    public int getLockfileVersion() {
        return lockfileVersion;
    }

    public void setLockfileVersion(int lockfileVersion) {
        this.lockfileVersion = lockfileVersion;
    }

    public LockedServer getServer() {
        return server;
    }

    public void setServer(LockedServer server) {
        this.server = server;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public void setMinecraftVersion(String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
    }

    public String getLoader() {
        return loader;
    }

    public void setLoader(String loader) {
        this.loader = loader;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<LockedPlugin> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<LockedPlugin> plugins) {
        this.plugins = plugins;
    }
}
