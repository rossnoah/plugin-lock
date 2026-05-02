package dev.noah.pluginlock.core;

import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginResolverTest {
    @Test
    void rejectsUnsupportedProviderBeforeNetworkRequest() {
        PluginManifest manifest = new PluginManifest();
        manifest.setPlugins(List.of(new PluginRequest("luckperms", "unknown", "latest")));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new PluginResolver().resolve(manifest));

        assertTrue(exception.getMessage().contains("Unsupported provider"));
    }
}
