package dev.noah.pluginlock.core.manifest;

import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.model.PluginRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestEditorTest {
    @Test
    void applyRequestAddsNewPlugin() {
        PluginManifest manifest = new PluginManifest();

        RequestChange change = ManifestEditor.applyRequest(manifest, new PluginRequest("luckperms", "modrinth", "latest"));

        assertTrue(change.changed());
        assertTrue(change.added());
        assertEquals(List.of("luckperms"), manifest.getPlugins().stream().map(PluginRequest::getId).toList());
    }

    @Test
    void applyRequestCleansDuplicateLogicalPluginIds() {
        PluginManifest manifest = new PluginManifest();
        manifest.setPlugins(new java.util.ArrayList<>(List.of(
                new PluginRequest("LuckPerms", "modrinth", "old"),
                new PluginRequest("luck-perms", "hangar", "old")
        )));

        RequestChange change = ManifestEditor.applyRequest(manifest, new PluginRequest("luckperms", "modrinth", "latest"));

        assertTrue(change.changed());
        assertTrue(change.duplicateCleanup());
        assertEquals(1, manifest.getPlugins().size());
        assertEquals("latest", manifest.getPlugins().getFirst().getVersion());
    }

    @Test
    void applyRequestDetectsUnchangedRequest() {
        PluginManifest manifest = new PluginManifest();
        manifest.setPlugins(new java.util.ArrayList<>(List.of(new PluginRequest("luckperms", "modrinth", "latest"))));

        RequestChange change = ManifestEditor.applyRequest(manifest, new PluginRequest("luckperms", "modrinth", "latest"));

        assertFalse(change.changed());
        assertEquals("already-added", change.label());
    }
}
