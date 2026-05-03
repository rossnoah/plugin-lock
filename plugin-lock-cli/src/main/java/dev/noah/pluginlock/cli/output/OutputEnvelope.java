package dev.noah.pluginlock.cli.output;

import java.util.Map;

public record OutputEnvelope(String status, String command, String message, Map<String, ?> details) {
    public OutputEnvelope {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
