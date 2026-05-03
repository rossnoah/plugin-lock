package dev.noah.pluginlock.cli;

import sun.misc.Signal;

import java.util.concurrent.atomic.AtomicBoolean;

final class CancellationHandler {
    private static final int INTERRUPTED_EXIT_CODE = 130;

    private CancellationHandler() {
    }

    static void install(PluginLockCli cli) {
        Thread mainThread = Thread.currentThread();
        AtomicBoolean cancelling = new AtomicBoolean();
        Signal.handle(new Signal("INT"), signal -> {
            if (!cancelling.compareAndSet(false, true)) {
                Runtime.getRuntime().halt(INTERRUPTED_EXIT_CODE);
            }
            mainThread.interrupt();
            cli.context().output().error("pl", "Operation cancelled");
        });
    }
}
