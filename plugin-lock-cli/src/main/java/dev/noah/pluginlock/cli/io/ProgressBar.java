package dev.noah.pluginlock.cli.io;

import dev.noah.pluginlock.core.DownloadProgress;

import java.util.Locale;

public final class ProgressBar implements DownloadProgress {
    private static final int WIDTH = 28;
    private static final int MIN_FILE_NAME_LENGTH = 12;
    private static final int DEFAULT_TERMINAL_COLUMNS = 80;

    private final Terminal terminal;
    private final int columns;
    private String currentFile;
    private int lastPercent = -1;
    private int lastLineLength;

    public ProgressBar(Terminal terminal) {
        this.terminal = terminal;
        this.columns = terminalColumns();
    }

    @Override
    public void update(String fileName, long downloadedBytes, long totalBytes) {
        boolean newFile = !fileName.equals(currentFile);
        int percent = totalBytes > 0 ? (int) Math.min(100, downloadedBytes * 100 / totalBytes) : -1;
        if (newFile && lastLineLength > 0) {
            terminal.println("");
            lastLineLength = 0;
            lastPercent = -1;
        }
        if (!newFile && percent == lastPercent && downloadedBytes != totalBytes) {
            return;
        }
        currentFile = fileName;
        lastPercent = percent;

        String suffix = totalBytes > 0
                ? " " + bar(percent) + " " + percent + "% " + formatBytes(downloadedBytes) + "/" + formatBytes(totalBytes)
                : " " + formatBytes(downloadedBytes);
        String displayName = truncateFileName(fileName, Math.max(MIN_FILE_NAME_LENGTH, columns - suffix.length()));
        String line = displayName + suffix;
        int padding = Math.max(0, lastLineLength - line.length());
        terminal.print("\r\u001B[2K" + line + " ".repeat(padding));
        lastLineLength = line.length();
        if (totalBytes > 0 && downloadedBytes >= totalBytes) {
            terminal.println("");
            lastLineLength = 0;
        }
    }

    private static String truncateFileName(String fileName, int maxLength) {
        if (fileName.length() <= maxLength) {
            return fileName;
        }
        int suffixLength = Math.max(1, maxLength - 3);
        return "..." + fileName.substring(fileName.length() - suffixLength);
    }

    private static String bar(int percent) {
        int filled = Math.min(WIDTH, Math.max(0, percent * WIDTH / 100));
        return "[" + "#".repeat(filled) + "-".repeat(WIDTH - filled) + "]";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024) {
            return String.format(Locale.US, "%.1f KiB", kib);
        }
        return String.format(Locale.US, "%.1f MiB", kib / 1024.0);
    }

    private static int terminalColumns() {
        String columns = System.getenv("COLUMNS");
        if (columns != null) {
            try {
                int parsed = Integer.parseInt(columns);
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_TERMINAL_COLUMNS;
    }
}
