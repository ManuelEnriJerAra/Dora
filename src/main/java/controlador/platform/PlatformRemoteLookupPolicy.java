package controlador.platform;

import com.google.gson.JsonElement;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class PlatformRemoteLookupPolicy {
    static final int CONNECT_TIMEOUT_MS = 5_000;
    static final int READ_TIMEOUT_MS = 10_000;
    static final String USER_AGENT = "Dora/1.0 (+https://github.com/)";

    private static final Duration SUCCESS_TTL = Duration.ofMinutes(10);
    private static final Duration FAILURE_TTL = Duration.ofSeconds(30);
    private static final String REMOTE_FAILURE_MESSAGE = "No se ha podido consultar el servicio remoto de plataformas. "
            + "Revisa la conexión e inténtalo de nuevo.";

    private static final ConcurrentMap<String, JsonCacheEntry> JSON_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, BytesCacheEntry> BYTES_CACHE = new ConcurrentHashMap<>();

    private PlatformRemoteLookupPolicy() {
    }

    static JsonElement getJson(String key, RemoteJsonLoader loader) throws IOException {
        String normalizedKey = requireKey(key);
        long now = System.currentTimeMillis();
        JsonCacheEntry cached = JSON_CACHE.get(normalizedKey);
        if (cached != null && cached.validAt(now)) {
            if (cached.failureMessage() != null) {
                throw new IOException(cached.failureMessage());
            }
            return cached.value().deepCopy();
        }

        try {
            JsonElement value = loader.load();
            JSON_CACHE.put(normalizedKey, JsonCacheEntry.success(value.deepCopy(), now + SUCCESS_TTL.toMillis()));
            return value.deepCopy();
        } catch (IOException | RuntimeException ex) {
            String message = remoteFailureMessage();
            JSON_CACHE.put(normalizedKey, JsonCacheEntry.failure(message, now + FAILURE_TTL.toMillis()));
            throw new IOException(message, ex);
        }
    }

    static byte[] getBytes(String key, RemoteBytesLoader loader) throws IOException {
        String normalizedKey = requireKey(key);
        long now = System.currentTimeMillis();
        BytesCacheEntry cached = BYTES_CACHE.get(normalizedKey);
        if (cached != null && cached.validAt(now)) {
            if (cached.failureMessage() != null) {
                throw new IOException(cached.failureMessage());
            }
            return Arrays.copyOf(cached.value(), cached.value().length);
        }

        try {
            byte[] value = loader.load();
            byte[] copy = Arrays.copyOf(value, value.length);
            BYTES_CACHE.put(normalizedKey, BytesCacheEntry.success(copy, now + SUCCESS_TTL.toMillis()));
            return Arrays.copyOf(copy, copy.length);
        } catch (IOException | RuntimeException ex) {
            String message = remoteFailureMessage();
            BYTES_CACHE.put(normalizedKey, BytesCacheEntry.failure(message, now + FAILURE_TTL.toMillis()));
            throw new IOException(message, ex);
        }
    }

    static void clearForTests() {
        JSON_CACHE.clear();
        BYTES_CACHE.clear();
    }

    private static String requireKey(String key) throws IOException {
        if (key == null || key.isBlank()) {
            throw new IOException("No se ha indicado la URL.");
        }
        return key.trim();
    }

    static String remoteFailureMessage() {
        return REMOTE_FAILURE_MESSAGE;
    }

    @FunctionalInterface
    interface RemoteJsonLoader {
        JsonElement load() throws IOException;
    }

    @FunctionalInterface
    interface RemoteBytesLoader {
        byte[] load() throws IOException;
    }

    private record JsonCacheEntry(JsonElement value, String failureMessage, long expiresAtMillis) {
        static JsonCacheEntry success(JsonElement value, long expiresAtMillis) {
            return new JsonCacheEntry(value, null, expiresAtMillis);
        }

        static JsonCacheEntry failure(String message, long expiresAtMillis) {
            return new JsonCacheEntry(null, message, expiresAtMillis);
        }

        boolean validAt(long nowMillis) {
            return expiresAtMillis > nowMillis;
        }
    }

    private record BytesCacheEntry(byte[] value, String failureMessage, long expiresAtMillis) {
        static BytesCacheEntry success(byte[] value, long expiresAtMillis) {
            return new BytesCacheEntry(value, null, expiresAtMillis);
        }

        static BytesCacheEntry failure(String message, long expiresAtMillis) {
            return new BytesCacheEntry(null, message, expiresAtMillis);
        }

        boolean validAt(long nowMillis) {
            return expiresAtMillis > nowMillis;
        }
    }
}
