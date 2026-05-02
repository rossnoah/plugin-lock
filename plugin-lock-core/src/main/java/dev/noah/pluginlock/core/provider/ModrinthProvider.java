package dev.noah.pluginlock.core.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginMetadata;
import dev.noah.pluginlock.core.model.PluginRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModrinthProvider {
    private static final URI DEFAULT_API_BASE = URI.create("https://api.modrinth.com/v2/");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient;
    private final URI apiBase;

    public ModrinthProvider(HttpClient httpClient) {
        this(httpClient, DEFAULT_API_BASE);
    }

    public ModrinthProvider(HttpClient httpClient, URI apiBase) {
        this.httpClient = httpClient;
        this.apiBase = apiBase;
    }

    public LockedPlugin resolve(PluginRequest request, String minecraftVersion, String loader)
            throws IOException, InterruptedException {
        JsonNode project = get("project/" + segment(request.getId()), request.getId());
        JsonNode versions = get("project/" + segment(request.getId()) + "/version"
                + "?loaders=%5B%22" + query(loader.toLowerCase(Locale.ROOT)) + "%22%5D"
                + "&game_versions=%5B%22" + query(minecraftVersion) + "%22%5D");

        JsonNode selectedVersion = selectVersion(request, versions);
        JsonNode selectedFile = selectPrimaryFile(selectedVersion);

        LockedPlugin locked = new LockedPlugin();
        locked.setId(request.getId());
        locked.setProvider("modrinth");
        locked.setName(project.path("title").asText(request.getId()));
        locked.setProjectId(project.path("id").asText());
        locked.setVersionId(selectedVersion.path("id").asText());
        locked.setVersionName(selectedVersion.path("version_number").asText(selectedVersion.path("name").asText()));
        locked.setFileName(selectedFile.path("filename").asText());
        locked.setDownloadUrl(selectedFile.path("url").asText());
        locked.setSha512(selectedFile.path("hashes").path("sha512").asText());
        locked.setSize(selectedFile.path("size").asLong());
        return locked;
    }

    public PluginMetadata fetchMetadata(String id) throws IOException, InterruptedException {
        JsonNode project = get("project/" + segment(id), id);
        PluginMetadata metadata = new PluginMetadata();
        metadata.setId(id);
        metadata.setProvider("modrinth");
        metadata.setName(project.path("title").asText(id));
        metadata.setDescription(project.path("description").asText(""));
        metadata.setDownloads(project.path("downloads").asLong());
        metadata.setAuthors(fetchAuthors(project.path("team").asText(null)));
        return metadata;
    }

    private JsonNode get(String path) throws IOException, InterruptedException {
        return get(path, null);
    }

    private JsonNode get(String path, String pluginId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBase.resolve(path))
                .header("Accept", "application/json")
                .header("User-Agent", "plugin-lock/0.1.0 (https://github.com/plugin-lock/plugin-lock)")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404 && pluginId != null) {
            throw new PluginNotFoundException("Modrinth", pluginId);
        }
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new IOException("Modrinth request failed: HTTP " + response.statusCode() + " for " + path);
        }
        return JSON.readTree(response.body());
    }

    private List<String> fetchAuthors(String teamId) throws IOException, InterruptedException {
        if (teamId == null || teamId.isBlank()) {
            return List.of();
        }
        JsonNode members = get("team/" + segment(teamId) + "/members");
        List<String> authors = new ArrayList<>();
        for (JsonNode member : members) {
            String username = member.path("user").path("username").asText("");
            if (!username.isBlank()) {
                authors.add(username);
            }
        }
        return authors;
    }

    private static JsonNode selectVersion(PluginRequest request, JsonNode versions) {
        if (!versions.isArray() || versions.isEmpty()) {
            throw new IllegalArgumentException("No compatible Modrinth versions found for " + request.getId());
        }
        if (request.getVersion() == null || request.getVersion().isBlank() || "latest".equals(request.getVersion())) {
            return versions.get(0);
        }
        for (JsonNode version : versions) {
            if (request.getVersion().equals(version.path("version_number").asText())
                    || request.getVersion().equals(version.path("id").asText())) {
                return version;
            }
        }
        throw new IllegalArgumentException("Version " + request.getVersion() + " was not found for " + request.getId());
    }

    private static JsonNode selectPrimaryFile(JsonNode version) {
        JsonNode fallback = null;
        for (JsonNode file : version.path("files")) {
            if (fallback == null) {
                fallback = file;
            }
            if (file.path("primary").asBoolean(false)) {
                return file;
            }
        }
        if (fallback == null) {
            throw new IllegalArgumentException("Version " + version.path("id").asText() + " has no files");
        }
        return fallback;
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String query(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
