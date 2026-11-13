package controlador.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class PurpurDownloadsClient {
    private static final String BASE_URL = "https://api.purpurmc.org/v2/purpur";
    private static final String LATEST_BUILD = "latest";
    private final PlatformHttpClient httpClient;

    PurpurDownloadsClient() {
        this(new UrlConnectionPlatformHttpClient());
    }

    PurpurDownloadsClient(PlatformHttpClient httpClient) {
        this.httpClient = new CachedPlatformHttpClient(httpClient);
    }

    List<ServerCreationOption> listCreationOptions() throws IOException {
        JsonObject project = httpClient.getJson(BASE_URL).getAsJsonObject();
        JsonArray versions = project.getAsJsonArray("versions");
        List<ServerCreationOption> options = new ArrayList<>();
        if (versions == null) {
            return options;
        }
        List<String> sortedVersions = new ArrayList<>();
        for (JsonElement element : versions) {
            if (element != null && element.isJsonPrimitive()) {
                sortedVersions.add(element.getAsString());
            }
        }
        sortedVersions.sort(VersionStringComparator.minecraftVersionsDescending());
        for (String minecraftVersion : sortedVersions) {
            String versionType = ServerCreationOption.versionTypeFromText(minecraftVersion);
            boolean stableMinecraftVersion = ServerCreationOption.VERSION_TYPE_RELEASE.equals(versionType);
            options.add(new ServerCreationOption(
                    ServerPlatform.PURPUR,
                    minecraftVersion,
                    LATEST_BUILD,
                    "Minecraft " + minecraftVersion + (stableMinecraftVersion ? " (Purpur latest)" : " (Purpur unstable)"),
                    "purpur-" + minecraftVersion + "-server",
                    versionType
            ));
        }
        return options;
    }

    String downloadUrl(String minecraftVersion, String buildId) throws IOException {
        String resolvedBuild = buildId == null || buildId.isBlank() ? latestBuild(minecraftVersion) : buildId;
        if (resolvedBuild == null || resolvedBuild.isBlank()) {
            throw new IOException("No se ha encontrado una build de Purpur para Minecraft " + minecraftVersion + ".");
        }
        return BASE_URL + "/" + minecraftVersion + "/" + resolvedBuild + "/download";
    }

    /**
     * MD5 publicado por Purpur para la build, o {@code null} si no se puede consultar
     * (en ese caso la descarga no se comprueba).
     */
    String md5(String minecraftVersion, String buildId) {
        String build = buildId == null || buildId.isBlank() ? LATEST_BUILD : buildId;
        try {
            JsonElement info = httpClient.getJson(BASE_URL + "/" + minecraftVersion + "/" + build);
            if (info == null || !info.isJsonObject()) {
                return null;
            }
            JsonElement md5 = info.getAsJsonObject().get("md5");
            return md5 == null || md5.isJsonNull() ? null : md5.getAsString();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private String latestBuild(String minecraftVersion) throws IOException {
        JsonObject version = httpClient.getJson(BASE_URL + "/" + minecraftVersion).getAsJsonObject();
        JsonObject builds = version.getAsJsonObject("builds");
        if (builds == null) {
            return null;
        }
        JsonElement latest = builds.get("latest");
        if (latest != null && !latest.isJsonNull()) {
            return latest.getAsString();
        }
        JsonArray all = builds.getAsJsonArray("all");
        return all == null || all.isEmpty() ? null : all.get(all.size() - 1).getAsString();
    }
}
