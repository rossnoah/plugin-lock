package dev.noah.pluginlock.core.catalog;

public record PluginCoordinate(String provider, String id, String version) {
    public static PluginCoordinate parse(String raw, String defaultProvider, String defaultVersion) {
        String parsedProvider = defaultProvider;
        String parsedId = raw;
        String parsedVersion = defaultVersion;
        int providerSeparator = raw.indexOf(':');
        if (providerSeparator > 0) {
            parsedProvider = raw.substring(0, providerSeparator);
            parsedId = raw.substring(providerSeparator + 1);
        }
        int versionSeparator = parsedId.lastIndexOf('@');
        if (versionSeparator > 0 && versionSeparator < parsedId.length() - 1) {
            parsedVersion = parsedId.substring(versionSeparator + 1);
            parsedId = parsedId.substring(0, versionSeparator);
        }
        if (parsedId.isBlank()) {
            throw new IllegalArgumentException("Plugin id cannot be blank");
        }
        return new PluginCoordinate(parsedProvider, parsedId, parsedVersion);
    }
}
