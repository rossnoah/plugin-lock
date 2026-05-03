package dev.noah.pluginlock.core.lock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.noah.pluginlock.core.model.PluginLock;

public final class LockSnapshots {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private LockSnapshots() {
    }

    public static boolean sameLock(PluginLock left, PluginLock right) {
        return stableLock(left).equals(stableLock(right));
    }

    public static ObjectNode stableLock(PluginLock lock) {
        ObjectNode node = JSON.valueToTree(lock);
        node.remove("generatedAt");
        return node;
    }
}
