package dev.noah.pluginlock.cli.output;

import dev.noah.pluginlock.cli.io.Ansi;
import dev.noah.pluginlock.cli.io.Terminal;

public final class TextOutputRenderer implements OutputRenderer {
    private final Terminal terminal;
    private final boolean verbose;

    public TextOutputRenderer(Terminal terminal, boolean verbose) {
        this.terminal = terminal;
        this.verbose = verbose;
    }

    @Override
    public void render(OutputEnvelope envelope) {
        if ("error".equals(envelope.status())) {
            terminal.error(Ansi.red("Error: ") + envelope.message());
            return;
        }
        terminal.println(Ansi.green(envelope.message()));
        if (verbose && envelope.details() != null && !envelope.details().isEmpty()) {
            envelope.details().forEach((key, value) -> terminal.println(Ansi.dim(key + ": " + value)));
        }
    }

    @Override
    public void warning(String message) {
        terminal.error(Ansi.yellow("Warning: ") + message);
    }
}
