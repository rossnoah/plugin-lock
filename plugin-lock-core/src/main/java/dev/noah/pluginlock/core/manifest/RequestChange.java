package dev.noah.pluginlock.core.manifest;

import dev.noah.pluginlock.core.model.PluginRequest;

import java.util.List;

public record RequestChange(PluginRequest request, List<PluginRequest> replaced, boolean changed) {
    public RequestChange {
        replaced = List.copyOf(replaced);
    }

    public boolean added() {
        return replaced.isEmpty();
    }

    public boolean providerSwitch() {
        return replaced.stream().anyMatch(existing -> !ManifestEditor.same(existing.getProvider(), request.getProvider()));
    }

    public boolean duplicateCleanup() {
        return replaced.size() > 1;
    }

    public String label() {
        if (!changed) {
            return "already-added";
        }
        if (added()) {
            return "added";
        }
        if (providerSwitch()) {
            return "provider-switched";
        }
        if (duplicateCleanup()) {
            return "duplicate-cleaned";
        }
        return "updated";
    }
}
