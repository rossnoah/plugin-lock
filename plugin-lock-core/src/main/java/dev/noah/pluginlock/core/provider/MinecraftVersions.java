package dev.noah.pluginlock.core.provider;

import java.util.ArrayList;
import java.util.List;

final class MinecraftVersions {
    private static final Version BREAKING_1_13 = parse("1.13");

    private MinecraftVersions() {
    }

    static boolean supports(String pluginVersion, String serverVersion) {
        Version plugin = parse(pluginVersion);
        Version server = parse(serverVersion);
        if (plugin == null || server == null) {
            return pluginVersion.equals(serverVersion);
        }
        if (crossesFlatteningBoundary(plugin, server)) {
            return false;
        }
        return plugin.compareTo(server) <= 0;
    }

    static boolean isExact(String pluginVersion, String serverVersion) {
        Version plugin = parse(pluginVersion);
        Version server = parse(serverVersion);
        if (plugin == null || server == null) {
            return pluginVersion.equals(serverVersion);
        }
        return plugin.compareTo(server) == 0;
    }

    private static boolean crossesFlatteningBoundary(Version plugin, Version server) {
        return plugin.compareTo(BREAKING_1_13) < 0 && server.compareTo(BREAKING_1_13) >= 0;
    }

    private static Version parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split("\\.");
        List<Integer> numbers = new ArrayList<>();
        for (String part : parts) {
            StringBuilder digits = new StringBuilder();
            for (int index = 0; index < part.length(); index++) {
                char next = part.charAt(index);
                if (!Character.isDigit(next)) {
                    break;
                }
                digits.append(next);
            }
            if (digits.isEmpty()) {
                return null;
            }
            numbers.add(Integer.parseInt(digits.toString()));
        }
        return new Version(numbers);
    }

    private record Version(List<Integer> parts) implements Comparable<Version> {
        @Override
        public int compareTo(Version other) {
            int length = Math.max(parts.size(), other.parts.size());
            for (int index = 0; index < length; index++) {
                int left = index < parts.size() ? parts.get(index) : 0;
                int right = index < other.parts.size() ? other.parts.get(index) : 0;
                int compared = Integer.compare(left, right);
                if (compared != 0) {
                    return compared;
                }
            }
            return 0;
        }
    }
}
