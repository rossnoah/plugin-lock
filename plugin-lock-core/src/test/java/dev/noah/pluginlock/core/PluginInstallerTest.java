package dev.noah.pluginlock.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginInstallerTest {
    @TempDir
    Path tempDir;

    @Test
    void calculatesSha512ForFile() throws Exception {
        Path file = tempDir.resolve("plugin.jar");
        Files.writeString(file, "abc", StandardCharsets.UTF_8);

        assertEquals(
                "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a"
                        + "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
                PluginInstaller.sha512(file));
    }
}
