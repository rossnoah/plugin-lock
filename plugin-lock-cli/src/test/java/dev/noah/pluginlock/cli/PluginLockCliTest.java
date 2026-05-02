package dev.noah.pluginlock.cli;

import dev.noah.pluginlock.core.model.PluginMetadata;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLockCliTest {
    @Test
    void metadataConfirmationSummaryIncludesFormattedDownloadCount() {
        PluginMetadata metadata = new PluginMetadata();
        metadata.setId("luckperms");
        metadata.setProvider("modrinth");
        metadata.setName("LuckPerms");
        metadata.setAuthors(List.of("Luck"));
        metadata.setDownloads(1234567);
        metadata.setDescription("A permissions plugin");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output));
            PluginLockCli.printMetadata(metadata);
        } finally {
            System.setOut(originalOut);
        }

        String summary = output.toString();
        assertTrue(summary.contains("LuckPerms (modrinth:luckperms)"));
        assertTrue(summary.contains("Authors: Luck"));
        assertTrue(summary.contains("Downloads: 1,234,567"));
        assertTrue(summary.contains("Description: A permissions plugin"));
    }

    @Test
    void blankProviderSelectionUsesRequestedFallback() {
        InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream("\n".getBytes(StandardCharsets.UTF_8)));

            PluginMetadata selected = PluginLockCli.selectProvider(List.of(
                    metadata("modrinth", "perplayerkit"),
                    metadata("hangar", "PerPlayerKit")
            ), 1);

            assertEquals("hangar", selected.getProvider());
            assertEquals("PerPlayerKit", selected.getId());
        } finally {
            System.setIn(originalIn);
        }
    }

    private static PluginMetadata metadata(String provider, String id) {
        PluginMetadata metadata = new PluginMetadata();
        metadata.setProvider(provider);
        metadata.setId(id);
        metadata.setName(id);
        return metadata;
    }
}
