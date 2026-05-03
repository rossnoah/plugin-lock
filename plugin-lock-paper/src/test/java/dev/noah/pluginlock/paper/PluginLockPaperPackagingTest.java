package dev.noah.pluginlock.paper;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLockPaperPackagingTest {
    @Test
    void pluginYmlPointsAtPaperEntrypoint() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(input);
            String pluginYml = new String(input.readAllBytes());

            assertTrue(pluginYml.contains("name: PluginLock"));
            assertTrue(pluginYml.contains("main: dev.noah.pluginlock.paper.PluginLockPaperPlugin"));
            assertTrue(pluginYml.contains("pluginlock:"));
            assertTrue(pluginYml.contains("aliases: [plock]"));
            assertTrue(pluginYml.contains("pluginlock.use:"));
            assertTrue(pluginYml.contains("pluginlock.list:"));
            assertTrue(pluginYml.contains("pluginlock.doctor:"));
            assertTrue(pluginYml.contains("pluginlock.admin:"));
        }
    }

    @Test
    void pluginClassCanBeLoaded() {
        assertEquals("dev.noah.pluginlock.paper.PluginLockPaperPlugin", PluginLockPaperPlugin.class.getName());
    }
}
