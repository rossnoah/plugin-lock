package dev.noah.pluginlock.core.model;

public final class PluginResolutionCheck {
    private boolean resolvable;
    private LockedPlugin plugin;
    private String message;

    public static PluginResolutionCheck ok(LockedPlugin plugin) {
        PluginResolutionCheck check = new PluginResolutionCheck();
        check.setResolvable(true);
        check.setPlugin(plugin);
        check.setMessage("Version OK");
        return check;
    }

    public static PluginResolutionCheck failed(String message) {
        PluginResolutionCheck check = new PluginResolutionCheck();
        check.setResolvable(false);
        check.setMessage(message);
        return check;
    }

    public boolean isResolvable() {
        return resolvable;
    }

    public void setResolvable(boolean resolvable) {
        this.resolvable = resolvable;
    }

    public LockedPlugin getPlugin() {
        return plugin;
    }

    public void setPlugin(LockedPlugin plugin) {
        this.plugin = plugin;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
