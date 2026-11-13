package controlador.extensions;

import controlador.platform.FileDownloader;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Locale;

public final class ExtensionArtifactDownloader implements FileDownloader {
    private static final String USER_AGENT = "Dora/alpha (+https://github.com/ManuJara/Dora)";

    private final HttpClient client;

    public ExtensionArtifactDownloader() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build());
    }

    ExtensionArtifactDownloader(HttpClient client) {
        this.client = client == null ? HttpClient.newHttpClient() : client;
    }

    @Override
    public void download(String url, File destination) throws IOException {
        if (url == null || url.isBlank()) {
            throw new IOException("La URL de descarga de la extensión está vacía.");
        }
        if (destination == null) {
            throw new IOException("No se ha indicado el destino de la descarga.");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw new IOException("La URL de descarga de la extensión no es válida.", ex);
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(45))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/java-archive, application/octet-stream, */*")
                .GET()
                .build();

        HttpResponse<byte[]> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("La descarga de la extensión ha sido interrumpida.", ex);
        } catch (IllegalArgumentException ex) {
            throw new IOException("No se ha podido construir la petición de descarga.", ex);
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("La descarga de la extensión ha devuelto HTTP " + statusCode + ".");
        }
        byte[] body = response.body() == null ? new byte[0] : response.body();
        rejectTextualErrorBody(response, body);
        if (destination.toPath().getParent() != null) {
            Files.createDirectories(destination.toPath().getParent());
        }
        Files.write(destination.toPath(), body);
    }

    private void rejectTextualErrorBody(HttpResponse<byte[]> response, byte[] body) throws IOException {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        boolean declaredText = normalizedContentType.startsWith("text/")
                || normalizedContentType.contains("application/json")
                || normalizedContentType.contains("application/problem+json")
                || normalizedContentType.contains("application/xml");
        if (declaredText || looksLikeHtmlJsonOrText(body)) {
            throw new IOException("La descarga remota no parece ser un archivo JAR.");
        }
    }

    private boolean looksLikeHtmlJsonOrText(byte[] body) {
        if (body == null || body.length == 0) {
            return true;
        }
        int offset = 0;
        while (offset < body.length && offset < 64 && Character.isWhitespace((char) (body[offset] & 0xff))) {
            offset++;
        }
        if (offset >= body.length) {
            return true;
        }
        int first = body[offset] & 0xff;
        if (first == '<' || first == '{' || first == '[') {
            return true;
        }
        return body.length >= offset + 5
                && Character.toLowerCase((char) first) == 'e'
                && Character.toLowerCase((char) (body[offset + 1] & 0xff)) == 'r'
                && Character.toLowerCase((char) (body[offset + 2] & 0xff)) == 'r'
                && Character.toLowerCase((char) (body[offset + 3] & 0xff)) == 'o'
                && Character.toLowerCase((char) (body[offset + 4] & 0xff)) == 'r';
    }
}
