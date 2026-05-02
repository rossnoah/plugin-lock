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
import java.util.List;
import java.util.Locale;

public final class HangarProvider {
    private static final URI DEFAULT_API_BASE = URI.create("https://hangar.papermc.io/api/v1/");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient;
    private final URI apiBase;

    public HangarProvider(HttpClient httpClient) {
        this(httpClient, DEFAULT_API_BASE);
    }

    public HangarProvider(HttpClient httpClient, URI apiBase) {
        this.httpClient = httpClient;
        this.apiBase = apiBase;
    }

    public LockedPlugin resolve(PluginRequest request, String minecraftVersion, String loader)
            throws IOException, InterruptedException {
        JsonNode project = project(request.getId());
        JsonNode versions = get(projectPath(project) + "/versions?limit=100");
        JsonNode selectedVersion = selectVersion(request, versions.path("result"), minecraftVersion, loader);
        String platform = platform(loader);
        JsonNode download = selectedVersion.path("downloads").path(platform);
        if (download.isMissingNode() || download.path("downloadUrl").asText("").isBlank()) {
            throw new IllegalArgumentException("Version " + selectedVersion.path("name").asText() + " has no " + platform + " download");
        }

        LockedPlugin locked = new LockedPlugin();
        locked.setId(project.path("namespace").path("slug").asText(request.getId()));
        locked.setProvider("hangar");
        locked.setName(project.path("name").asText(request.getId()));
        locked.setProjectId(project.path("id").asText());
        locked.setVersionId(selectedVersion.path("name").asText());
        locked.setVersionName(selectedVersion.path("name").asText());
        locked.setFileName(download.path("fileInfo").path("name").asText());
        locked.setDownloadUrl(download.path("downloadUrl").asText());
        locked.setSha256(download.path("fileInfo").path("sha256Hash").asText());
        locked.setSize(download.path("fileInfo").path("sizeBytes").asLong());
        return locked;
    }

    public PluginMetadata fetchMetadata(String id) throws IOException, InterruptedException {
        JsonNode project = project(id);
        PluginMetadata metadata = new PluginMetadata();
        metadata.setId(project.path("namespace").path("slug").asText(id));
        metadata.setProvider("hangar");
        metadata.setName(project.path("name").asText(id));
        metadata.setDescription(project.path("description").asText(""));
        metadata.setDownloads(project.path("stats").path("downloads").asLong());
        metadata.setAuthors(memberNames(project));
        return metadata;
    }

    private JsonNode project(String id) throws IOException, InterruptedException {
        try {
            return get("projects/" + projectPath(id), id);
        } catch (PluginNotFoundException exception) {
            JsonNode result = get("projects?query=" + query(id) + "&limit=10").path("result");
            for (JsonNode project : result) {
                if (id.equalsIgnoreCase(project.path("name").asText())
                        || id.equalsIgnoreCase(project.path("namespace").path("slug").asText())) {
                    return project;
                }
            }
            throw exception;
        }
    }

    private JsonNode get(String path) throws IOException, InterruptedException {
        return get(path, null);
    }

    private JsonNode get(String path, String pluginId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBase.resolve(path))
                .header("Accept", "application/json")
                .header("User-Agent", "plugin-lock/0.1.0")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404 && pluginId != null) {
            throw new PluginNotFoundException("Hangar", pluginId);
        }
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new IOException("Hangar request failed: HTTP " + response.statusCode() + " for " + path);
        }
        return JSON.readTree(response.body());
    }

    private static JsonNode selectVersion(PluginRequest request, JsonNode versions, String minecraftVersion, String loader) {
        if (!versions.isArray() || versions.isEmpty()) {
            throw new IllegalArgumentException("No Hangar versions found for " + request.getId());
        }
        String platform = platform(loader);
        for (JsonNode version : versions) {
            if (!matchesRequestedVersion(request, version)) {
                continue;
            }
            if (supports(version, platform, minecraftVersion)) {
                return version;
            }
        }
        throw new IllegalArgumentException("No compatible Hangar versions found for " + request.getId());
    }

    private static boolean matchesRequestedVersion(PluginRequest request, JsonNode version) {
        return request.getVersion() == null
                || request.getVersion().isBlank()
                || "latest".equals(request.getVersion())
                || request.getVersion().equals(version.path("name").asText());
    }

    private static boolean supports(JsonNode version, String platform, String minecraftVersion) {
        JsonNode platformVersions = version.path("platformDependencies").path(platform);
        if (!platformVersions.isArray()) {
            return false;
        }
        for (JsonNode versionNode : platformVersions) {
            if (minecraftVersion.equals(versionNode.asText())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> memberNames(JsonNode project) {
        JsonNode members = project.path("memberNames");
        if (!members.isArray()) {
            return List.of();
        }
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        for (JsonNode member : members) {
            if (!member.asText("").isBlank()) {
                names.add(member.asText());
            }
        }
        return names;
    }

    private static String projectPath(JsonNode project) {
        return "projects/" + segment(project.path("namespace").path("owner").asText())
                + "/" + segment(project.path("namespace").path("slug").asText());
    }

    private static String projectPath(String id) {
        String[] parts = id.split("/", 2);
        if (parts.length == 2) {
            return segment(parts[0]) + "/" + segment(parts[1]);
        }
        return segment(id);
    }

    private static String platform(String loader) {
        return loader.toUpperCase(Locale.ROOT);
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String query(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
