package dev.noah.pluginlock.core.lock;

import dev.noah.pluginlock.core.model.PluginLock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LockSnapshotsTest {
    @Test
    void ignoresGeneratedAtWhenComparingLocks() {
        PluginLock left = new PluginLock();
        left.setGeneratedAt("2026-01-01T00:00:00Z");
        PluginLock right = new PluginLock();
        right.setGeneratedAt("2026-01-02T00:00:00Z");

        assertTrue(LockSnapshots.sameLock(left, right));
    }
}
