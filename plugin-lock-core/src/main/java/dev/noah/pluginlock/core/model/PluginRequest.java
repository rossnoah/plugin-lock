package dev.noah.pluginlock.core.model;

public final class PluginRequest {
    private String id;
    private String provider = "modrinth";
    private String version;

    public PluginRequest() {
    }

    public PluginRequest(String id, String provider, String version) {
        this.id = id;
        this.provider = provider;
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
