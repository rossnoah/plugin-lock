package dev.noah.pluginlock.core.provider;

import dev.noah.pluginlock.core.TestHttpServer;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginMetadata;
import dev.noah.pluginlock.core.model.PluginRequest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HangarProviderIntegrationTest {
    @Test
    void resolvesPluginAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.json("/projects/Owner/Demo", projectJson("Owner", "Demo", "Demo Plugin"));
            server.json("/projects/Owner/Demo/versions?limit=100", """
                    {
                      "pagination":{"count":1,"limit":100,"offset":0},
                      "result":[{
                        "name":"1.0.0",
                        "downloads":{
                          "PAPER":{
                            "fileInfo":{"name":"Demo.jar","sizeBytes":42,"sha256Hash":"abc123"},
                            "downloadUrl":"https://example.test/Demo.jar"
                          }
                        },
                        "platformDependencies":{"PAPER":["1.21.4"]}
                      }]
                    }
                    """);

            LockedPlugin plugin = new HangarProvider(HttpClient.newHttpClient(), server.baseUri())
                    .resolve(new PluginRequest("Owner/Demo", "hangar", "latest"), "1.21.4", "paper");

            assertEquals("Demo", plugin.getId());
            assertEquals("hangar", plugin.getProvider());
            assertEquals("Demo Plugin", plugin.getName());
            assertEquals("1.0.0", plugin.getVersionName());
            assertEquals("Demo.jar", plugin.getFileName());
            assertEquals("abc123", plugin.getSha256());
        }
    }

    @Test
    void fallsBackToSearchWhenOwnerlessProjectPathIsMissing() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.response("/projects/Demo", 404, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
            server.json("/projects?query=Demo&limit=10", """
                    {
                      "pagination":{"count":1,"limit":10,"offset":0},
                      "result":[%s]
                    }
                    """.formatted(projectJson("Owner", "Demo", "Demo Plugin")));

            PluginMetadata metadata = new HangarProvider(HttpClient.newHttpClient(), server.baseUri())
                    .fetchMetadata("Demo");

            assertEquals("Demo", metadata.getId());
            assertEquals("Demo Plugin", metadata.getName());
            assertEquals(java.util.List.of("Owner", "Maintainer"), metadata.getAuthors());
        }
    }

    @Test
    void choosesRequestedVersionAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.json("/projects/Owner/Demo", projectJson("Owner", "Demo", "Demo Plugin"));
            server.json("/projects/Owner/Demo/versions?limit=100", """
                    {
                      "pagination":{"count":2,"limit":100,"offset":0},
                      "result":[
                        {
                          "name":"2.0.0",
                          "downloads":{"PAPER":{"fileInfo":{"name":"new.jar","sizeBytes":1,"sha256Hash":"new"},"downloadUrl":"https://example.test/new.jar"}},
                          "platformDependencies":{"PAPER":["1.21.4"]}
                        },
                        {
                          "name":"1.0.0",
                          "downloads":{"PAPER":{"fileInfo":{"name":"old.jar","sizeBytes":1,"sha256Hash":"old"},"downloadUrl":"https://example.test/old.jar"}},
                          "platformDependencies":{"PAPER":["1.21.4"]}
                        }
                      ]
                    }
                    """);

            LockedPlugin plugin = new HangarProvider(HttpClient.newHttpClient(), server.baseUri())
                    .resolve(new PluginRequest("Owner/Demo", "hangar", "1.0.0"), "1.21.4", "paper");

            assertEquals("1.0.0", plugin.getVersionName());
            assertEquals("old.jar", plugin.getFileName());
        }
    }

    @Test
    void skipsCompatibleVersionWithoutPlatformDownloadAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.json("/projects/Owner/Demo", projectJson("Owner", "Demo", "Demo Plugin"));
            server.json("/projects/Owner/Demo/versions?limit=100", """
                    {
                      "pagination":{"count":2,"limit":100,"offset":0},
                      "result":[
                        {
                          "name":"2.0.0",
                          "downloads":{"PAPER":{"fileInfo":null,"externalUrl":"https://example.test/releases","downloadUrl":null}},
                          "platformDependencies":{"PAPER":["1.21.4"]}
                        },
                        {
                          "name":"1.0.0",
                          "downloads":{"PAPER":{"fileInfo":{"name":"paper.jar","sizeBytes":1,"sha256Hash":"paper"},"downloadUrl":"https://example.test/paper.jar"}},
                          "platformDependencies":{"PAPER":["1.21.4"]}
                        }
                      ]
                    }
                    """);

            LockedPlugin plugin = new HangarProvider(HttpClient.newHttpClient(), server.baseUri())
                    .resolve(new PluginRequest("Owner/Demo", "hangar", "latest"), "1.21.4", "paper");

            assertEquals("1.0.0", plugin.getVersionName());
            assertEquals("paper.jar", plugin.getFileName());
        }
    }

    @Test
    void reportsExternalDownloadWhenNoDirectDownloadExistsAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.json("/projects/Owner/Demo", projectJson("Owner", "Demo", "Demo Plugin"));
            server.json("/projects/Owner/Demo/versions?limit=100", """
                    {
                      "pagination":{"count":1,"limit":100,"offset":0},
                      "result":[{
                        "name":"2.0.0",
                        "downloads":{"PAPER":{"fileInfo":null,"externalUrl":"https://example.test/releases","downloadUrl":null}},
                        "platformDependencies":{"PAPER":["1.21.4"]}
                      }]
                    }
                    """);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    new HangarProvider(HttpClient.newHttpClient(), server.baseUri())
                            .resolve(new PluginRequest("Owner/Demo", "hangar", "latest"), "1.21.4", "paper"));

            assertTrue(exception.getMessage().contains("Owner/Demo"));
            assertTrue(exception.getMessage().contains("uses an external download"));
            assertTrue(exception.getMessage().contains("https://example.test/releases"));
        }
    }

    @Test
    void reportsMissingCompatibleVersionsAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.json("/projects/Owner/Demo", projectJson("Owner", "Demo", "Demo Plugin"));
            server.json("/projects/Owner/Demo/versions?limit=100", """
                    {
                      "pagination":{"count":1,"limit":100,"offset":0},
                      "result":[{
                        "name":"1.0.0",
                        "downloads":{"PAPER":{"fileInfo":{"name":"Demo.jar","sizeBytes":1,"sha256Hash":"hash"},"downloadUrl":"https://example.test/Demo.jar"}},
                        "platformDependencies":{"PAPER":["1.21.5"]}
                      }]
                    }
                    """);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    new HangarProvider(HttpClient.newHttpClient(), server.baseUri())
                            .resolve(new PluginRequest("Owner/Demo", "hangar", "latest"), "1.21.4", "paper"));

            assertTrue(exception.getMessage().contains("do not list a compatible Minecraft version"));
        }
    }

    @Test
    void resolvesOlderVersionOnNewerServerWithWarningAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.json("/projects/Owner/Demo", projectJson("Owner", "Demo", "Demo Plugin"));
            server.json("/projects/Owner/Demo/versions?limit=100", """
                    {
                      "pagination":{"count":1,"limit":100,"offset":0},
                      "result":[{
                        "name":"1.0.0",
                        "downloads":{"PAPER":{"fileInfo":{"name":"Demo.jar","sizeBytes":1,"sha256Hash":"hash"},"downloadUrl":"https://example.test/Demo.jar"}},
                        "platformDependencies":{"PAPER":["1.21.1"]}
                      }]
                    }
                    """);

            LockedPlugin plugin = new HangarProvider(HttpClient.newHttpClient(), server.baseUri())
                    .resolve(new PluginRequest("Owner/Demo", "hangar", "latest"), "1.21.4", "paper");

            assertEquals("1.0.0", plugin.getVersionName());
            assertTrue(plugin.getCompatibilityWarning().contains("does not explicitly list Minecraft 1.21.4"));
        }
    }

    @Test
    void doesNotCrossMinecraft113BoundaryAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.json("/projects/Owner/Demo", projectJson("Owner", "Demo", "Demo Plugin"));
            server.json("/projects/Owner/Demo/versions?limit=100", """
                    {
                      "pagination":{"count":1,"limit":100,"offset":0},
                      "result":[{
                        "name":"1.0.0",
                        "downloads":{"PAPER":{"fileInfo":{"name":"Demo.jar","sizeBytes":1,"sha256Hash":"hash"},"downloadUrl":"https://example.test/Demo.jar"}},
                        "platformDependencies":{"PAPER":["1.12.2"]}
                      }]
                    }
                    """);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    new HangarProvider(HttpClient.newHttpClient(), server.baseUri())
                            .resolve(new PluginRequest("Owner/Demo", "hangar", "latest"), "1.13", "paper"));

            assertTrue(exception.getMessage().contains("do not list a compatible Minecraft version"));
        }
    }

    @Test
    void reportsPluginNotFoundAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.response("/projects/Missing", 404, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
            server.json("/projects?query=Missing&limit=10", """
                    {"pagination":{"count":0,"limit":10,"offset":0},"result":[]}
                    """);

            PluginNotFoundException exception = assertThrows(PluginNotFoundException.class, () ->
                    new HangarProvider(HttpClient.newHttpClient(), server.baseUri()).fetchMetadata("Missing"));

            assertTrue(exception.getMessage().contains("Plugin not found on Hangar: Missing"));
        }
    }

    @Test
    void searchesProjectsAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.json("/projects?query=chunky&limit=2", """
                    {
                      "pagination":{"count":2,"limit":2,"offset":0},
                      "result":[
                        %s,
                        %s
                      ]
                    }
                    """.formatted(projectJson("pop4959", "Chunky", "Chunky"), projectJson("Other", "ChunkyBorder", "ChunkyBorder")));

            java.util.List<PluginMetadata> results = new HangarProvider(HttpClient.newHttpClient(), server.baseUri())
                    .search("chunky", 2);

            assertEquals(2, results.size());
            assertEquals("hangar", results.getFirst().getProvider());
            assertEquals("Chunky", results.getFirst().getId());
            assertEquals("Chunky", results.getFirst().getName());
            assertEquals("A demo plugin", results.getFirst().getDescription());
            assertEquals(321, results.getFirst().getDownloads());
            assertEquals(java.util.List.of("Owner", "Maintainer"), results.getFirst().getAuthors());
        }
    }

    @Test
    void searchUrlEncodesQueriesAgainstFakeApi() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.json("/projects?query=better+rtp&limit=1", """
                    {"pagination":{"count":0,"limit":1,"offset":0},"result":[]}
                    """);

            java.util.List<PluginMetadata> results = new HangarProvider(HttpClient.newHttpClient(), server.baseUri())
                    .search("better rtp", 1);

            assertTrue(results.isEmpty());
        }
    }

    private static String projectJson(String owner, String slug, String name) {
        return """
                {
                  "id":123,
                  "name":"%s",
                  "namespace":{"owner":"%s","slug":"%s"},
                  "stats":{"downloads":321},
                  "description":"A demo plugin",
                  "memberNames":["Owner","Maintainer"]
                }
                """.formatted(name, owner, slug);
    }
}
