package dev.noah.pluginlock.core.provider;

import dev.noah.pluginlock.core.TestHttpServer;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginMetadata;
import dev.noah.pluginlock.core.model.PluginRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthProviderIntegrationTest {
    @Test
    void resolvesPluginAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.json("/project/luckperms", """
                    {"id":"Vebnzrzj","title":"LuckPerms"}
                    """);
            server.json("/project/luckperms/version?loaders=%5B%22paper%22%5D&game_versions=%5B%221.21.4%22%5D", """
                    [{
                      "id":"version-1",
                      "version_number":"v1.0.0",
                      "files":[
                        {
                          "filename":"LuckPerms.jar",
                          "url":"https://example.test/LuckPerms.jar",
                          "primary":true,
                          "size":123,
                          "hashes":{"sha512":"abc123"}
                        }
                      ]
                    }]
                    """);

            LockedPlugin plugin = new ModrinthProvider(HttpClient.newHttpClient(), server.baseUri())
                    .resolve(new PluginRequest("luckperms", "modrinth", "latest"), "1.21.4", "paper");

            assertEquals("luckperms", plugin.getId());
            assertEquals("LuckPerms", plugin.getName());
            assertEquals("version-1", plugin.getVersionId());
            assertEquals("LuckPerms.jar", plugin.getFileName());
            assertEquals("abc123", plugin.getSha512());
        }
    }

    @Test
    void choosesRequestedVersionNumberAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.json("/project/demo", """
                    {"id":"project-1","title":"Demo"}
                    """);
            server.json("/project/demo/version?loaders=%5B%22paper%22%5D&game_versions=%5B%221.21.4%22%5D", """
                    [
                      {"id":"new","version_number":"2.0.0","files":[{"filename":"new.jar","url":"https://example.test/new.jar","size":1,"hashes":{"sha512":"newhash"}}]},
                      {"id":"old","version_number":"1.0.0","files":[{"filename":"old.jar","url":"https://example.test/old.jar","primary":true,"size":1,"hashes":{"sha512":"oldhash"}}]}
                    ]
                    """);

            LockedPlugin plugin = new ModrinthProvider(HttpClient.newHttpClient(), server.baseUri())
                    .resolve(new PluginRequest("demo", "modrinth", "1.0.0"), "1.21.4", "paper");

            assertEquals("old", plugin.getVersionId());
            assertEquals("old.jar", plugin.getFileName());
        }
    }

    @Test
    void reportsMissingCompatibleVersionsAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.json("/project/demo", """
                    {"id":"project-1","title":"Demo"}
                    """);
            server.json("/project/demo/version?loaders=%5B%22paper%22%5D&game_versions=%5B%221.21.4%22%5D", "[]");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    new ModrinthProvider(HttpClient.newHttpClient(), server.baseUri())
                            .resolve(new PluginRequest("demo", "modrinth", "latest"), "1.21.4", "paper"));

            assertTrue(exception.getMessage().contains("No compatible Modrinth versions"));
        }
    }

    @Test
    void fetchesPluginMetadataAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.json("/project/luckperms", """
                    {
                      "id":"Vebnzrzj",
                      "title":"LuckPerms",
                      "description":"A permissions plugin",
                      "downloads":123456,
                      "team":"team-1"
                    }
                    """);
            server.json("/team/team-1/members", """
                    [
                      {"user":{"username":"Luck"}},
                      {"user":{"username":"Perms"}}
                    ]
                    """);

            PluginMetadata metadata = new ModrinthProvider(HttpClient.newHttpClient(), server.baseUri())
                    .fetchMetadata("luckperms");

            assertEquals("luckperms", metadata.getId());
            assertEquals("LuckPerms", metadata.getName());
            assertEquals("A permissions plugin", metadata.getDescription());
            assertEquals(123456, metadata.getDownloads());
            assertEquals(java.util.List.of("Luck", "Perms"), metadata.getAuthors());
        }
    }

    @Test
    void reportsPluginNotFoundAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.response("/project/missing", 404, "application/json",
                    "{}".getBytes(StandardCharsets.UTF_8));

            PluginNotFoundException exception = assertThrows(PluginNotFoundException.class, () ->
                    new ModrinthProvider(HttpClient.newHttpClient(), server.baseUri()).fetchMetadata("missing"));

            assertTrue(exception.getMessage().contains("Plugin not found on Modrinth: missing"));
        }
    }

    @Test
    @Tag("live")
    void resolvesLuckPermsAgainstRealModrinthApi() throws Exception {
        LockedPlugin plugin = new ModrinthProvider(HttpClient.newHttpClient())
                .resolve(new PluginRequest("luckperms", "modrinth", "latest"), "1.21.4", "paper");

        assertEquals("luckperms", plugin.getId());
        assertEquals("modrinth", plugin.getProvider());
        assertTrue(plugin.getDownloadUrl().startsWith("https://"));
        assertTrue(plugin.getFileName().endsWith(".jar"));
        assertEquals(128, plugin.getSha512().length());
        assertTrue(plugin.getSize() > 0);
    }

    @Test
    @Tag("live")
    void fetchesLuckPermsMetadataAgainstRealModrinthApi() throws Exception {
        PluginMetadata metadata = new ModrinthProvider(HttpClient.newHttpClient()).fetchMetadata("luckperms");

        assertEquals("luckperms", metadata.getId());
        assertEquals("modrinth", metadata.getProvider());
        assertTrue(metadata.getDownloads() > 0);
        assertTrue(metadata.getName().toLowerCase().contains("luckperms"));
    }
}
