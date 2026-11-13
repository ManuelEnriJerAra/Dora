package controlador;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MojangAPITest {
    @Test
    void descargar_debeUsarTimeoutLargoParaJarYMoverArchivoTemporal() throws Exception {
        FakeMojangApi api = new FakeMojangApi(List.of(new ByteArrayInputStream(new byte[]{1, 2, 3, 4})));
        Path target = Files.createTempDirectory("mojang-download-ok").resolve("server.jar");

        api.descargar("https://example.test/server.jar", target.toFile(), null);

        assertThat(Files.readAllBytes(target)).containsExactly(1, 2, 3, 4);
        assertThat(Files.exists(target.resolveSibling("server.jar.part"))).isFalse();
        assertThat(api.readTimeouts()).containsExactly(60_000);
    }

    @Test
    void descargar_debeReintentarTimeoutsYEliminarParcial() throws Exception {
        FakeMojangApi api = new FakeMojangApi(List.of(
                new TimeoutInputStream(),
                new TimeoutInputStream(),
                new TimeoutInputStream()
        ));
        Path target = Files.createTempDirectory("mojang-download-timeout").resolve("server.jar");

        assertThatThrownBy(() -> api.descargar("https://example.test/server.jar", target.toFile(), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("tardado demasiado")
                .hasRootCauseInstanceOf(SocketTimeoutException.class);

        assertThat(api.openCount()).isEqualTo(3);
        assertThat(Files.exists(target)).isFalse();
        assertThat(Files.exists(target.resolveSibling("server.jar.part"))).isFalse();
    }

    @Test
    void mensajeErrorDescarga_debeNormalizarTimeoutAnidado() {
        IOException exception = new IOException("java.net.SocketTimeoutException: Read timed out",
                new SocketTimeoutException("Read timed out"));

        assertThat(ErroresDescarga.mensaje(exception))
                .isEqualTo("La descarga ha tardado demasiado. Comprueba tu conexión e inténtalo de nuevo.");
    }

    private static final class FakeMojangApi extends MojangAPI {
        private final List<InputStream> responses;
        private final List<Integer> readTimeouts = new ArrayList<>();
        private int openCount;

        private FakeMojangApi(List<InputStream> responses) {
            this.responses = new ArrayList<>(responses);
        }

        @Override
        protected URLConnection openConnection(String url, int connectTimeoutMs, int readTimeoutMs) throws IOException {
            readTimeouts.add(readTimeoutMs);
            openCount++;
            InputStream response = responses.removeFirst();
            return new URLConnection(URI.create(url).toURL()) {
                @Override
                public void connect() {
                }

                @Override
                public long getContentLengthLong() {
                    return 4;
                }

                @Override
                public InputStream getInputStream() {
                    return response;
                }
            };
        }

        private int openCount() {
            return openCount;
        }

        private List<Integer> readTimeouts() {
            return readTimeouts;
        }
    }

    private static final class TimeoutInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new SocketTimeoutException("Read timed out");
        }
    }
}
