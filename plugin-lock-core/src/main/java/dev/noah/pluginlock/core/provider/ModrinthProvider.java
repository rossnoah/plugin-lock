package dev.noah.pluginlock.core.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginMetadata;
import dev.noah.pluginlock.core.model.PluginRequest;
import dev.noah.pluginlock.core.model.PluginVersion;

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
        ensurePluginProject(project, request.getId());
        JsonNode versions = get("project/" + segment(request.getId()) + "/version"
                + "?loaders=%5B%22" + query(loader.toLowerCase(Locale.ROOT)) + "%22%5D");

        JsonNode selectedVersion = selectVersion(request, versions, minecraftVersion);
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
        locked.setCompatibilityWarning(compatibilityWarning(locked.getName(), selectedVersion, minecraftVersion));
        return locked;
    }

    public PluginMetadata fetchMetadata(String id) throws IOException, InterruptedException {
        JsonNode project = get("project/" + segment(id), id);
        ensurePluginProject(project, id);
        PluginMetadata metadata = new PluginMetadata();
        metadata.setId(id);
        metadata.setProvider("modrinth");
        metadata.setName(project.path("title").asText(id));
        metadata.setDescription(project.path("description").asText(""));
        metadata.setDownloads(project.path("downloads").asLong());
        metadata.setAuthors(fetchAuthors(project.path("team").asText(null)));
        return metadata;
    }

    public List<PluginMetadata> search(String query, int limit) throws IOException, InterruptedException {
        JsonNode hits = get("search?query=" + query(query)
                + "&limit=" + limit
                + "&facets=%5B%5B%22project_type%3Aplugin%22%5D%5D").path("hits");
        List<PluginMetadata> results = new ArrayList<>();
        for (JsonNode hit : hits) {
            PluginMetadata metadata = new PluginMetadata();
            metadata.setId(hit.path("slug").asText(hit.path("project_id").asText()));
            metadata.setProvider("modrinth");
            metadata.setName(hit.path("title").asText(metadata.getId()));
            metadata.setDescription(hit.path("description").asText(""));
            metadata.setDownloads(hit.path("downloads").asLong());
            results.add(metadata);
        }
        return results;
    }

    public List<PluginVersion> versions(String id, String loader) throws IOException, InterruptedException {
        JsonNode project = get("project/" + segment(id), id);
        ensurePluginProject(project, id);
        String path = "project/" + segment(id) + "/version";
        if (loader != null && !loader.isBlank()) {
            path += "?loaders=%5B%22" + query(loader.toLowerCase(Locale.ROOT)) + "%22%5D";
        }
        List<PluginVersion> result = new ArrayList<>();
        for (JsonNode version : get(path)) {
            PluginVersion info = new PluginVersion();
            info.setId(version.path("id").asText());
            info.setName(version.path("version_number").asText(version.path("name").asText()));
            info.setMinecraftVersions(textValues(version.path("game_versions")));
            info.setLoaders(textValues(version.path("loaders")));
            JsonNode file = selectPrimaryFile(version);
            info.setFileName(file.path("filename").asText(""));
            info.setDownloadable(!file.path("url").asText("").isBlank());
            result.add(info);
        }
        return result;
    }

    private static void ensurePluginProject(JsonNode project, String id) throws PluginNotFoundException {
        if (!hasPluginLoader(project)) {
            throw new PluginNotFoundException("Modrinth plugin catalog", id);
        }
    }

    private static boolean hasPluginLoader(JsonNode project) {
        JsonNode loaders = project.path("loaders");
        if (!loaders.isArray()) {
            return "plugin".equals(project.path("project_type").asText(""));
        }
        for (JsonNode loaderNode : loaders) {
            String loader = loaderNode.asText("").toLowerCase(Locale.ROOT);
            if ("paper".equals(loader)
                    || "purpur".equals(loader)
                    || "folia".equals(loader)
                    || "spigot".equals(loader)
                    || "bukkit".equals(loader)
                    || "bungeecord".equals(loader)
                    || "waterfall".equals(loader)
                    || "velocity".equals(loader)) {
                return true;
            }
        }
        return false;
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

    private static JsonNode selectVersion(PluginRequest request, JsonNode versions, String minecraftVersion) {
        if (!versions.isArray() || versions.isEmpty()) {
            throw new IllegalArgumentException("No compatible Modrinth versions found for " + request.getId());
        }
        for (JsonNode version : versions) {
            if (matchesRequestedVersion(request, version) && supports(version, minecraftVersion)) {
                return version;
            }
        }
        throw new IllegalArgumentException("No compatible Modrinth versions found for " + request.getId());
    }

    private static boolean matchesRequestedVersion(PluginRequest request, JsonNode version) {
        return request.getVersion() == null
                || request.getVersion().isBlank()
                || "latest".equals(request.getVersion())
                || request.getVersion().equals(version.path("version_number").asText())
                || request.getVersion().equals(version.path("id").asText());
    }

    private static boolean supports(JsonNode version, String minecraftVersion) {
        JsonNode gameVersions = version.path("game_versions");
        if (!gameVersions.isArray()) {
            return false;
        }
        for (JsonNode versionNode : gameVersions) {
            if (MinecraftVersions.supports(versionNode.asText(), minecraftVersion)) {
                return true;
            }
        }
        return false;
    }

    private static String compatibilityWarning(String pluginName, JsonNode version, String minecraftVersion) {
        JsonNode gameVersions = version.path("game_versions");
        if (!gameVersions.isArray()) {
            return null;
        }
        for (JsonNode versionNode : gameVersions) {
            if (MinecraftVersions.isExact(versionNode.asText(), minecraftVersion)) {
                return null;
            }
        }
        return pluginName + " does not explicitly list Minecraft " + minecraftVersion
                + "; selected an older compatible release. Check for plugin-specific breaking changes.";
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

    private static List<String> textValues(JsonNode values) {
        List<String> result = new ArrayList<>();
        if (values.isArray()) {
            for (JsonNode value : values) {
                result.add(value.asText());
            }
        }
        return result;
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String query(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
