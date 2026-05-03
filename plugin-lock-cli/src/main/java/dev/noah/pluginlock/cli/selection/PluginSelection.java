package dev.noah.pluginlock.cli.selection;

import dev.noah.pluginlock.core.model.PluginRequest;

public record PluginSelection(PluginSelectionStatus status, PluginRequest request) {
    public static PluginSelection selected(PluginRequest request) {
        return new PluginSelection(PluginSelectionStatus.SELECTED, request);
    }

    public static PluginSelection exited() {
        return new PluginSelection(PluginSelectionStatus.EXITED, null);
    }

    public boolean isExited() {
        return status == PluginSelectionStatus.EXITED;
    }
}
