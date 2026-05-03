package dev.noah.pluginlock.core.run;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRunCommandTest {
    @Test
    void normalizesMemoryValues() {
        assertEquals("2048M", ServerRunCommand.normalizeMemory("2048"));
        assertEquals("2048M", ServerRunCommand.normalizeMemory("2048m"));
        assertEquals("2G", ServerRunCommand.normalizeMemory("2gb"));
    }

    @Test
    void buildsOptimizedServerCommand() {
        List<String> command = ServerRunCommand.build("java", "2048M", Path.of("server.jar"));

        assertEquals("java", command.getFirst());
        assertTrue(command.contains("-Xms2048M"));
        assertTrue(command.contains("-Xmx2048M"));
        assertTrue(command.contains("-XX:+UseG1GC"));
        assertTrue(command.contains("-jar"));
        assertEquals("--nogui", command.getLast());
    }
}
