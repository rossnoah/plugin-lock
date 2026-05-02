package dev.noah.pluginlock.core;

import java.io.IOException;
import java.io.OutputStream;

final class OutputStreamDiscard extends OutputStream {
    static final OutputStreamDiscard INSTANCE = new OutputStreamDiscard();

    private OutputStreamDiscard() {
    }

    @Override
    public void write(int b) throws IOException {
    }
}
