package dev.noah.pluginlock.core.model;

import java.util.ArrayList;
import java.util.List;

public final class PluginVersion {
    private String id;
    private String name;
    private List<String> minecraftVersions = new ArrayList<>();
    private List<String> loaders = new ArrayList<>();
    private String fileName;
    private boolean downloadable;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getMinecraftVersions() {
        return minecraftVersions;
    }

    public void setMinecraftVersions(List<String> minecraftVersions) {
        this.minecraftVersions = minecraftVersions;
    }

    public List<String> getLoaders() {
        return loaders;
    }

    public void setLoaders(List<String> loaders) {
        this.loaders = loaders;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isDownloadable() {
        return downloadable;
    }

    public void setDownloadable(boolean downloadable) {
        this.downloadable = downloadable;
    }
}
