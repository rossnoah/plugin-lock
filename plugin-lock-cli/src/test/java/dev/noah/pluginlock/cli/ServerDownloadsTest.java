package dev.noah.pluginlock.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerDownloadsTest {
    @Test
    void filtersPrereleasesAndSortsStableVersionsBySemverDescending() {
        List<String> versions = ServerDownloads.minecraftVersions(List.of(
                "1.21.11-rc1",
                "26.1.2",
                "1.21.11",
                "1.21.9",
                "26.1",
                "1.21.11-pre5",
                "1.20.6",
                "26.1.2"
        ));

        assertEquals(List.of("26.1.2", "26.1", "1.21.11", "1.21.9", "1.20.6"), versions);
    }

    @Test
    void recognizesOnlyStableSemanticVersions() {
        assertTrue(ServerDownloads.SemanticVersion.isStable("26.1.2"));
        assertTrue(ServerDownloads.SemanticVersion.isStable("1.21.11"));
        assertFalse(ServerDownloads.SemanticVersion.isStable("1.21.11-rc1"));
        assertFalse(ServerDownloads.SemanticVersion.isStable("latest"));
    }

    @Test
    void mapsPurpurToPaperPluginLoader() {
        assertEquals("paper", ServerDownloads.pluginLoaderFor("purpur"));
        assertEquals("paper", ServerDownloads.pluginLoaderFor("paper"));
    }
}
