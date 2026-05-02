package dev.noah.pluginlock.core.provider;

import java.io.IOException;

public final class PluginNotFoundException extends IOException {
    public PluginNotFoundException(String provider, String pluginId) {
        super("Plugin not found on " + provider + ": " + pluginId);
    }
}
