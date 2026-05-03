package dev.noah.pluginlock.cli.io;

public final class Ansi {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";

    private Ansi() {
    }

    public static String bold(String text) {
        return color(BOLD, text);
    }

    public static String dim(String text) {
        return color(DIM, text);
    }

    public static String red(String text) {
        return color(RED, text);
    }

    public static String green(String text) {
        return color(GREEN, text);
    }

    public static String yellow(String text) {
        return color(YELLOW, text);
    }

    public static String blue(String text) {
        return color(BLUE, text);
    }

    private static String color(String code, String text) {
        if (!enabled()) {
            return text;
        }
        return code + text + RESET;
    }

    private static boolean enabled() {
        return System.console() != null && System.getenv("NO_COLOR") == null;
    }
}
