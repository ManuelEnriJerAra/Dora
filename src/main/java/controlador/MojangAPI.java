/*
 * Fichero: MojangAPI.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Esta clase es la encargada de hacer todas las consultas a la API de Mojang.
 * */

package controlador;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MojangAPI {
    private static final String VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final String UUID_BY_USERNAME_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 60_000;
    private static final int DOWNLOAD_MAX_ATTEMPTS = 3;
    private static final String NO_SERVER_JAR_URL = "";
    private static final Map<String, String> SERVER_JAR_URL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> SERVER_JAR_SHA1_CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService BACKGROUND_REQUESTS = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "mojang-api-worker");
        thread.setDaemon(true);
        return thread;
    });

    private final ObjectMapper mapper = new ObjectMapper();

    public static final class PlayerProfile {
        private final String name;
        private final String uuid;
        private final String skinUrl;

        public PlayerProfile(String name, String uuid, String skinUrl) {
            this.name = name;
            this.uuid = uuid;
            this.skinUrl = skinUrl;
        }

        public String getName() {
            return name;
        }

        public String getUuid() {
            return uuid;
        }

        public String getSkinUrl() {
            return skinUrl;
        }
    }

    public record MinecraftVersionInfo(String id, String type) {
        public boolean isRelease() {
            return "release".equalsIgnoreCase(type);
        }

        public boolean isSnapshot() {
            return "snapshot".equalsIgnoreCase(type);
        }
    }

    public static void runBackgroundRequest(Runnable task) {
        if (task == null) {
            return;
        }
        BACKGROUND_REQUESTS.execute(task);
    }

    public static void clearCachesForTests() {
        SERVER_JAR_URL_CACHE.clear();
    }

    public JsonNode getManifest() {
        try{
            return readJsonFromUrl(VERSION_MANIFEST_URL);
        }
        catch(Exception e){
            throw new  RuntimeException(e);
        }
    }

    public List<String> obtenerListaVersiones(){
        try{
            return obtenerListaVersionesConTipo().stream()
                    .filter(MinecraftVersionInfo::isRelease)
                    .map(MinecraftVersionInfo::id)
                    .toList();
        } catch(Exception e){
            throw new  RuntimeException(e);
        }
    }

    public List<MinecraftVersionInfo> obtenerListaVersionesConTipo(){
        try{
            JsonNode manifest = getManifest();
            List<MinecraftVersionInfo> listaVersiones = new ArrayList<>();

            for(JsonNode node : manifest.get("versions")){
                String id = node.get("id").asString();
                String type = node.get("type").asString();
                if(id == null || id.isBlank() || type == null || type.isBlank()) continue;
                if(!"release".equalsIgnoreCase(type) && !"snapshot".equalsIgnoreCase(type)) continue;
                listaVersiones.add(new MinecraftVersionInfo(id, type));
            }

            return listaVersiones;
        } catch(Exception e){
            throw new  RuntimeException(e);
        }
    }

    public String obtenerUrlVersionJson(String versionId){
        try{
            JsonNode manifest = getManifest();
            for(JsonNode node : manifest.get("versions")){
                if(node.get("id").asString().equals(versionId))
                    return node.get("url").asString();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public String obtenerUrlServerJar(String versionId){
        if (versionId == null || versionId.isBlank()) {
            return null;
        }
        String cachedUrl = SERVER_JAR_URL_CACHE.get(versionId);
        if (cachedUrl != null) {
            return NO_SERVER_JAR_URL.equals(cachedUrl) ? null : cachedUrl;
        }
        try{
            String versionJsonUrl = obtenerUrlVersionJson(versionId);
            if (versionJsonUrl == null) {
                SERVER_JAR_URL_CACHE.put(versionId, NO_SERVER_JAR_URL);
                return null;
            }

            JsonNode versionJson = readJsonFromUrl(versionJsonUrl);

            JsonNode downloadsNode = versionJson.get("downloads");
            if (downloadsNode == null) {
                SERVER_JAR_URL_CACHE.put(versionId, NO_SERVER_JAR_URL);
                return null;
            }
            JsonNode serverNode = downloadsNode.get("server");
            if (serverNode == null || serverNode.get("url") == null) {
                SERVER_JAR_URL_CACHE.put(versionId, NO_SERVER_JAR_URL);
                return null;
            }

            String serverUrl = serverNode.get("url").asString();
            if (serverUrl == null || serverUrl.isBlank()) {
                SERVER_JAR_URL_CACHE.put(versionId, NO_SERVER_JAR_URL);
                return null;
            }
            JsonNode sha1Node = serverNode.get("sha1");
            if (sha1Node != null && !sha1Node.isNull()) {
                SERVER_JAR_SHA1_CACHE.put(versionId, sha1Node.asString());
            }
            SERVER_JAR_URL_CACHE.put(versionId, serverUrl);
            return serverUrl;

        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    /**
     * SHA-1 que publica Mojang para el server.jar de la versión, o {@code null} si no se conoce.
     * Se rellena al consultar {@link #obtenerUrlServerJar(String)}.
     */
    public String obtenerSha1ServerJar(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            return null;
        }
        return SERVER_JAR_SHA1_CACHE.get(versionId);
    }

    public interface DownloadProgressListener {
        void onProgress(long bytesLeidos, long totalBytes);
    }

    public void descargar(String url, File destino, DownloadProgressListener listener) {
        if (url == null || url.isBlank()) {
            throw new RuntimeException("No se ha indicado la URL de descarga.");
        }
        if (destino == null) {
            throw new RuntimeException("No se ha indicado el archivo de destino.");
        }

        Path destinationPath = destino.toPath();
        Path parent = destinationPath.getParent();
        Path tempPath = destinationPath.resolveSibling(destinationPath.getFileName() + ".part");
        IOException lastFailure = null;

        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            for (int attempt = 1; attempt <= DOWNLOAD_MAX_ATTEMPTS; attempt++) {
                try {
                    Files.deleteIfExists(tempPath);
                    downloadOnce(url, tempPath, listener);
                    Files.move(tempPath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
                    return;
                } catch (SocketTimeoutException e) {
                    lastFailure = e;
                    if (attempt == DOWNLOAD_MAX_ATTEMPTS) {
                        break;
                    }
                } catch (IOException e) {
                    lastFailure = e;
                    break;
                }
            }
        } catch (IOException e) {
            lastFailure = e;
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
        }

        throw new RuntimeException(downloadFailureMessage(lastFailure), lastFailure);
    }

    private void downloadOnce(String url, Path tempPath, DownloadProgressListener listener) throws IOException {
        URLConnection conn = openConnection(url, CONNECT_TIMEOUT_MS, DOWNLOAD_READ_TIMEOUT_MS);
        long total = conn.getContentLengthLong();

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(tempPath.toFile())) {

            byte[] buffer = new byte[8192];
            long leidos = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                leidos += n;
                if (listener != null) {
                    listener.onProgress(leidos, total);
                }
            }
        }
    }

    private static String downloadFailureMessage(IOException failure) {
        if (failure instanceof SocketTimeoutException) {
            return "La descarga desde Mojang ha tardado demasiado. Comprueba la conexión e inténtalo de nuevo.";
        }
        String detail = failure == null ? null : failure.getMessage();
        if (detail == null || detail.isBlank()) {
            return "No se ha podido descargar el archivo desde Mojang.";
        }
        return "No se ha podido descargar el archivo desde Mojang: " + detail;
    }

    public ImageIcon obtenerCabezaJugador(String username, int sizePx){
        if(username == null || username.isBlank()) return null;
        if(sizePx <= 0) return null;

        try{
            String uuid = obtenerUuidPorUsername(username);
            if(uuid == null) return null;

            String skinUrl = obtenerUrlSkinPorUuid(uuid);
            if(skinUrl == null) return null;

            BufferedImage skin = readImageFromUrl(skinUrl);
            if(skin == null) return null;

            BufferedImage head = extraerCabezaDesdeSkin(skin);

            BufferedImage out = new BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            // La cara del jugador es pixel-art: usar nearest-neighbor para evitar blur
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.drawImage(head, 0, 0, sizePx, sizePx, null);
            g.dispose();

            return new ImageIcon(out);
        } catch (Exception e){
            return null;
        }
    }

    public String obtenerUuidJugador(String username){
        if(username == null || username.isBlank()) return null;
        return obtenerUuidPorUsername(username.strip());
    }

    public String obtenerNombreJugadorExacto(String username){
        PlayerProfile profile = obtenerPerfilJugadorExacto(username);
        return profile == null ? null : profile.getName();
    }

    public PlayerProfile obtenerPerfilJugadorExacto(String username){
        if(username == null || username.isBlank()) return null;
        try{
            JsonNode node = readJsonFromUrl(UUID_BY_USERNAME_URL + username.strip());
            JsonNode nameNode = node.get("name");
            JsonNode idNode = node.get("id");
            if(nameNode == null || idNode == null) return null;
            String name = nameNode.asString();
            String uuid = idNode.asString();
            if(name == null || name.isBlank() || uuid == null || uuid.isBlank()) return null;
            String skinUrl = obtenerUrlSkinPorUuid(uuid);
            return new PlayerProfile(name, uuid, skinUrl);
        } catch (Exception e){
            return null;
        }
    }

    private String obtenerUuidPorUsername(String username){
        try{
            JsonNode node = readJsonFromUrl(UUID_BY_USERNAME_URL + username);
            JsonNode idNode = node.get("id");
            if(idNode == null) return null;
            String id = idNode.asString();
            return (id == null || id.isBlank()) ? null : id;
        } catch (Exception e){
            return null;
        }
    }

    private String obtenerUrlSkinPorUuid(String uuid){
        try{
            JsonNode profile = readJsonFromUrl(SESSION_PROFILE_URL + uuid);
            JsonNode props = profile.get("properties");
            if(props == null || !props.isArray() || props.isEmpty()) return null;

            for(JsonNode prop : props){
                if(prop == null) continue;
                if(!"textures".equals(prop.path("name").asString())) continue;
                String value = prop.path("value").asString();
                if(value == null || value.isBlank()) continue;

                byte[] decoded = Base64.getDecoder().decode(value);
                JsonNode texturesJson = mapper.readTree(decoded);
                JsonNode skinUrl = texturesJson.path("textures").path("SKIN").path("url");
                String url = skinUrl.asString();
                return (url == null || url.isBlank()) ? null : url;
            }
            return null;
        } catch (Exception e){
            return null;
        }
    }

    private BufferedImage extraerCabezaDesdeSkin(BufferedImage skin){
        // Formato skin clásico: cara en (8,8) 8x8; capa (sombrero) en (40,8) 8x8
        BufferedImage base = skin.getSubimage(8, 8, 8, 8);
        BufferedImage overlay = skin.getSubimage(40, 8, 8, 8);

        BufferedImage out = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(base, 0, 0, null);
        g.drawImage(overlay, 0, 0, null);
        g.dispose();
        return out;
    }

    private JsonNode readJsonFromUrl(String url) throws IOException {
        try (InputStream in = openConnection(url).getInputStream()) {
            return mapper.readTree(in);
        }
    }

    private BufferedImage readImageFromUrl(String url) throws IOException {
        try (InputStream in = openConnection(url).getInputStream()) {
            return ImageIO.read(in);
        }
    }

    private URLConnection openConnection(String url) throws IOException {
        return openConnection(url, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    protected URLConnection openConnection(String url, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        return connection;
    }
}
