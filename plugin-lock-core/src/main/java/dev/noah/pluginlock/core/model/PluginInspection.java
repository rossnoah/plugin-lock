package dev.noah.pluginlock.core.model;

import java.util.ArrayList;
import java.util.List;

public final class PluginInspection {
    private PluginMetadata metadata;
    private List<PluginVersion> versions = new ArrayList<>();

    public PluginMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(PluginMetadata metadata) {
        this.metadata = metadata;
    }

    public List<PluginVersion> getVersions() {
        return versions;
    }

    public void setVersions(List<PluginVersion> versions) {
        this.versions = versions;
    }
}
