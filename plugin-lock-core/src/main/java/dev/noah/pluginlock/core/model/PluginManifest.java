package dev.noah.pluginlock.core.model;

import java.util.ArrayList;
import java.util.List;

public final class PluginManifest {
    private String minecraftVersion = "1.21.4";
    private String loader = "paper";
    private List<PluginRequest> plugins = new ArrayList<>();

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

    public List<PluginRequest> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<PluginRequest> plugins) {
        this.plugins = plugins;
    }
}
