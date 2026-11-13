package controlador.platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Comprueba que un archivo descargado coincide con el hash publicado por su proveedor.
 */
final class DownloadChecksum {

    private DownloadChecksum() {
    }

    /**
     * Si {@code expectedHex} es nulo o vacío no se comprueba nada (el proveedor no publica hash).
     * Si no coincide, borra el archivo para que no se reutilice en el siguiente intento y lanza
     * una {@link IOException}.
     *
     * @param algorithm nombre de {@link MessageDigest}: "SHA-1", "SHA-256", "MD5"...
     */
    static void verifyOrDelete(Path file, String algorithm, String expectedHex, String description) throws IOException {
        if (expectedHex == null || expectedHex.isBlank()) {
            return;
        }
        String actual;
        try {
            actual = hash(file, algorithm);
        } catch (IOException e) {
            Files.deleteIfExists(file);
            throw e;
        }
        if (!actual.equalsIgnoreCase(expectedHex.trim())) {
            Files.deleteIfExists(file);
            throw new IOException("La descarga de " + description + " está dañada o no es la esperada"
                    + " (el " + algorithm + " no coincide). Vuelve a intentarlo.");
        }
    }

    static String hash(Path file, String algorithm) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm.toUpperCase(Locale.ROOT));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Algoritmo de hash no disponible: " + algorithm, e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
