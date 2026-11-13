package controlador.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class PaperDownloadsClient {
    private static final String PROJECT = "paper";
    private static final String BASE_URL = "https://fill.papermc.io/v3/projects/" + PROJECT;
    private static final String LATEST_STABLE_BUILD = "latest-stable";
    private static final String LATEST_UNSTABLE_BUILD = "latest-unstable";
    private static final String LEGACY_LATEST_BUILD = "latest";
    private final PlatformHttpClient httpClient;

    PaperDownloadsClient() {
        this(new UrlConnectionPlatformHttpClient());
    }

    PaperDownloadsClient(PlatformHttpClient httpClient) {
        this.httpClient = new CachedPlatformHttpClient(httpClient);
    }

    List<ServerCreationOption> listCreationOptions() throws IOException {
        List<String> versions = listVersions();
        List<ServerCreationOption> options = new ArrayList<>();
        for (String minecraftVersion : versions) {
            String versionType = ServerCreationOption.versionTypeFromText(minecraftVersion);
            boolean stableMinecraftVersion = ServerCreationOption.VERSION_TYPE_RELEASE.equals(versionType);
            options.add(new ServerCreationOption(
                    ServerPlatform.PAPER,
                    minecraftVersion,
                    stableMinecraftVersion ? LATEST_STABLE_BUILD : LATEST_UNSTABLE_BUILD,
                    "Minecraft " + minecraftVersion + (stableMinecraftVersion ? " (Paper latest)" : " (Paper unstable)"),
                    "paper-" + minecraftVersion + "-server",
                    versionType
            ));
        }
        return options;
    }

    String downloadUrl(String minecraftVersion, String buildId) throws IOException {
        return resolveBuild(minecraftVersion, buildId).downloadUrl();
    }

    PaperBuild resolveBuild(String minecraftVersion, String buildId) throws IOException {
        if (buildId == null || buildId.isBlank()
                || LATEST_STABLE_BUILD.equalsIgnoreCase(buildId)
                || LEGACY_LATEST_BUILD.equalsIgnoreCase(buildId)) {
            PaperBuild build = latestStableBuild(minecraftVersion);
            if (build == null) {
                build = latestDownloadableBuild(minecraftVersion);
            }
            if (build == null) {
                throw new IOException("No se ha encontrado una build de Paper para Minecraft " + minecraftVersion + ".");
            }
            return build;
        }
        if (LATEST_UNSTABLE_BUILD.equalsIgnoreCase(buildId)) {
            PaperBuild build = latestDownloadableBuild(minecraftVersion);
            if (build == null) {
                throw new IOException("No se ha encontrado una build de Paper para Minecraft " + minecraftVersion + ".");
            }
            return build;
        }
        JsonArray builds = httpClient.getJson(BASE_URL + "/versions/" + minecraftVersion + "/builds").getAsJsonArray();
        for (JsonElement element : builds) {
            JsonObject object = element.getAsJsonObject();
            String id = stringValue(object, "id");
            if (buildId.equals(id)) {
                String url = downloadUrlFromBuild(object);
                if (url != null && !url.isBlank()) {
                    return new PaperBuild(id, url, sha256FromBuild(object));
                }
            }
        }
        throw new IOException("No se ha encontrado la build " + buildId + " de Paper para Minecraft " + minecraftVersion + ".");
    }

    private List<String> listVersions() throws IOException {
        JsonObject project = httpClient.getJson(BASE_URL).getAsJsonObject();
        JsonObject versions = project.getAsJsonObject("versions");
        List<String> values = new ArrayList<>();
        if (versions == null) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : versions.entrySet()) {
            if (!entry.getValue().isJsonArray()) {
                continue;
            }
            JsonArray array = entry.getValue().getAsJsonArray();
            for (JsonElement version : array) {
                if (version != null && version.isJsonPrimitive()) {
                    values.add(version.getAsString());
                }
            }
        }
        values.sort(VersionStringComparator.minecraftVersionsDescending());
        return values;
    }

    private PaperBuild latestStableBuild(String minecraftVersion) throws IOException {
        JsonArray builds = httpClient.getJson(BASE_URL + "/versions/" + minecraftVersion + "/builds").getAsJsonArray();
        for (JsonElement element : builds) {
            JsonObject object = element.getAsJsonObject();
            String channel = stringValue(object, "channel");
            if (!"STABLE".equalsIgnoreCase(channel)) {
                continue;
            }
            String url = downloadUrlFromBuild(object);
            String id = stringValue(object, "id");
            if (url != null && !url.isBlank() && id != null && !id.isBlank()) {
                return new PaperBuild(id, url, sha256FromBuild(object));
            }
        }
        return null;
    }

    private PaperBuild latestDownloadableBuild(String minecraftVersion) throws IOException {
        JsonArray builds = httpClient.getJson(BASE_URL + "/versions/" + minecraftVersion + "/builds").getAsJsonArray();
        for (JsonElement element : builds) {
            JsonObject object = element.getAsJsonObject();
            String url = downloadUrlFromBuild(object);
            String id = stringValue(object, "id");
            if (url != null && !url.isBlank() && id != null && !id.isBlank()) {
                return new PaperBuild(id, url, sha256FromBuild(object));
            }
        }
        return null;
    }

    private String downloadUrlFromBuild(JsonObject build) {
        JsonObject downloads = build == null ? null : build.getAsJsonObject("downloads");
        JsonObject serverDefault = downloads == null ? null : downloads.getAsJsonObject("server:default");
        return serverDefault == null ? null : stringValue(serverDefault, "url");
    }

    private String sha256FromBuild(JsonObject build) {
        JsonObject downloads = build == null ? null : build.getAsJsonObject("downloads");
        JsonObject serverDefault = downloads == null ? null : downloads.getAsJsonObject("server:default");
        JsonObject checksums = serverDefault == null ? null : serverDefault.getAsJsonObject("checksums");
        return checksums == null ? null : stringValue(checksums, "sha256");
    }

    private String stringValue(JsonObject object, String field) {
        JsonElement element = object == null ? null : object.get(field);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    record PaperBuild(String id, String downloadUrl, String sha256) {
        PaperBuild(String id, String downloadUrl) {
            this(id, downloadUrl, null);
        }
    }
}
