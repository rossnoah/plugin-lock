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
            throw new IllegalArgumentException("Hangar plugin " + request.getId()
                    + " version " + selectedVersion.path("name").asText()
                    + " supports Minecraft " + minecraftVersion
                    + " but has no " + platform + " download");
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
        locked.setCompatibilityWarning(compatibilityWarning(locked.getName(), selectedVersion, platform, minecraftVersion));
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
        metadata.setAuthors(fetchAuthors(project));
        return metadata;
    }

    public List<PluginMetadata> search(String query, int limit) throws IOException, InterruptedException {
        JsonNode result = get("projects?query=" + query(query) + "&limit=" + limit).path("result");
        java.util.ArrayList<PluginMetadata> results = new java.util.ArrayList<>();
        for (JsonNode project : result) {
            PluginMetadata metadata = new PluginMetadata();
            metadata.setId(project.path("namespace").path("slug").asText(project.path("name").asText()));
            metadata.setProvider("hangar");
            metadata.setName(project.path("name").asText(metadata.getId()));
            metadata.setDescription(project.path("description").asText(""));
            metadata.setDownloads(project.path("stats").path("downloads").asLong());
            metadata.setAuthors(fetchAuthors(project));
            results.add(metadata);
        }
        return results;
    }

    public List<PluginVersion> versions(String id, String loader) throws IOException, InterruptedException {
        JsonNode project = project(id);
        String platform = loader == null || loader.isBlank() ? null : platform(loader);
        java.util.ArrayList<PluginVersion> result = new java.util.ArrayList<>();
        for (JsonNode version : get(projectPath(project) + "/versions?limit=100").path("result")) {
            PluginVersion info = new PluginVersion();
            info.setId(version.path("name").asText());
            info.setName(version.path("name").asText());
            if (platform == null) {
                info.setLoaders(textValues(version.path("platformDependencies").fieldNames()));
                info.setMinecraftVersions(allPlatformVersions(version));
            } else {
                info.setLoaders(List.of(platform.toLowerCase(Locale.ROOT)));
                info.setMinecraftVersions(textValues(version.path("platformDependencies").path(platform)));
                JsonNode download = version.path("downloads").path(platform);
                info.setFileName(download.path("fileInfo").path("name").asText(""));
                info.setDownloadable(!download.path("downloadUrl").asText("").isBlank());
            }
            result.add(info);
        }
        return result;
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
        boolean foundRequestedVersion = false;
        boolean foundCompatibleVersion = false;
        boolean foundMissingDirectDownload = false;
        String externalDownloadUrl = null;
        for (JsonNode version : versions) {
            if (!matchesRequestedVersion(request, version)) {
                continue;
            }
            foundRequestedVersion = true;
            if (!supports(version, platform, minecraftVersion)) {
                continue;
            }
            foundCompatibleVersion = true;
            if (!hasDownload(version, platform)) {
                foundMissingDirectDownload = true;
                if (externalDownloadUrl == null) {
                    externalDownloadUrl = externalDownloadUrl(version, platform);
                }
                continue;
            }
            if (supports(version, platform, minecraftVersion) && hasDownload(version, platform)) {
                return version;
            }
        }
        throw new IllegalArgumentException(hangarResolutionFailure(
                request, loader, minecraftVersion, platform, foundRequestedVersion,
                foundCompatibleVersion, foundMissingDirectDownload, externalDownloadUrl));
    }

    private static boolean matchesRequestedVersion(PluginRequest request, JsonNode version) {
        return request.getVersion() == null
                || request.getVersion().isBlank()
                || "latest".equals(request.getVersion())
                || request.getVersion().equals(version.path("name").asText());
    }

    private static String hangarResolutionFailure(
            PluginRequest request,
            String loader,
            String minecraftVersion,
            String platform,
            boolean foundRequestedVersion,
            boolean foundCompatibleVersion,
            boolean foundMissingDirectDownload,
            String externalDownloadUrl) {
        String target = request.getId() + " on " + loader.toLowerCase(Locale.ROOT) + " Minecraft " + minecraftVersion;
        if (!foundRequestedVersion && request.getVersion() != null && !request.getVersion().isBlank()
                && !"latest".equals(request.getVersion())) {
            return "Hangar plugin " + request.getId() + " has no version named " + request.getVersion();
        }
        if (!foundCompatibleVersion) {
            return "No Hangar version found for " + target + "; candidate versions do not list a compatible Minecraft version";
        }
        if (externalDownloadUrl != null && !externalDownloadUrl.isBlank()) {
            return "Hangar plugin " + request.getId() + " uses an external download for "
                    + loader.toLowerCase(Locale.ROOT) + " Minecraft " + minecraftVersion + ": " + externalDownloadUrl
                    + ". plugin-lock needs a direct Hangar-hosted jar URL to install it automatically.";
        }
        if (foundMissingDirectDownload) {
            return "No downloadable Hangar version found for " + target
                    + "; compatible candidate versions do not provide a direct " + platform + " jar download";
        }
        return "No downloadable Hangar version found for " + target;
    }

    private static boolean supports(JsonNode version, String platform, String minecraftVersion) {
        JsonNode platformVersions = version.path("platformDependencies").path(platform);
        if (!platformVersions.isArray()) {
            return false;
        }
        for (JsonNode versionNode : platformVersions) {
            if (MinecraftVersions.supports(versionNode.asText(), minecraftVersion)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDownload(JsonNode version, String platform) {
        JsonNode download = version.path("downloads").path(platform);
        return !download.isMissingNode() && !download.path("downloadUrl").asText("").isBlank();
    }

    private static String externalDownloadUrl(JsonNode version, String platform) {
        JsonNode download = version.path("downloads").path(platform);
        if (download.isMissingNode()) {
            return null;
        }
        String externalUrl = download.path("externalUrl").asText("");
        return externalUrl.isBlank() ? null : externalUrl;
    }

    private static boolean supportsExact(JsonNode version, String platform, String minecraftVersion) {
        JsonNode platformVersions = version.path("platformDependencies").path(platform);
        if (!platformVersions.isArray()) {
            return false;
        }
        for (JsonNode versionNode : platformVersions) {
            if (MinecraftVersions.isExact(versionNode.asText(), minecraftVersion)) {
                return true;
            }
        }
        return false;
    }

    private static String compatibilityWarning(String pluginName, JsonNode version, String platform, String minecraftVersion) {
        if (supportsExact(version, platform, minecraftVersion)) {
            return null;
        }
        return pluginName + " does not explicitly list Minecraft " + minecraftVersion
                + "; selected an older compatible release. Check for plugin-specific breaking changes.";
    }

    private List<String> fetchAuthors(JsonNode project) throws IOException, InterruptedException {
        JsonNode members = get(projectPath(project) + "/members?limit=100").path("result");
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        for (JsonNode member : members) {
            String username = member.path("user").asText("");
            if (!username.isBlank()) {
                names.add(username);
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

    private static List<String> textValues(java.util.Iterator<String> values) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        while (values.hasNext()) {
            result.add(values.next().toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private static List<String> textValues(JsonNode values) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        if (values.isArray()) {
            for (JsonNode value : values) {
                result.add(value.asText());
            }
        }
        return result;
    }

    private static List<String> allPlatformVersions(JsonNode version) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        JsonNode dependencies = version.path("platformDependencies");
        dependencies.fieldNames().forEachRemaining(platform -> {
            for (JsonNode value : dependencies.path(platform)) {
                String text = value.asText();
                if (!result.contains(text)) {
                    result.add(text);
                }
            }
        });
        return result;
    }
}
