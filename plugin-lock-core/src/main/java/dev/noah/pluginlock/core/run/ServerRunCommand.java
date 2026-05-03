package dev.noah.pluginlock.core.run;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ServerRunCommand {
    private ServerRunCommand() {
    }

    public static List<String> build(String javaExecutable, String memory, Path serverJar) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        command.add("-Xms" + memory);
        command.add("-Xmx" + memory);
        command.addAll(List.of(
                "--add-modules=jdk.incubator.vector",
                "-XX:+UseG1GC",
                "-XX:+ParallelRefProcEnabled",
                "-XX:MaxGCPauseMillis=200",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+DisableExplicitGC",
                "-XX:+AlwaysPreTouch",
                "-XX:G1HeapWastePercent=5",
                "-XX:G1MixedGCCountTarget=4",
                "-XX:InitiatingHeapOccupancyPercent=15",
                "-XX:G1MixedGCLiveThresholdPercent=90",
                "-XX:G1RSetUpdatingPauseTimePercent=5",
                "-XX:SurvivorRatio=32",
                "-XX:+PerfDisableSharedMem",
                "-XX:MaxTenuringThreshold=1",
                "-Dusing.aikars.flags=https://mcflags.emc.gs",
                "-Daikars.new.flags=true",
                "-XX:G1NewSizePercent=30",
                "-XX:G1MaxNewSizePercent=40",
                "-XX:G1HeapRegionSize=8M",
                "-XX:G1ReservePercent=20",
                "-jar",
                serverJar.toString()
        ));
        command.add("--nogui");
        return command;
    }

    public static String normalizeMemory(String memory) {
        String normalized = memory.trim().toUpperCase(Locale.ROOT);
        if (normalized.endsWith("GB")) {
            normalized = normalized.substring(0, normalized.length() - 2) + "G";
        }
        if (normalized.endsWith("MB")) {
            normalized = normalized.substring(0, normalized.length() - 2) + "M";
        }
        if (normalized.matches("\\d+")) {
            return normalized + "M";
        }
        if (!normalized.matches("\\d+[MG]")) {
            throw new IllegalArgumentException("Memory must look like 2048M, 2G, or 2048");
        }
        return normalized;
    }

    public static String format(List<String> command) {
        return String.join(" ", command.stream().map(ServerRunCommand::quoteIfNeeded).toList());
    }

    private static String quoteIfNeeded(String value) {
        return value.matches("[A-Za-z0-9_./:=+@%-]+") ? value : "'" + value.replace("'", "'\\''") + "'";
    }
}
