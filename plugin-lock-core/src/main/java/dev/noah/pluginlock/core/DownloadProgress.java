package dev.noah.pluginlock.core;

@FunctionalInterface
public interface DownloadProgress {
    DownloadProgress NONE = (fileName, downloadedBytes, totalBytes) -> {
    };

    void update(String fileName, long downloadedBytes, long totalBytes);
}
