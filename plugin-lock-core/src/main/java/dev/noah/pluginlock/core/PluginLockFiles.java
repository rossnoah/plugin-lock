package dev.noah.pluginlock.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PluginLockFiles {
    public static final String MANIFEST_FILE = "server-lock.json";
    public static final String LOCK_FILE = "server-lock.lock.json";

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .findAndRegisterModules();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .findAndRegisterModules();

    private PluginLockFiles() {
    }

    public static PluginManifest readManifest(Path path) throws IOException {
        return mapperFor(path).readValue(Files.readString(path), PluginManifest.class);
    }

    public static void writeManifest(Path path, PluginManifest manifest) throws IOException {
        Files.writeString(path, mapperFor(path).writeValueAsString(manifest));
    }

    public static PluginLock readLock(Path path) throws IOException {
        return JSON.readValue(Files.readString(path), PluginLock.class);
    }

    public static void writeLock(Path path, PluginLock lock) throws IOException {
        Files.writeString(path, JSON.writeValueAsString(lock));
    }

    private static ObjectMapper mapperFor(Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return YAML;
        }
        return JSON;
    }
}
