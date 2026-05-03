package dev.noah.pluginlock.cli;

import picocli.CommandLine;

public final class PluginLockVersionProvider implements CommandLine.IVersionProvider {
    private static final String FALLBACK_VERSION = "0.1.0-SNAPSHOT";

    @Override
    public String[] getVersion() {
        String version = PluginLockCli.class.getPackage().getImplementationVersion();
        if (version == null || version.isBlank()) {
            version = FALLBACK_VERSION;
        }
        return new String[]{"plugin-lock " + version};
    }
}
