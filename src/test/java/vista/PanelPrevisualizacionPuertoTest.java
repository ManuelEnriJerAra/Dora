package vista;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;

class PanelPrevisualizacionPuertoTest {

    @TempDir
    Path dir;

    @Test
    void soloAvisaCuandoCambiaElArchivoOLaRuta() throws Exception {
        Path props = dir.resolve("server.properties");
        Files.writeString(props, "server-port=25565\n");
        PanelPrevisualizacion.PuertoPropertiesCache cache = new PanelPrevisualizacion.PuertoPropertiesCache();

        assertThat(cache.haCambiado(props)).isTrue();
        assertThat(cache.haCambiado(props)).isFalse();

        Files.writeString(props, "server-port=25570\n");
        Files.setLastModifiedTime(props, FileTime.fromMillis(Files.getLastModifiedTime(props).toMillis() + 5_000));
        assertThat(cache.haCambiado(props)).isTrue();

        Path otro = dir.resolve("otro.properties");
        assertThat(cache.haCambiado(otro)).isTrue();
        assertThat(cache.haCambiado(otro)).isFalse();
        assertThat(cache.haCambiado(null)).isTrue();
    }
}
