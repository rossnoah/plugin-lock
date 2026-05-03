package dev.noah.pluginlock.cli.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.noah.pluginlock.cli.io.Terminal;

import java.util.Map;

public final class JsonOutputRenderer implements OutputRenderer {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final Terminal terminal;

    public JsonOutputRenderer(Terminal terminal) {
        this.terminal = terminal;
    }

    @Override
    public void render(OutputEnvelope envelope) {
        write(Map.of(
                "status", envelope.status(),
                "command", envelope.command() == null ? "" : envelope.command(),
                "message", envelope.message() == null ? "" : envelope.message(),
                "details", envelope.details()
        ));
    }

    @Override
    public void warning(String message) {
        render(new OutputEnvelope("warning", "", message, Map.of()));
    }

    private void write(Map<String, ?> payload) {
        try {
            terminal.println(JSON.writeValueAsString(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write JSON output", exception);
        }
    }
}
