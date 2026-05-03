package dev.noah.pluginlock.cli.output;

interface OutputRenderer {
    void render(OutputEnvelope envelope);

    void warning(String message);
}
