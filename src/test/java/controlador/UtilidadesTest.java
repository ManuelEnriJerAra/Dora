package controlador;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilidadesTest {
    @TempDir
    Path tempDir;

    @Test
    @Tag("smoke")
    void copiarDirectorio_debeCopiarArbolCompleto() throws Exception {
        Path origen = tempDir.resolve("origen");
        Path destino = tempDir.resolve("destino");
        Files.createDirectories(origen.resolve("sub"));
        Files.writeString(origen.resolve("root.txt"), "root");
        Files.writeString(origen.resolve("sub/data.txt"), "nested");

        Utilidades.copiarDirectorio(origen, destino);

        assertThat(destino.resolve("root.txt")).hasContent("root");
        assertThat(destino.resolve("sub/data.txt")).hasContent("nested");
    }

    @Test
    void moverDirectorio_debeMoverContenidoYEliminarOrigen() throws Exception {
        Path origen = tempDir.resolve("world");
        Path destino = tempDir.resolve("migrated/world");
        Files.createDirectories(origen);
        Files.writeString(origen.resolve("level.dat"), "data");

        Utilidades.moverDirectorio(origen, destino);

        assertThat(origen).doesNotExist();
        assertThat(destino.resolve("level.dat")).hasContent("data");
    }

    @Test
    void eliminarDirectorio_debeBorrarArbolCompleto() throws Exception {
        Path directorio = tempDir.resolve("delete-me");
        Files.createDirectories(directorio.resolve("nested"));
        Files.writeString(directorio.resolve("nested/file.txt"), "bye");

        Utilidades.eliminarDirectorio(directorio);

        assertThat(directorio).doesNotExist();
    }

    @Test
    @Tag("smoke")
    void escribirMotdYPuerto_debePersistirValoresEnProperties() throws Exception {
        Path serverDir = tempDir.resolve("server");

        Utilidades.escribirPuertoEnProperties(serverDir, 25570);
        Utilidades.escribirMotdEnProperties(serverDir, "Hello world");

        assertThat(Utilidades.leerMotdDesdeProperties(serverDir)).isEqualTo("Hello world");
        assertThat(Files.readString(serverDir.resolve("server.properties"))).contains("server-port=25570");
    }

    @Test
    void escribirPuerto_debeConservarPropertiesGuardadoEnLatin1() throws Exception {
        Path serverDir = tempDir.resolve("latin1-server");
        Files.createDirectories(serverDir);
        Path properties = serverDir.resolve("server.properties");
        // Un MOTD con tildes guardado desde un editor en ANSI/Latin-1 no es UTF-8 válido.
        Files.write(properties, "motd=Servidor de Ram\u00F3n\nlevel-name=dora-worlds/mundo\nmax-players=42\nserver-port=25565\n"
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

        Utilidades.escribirPuertoEnProperties(serverDir, 25570);

        java.util.Properties result = Utilidades.cargarPropertiesUtf8(properties);
        assertThat(result.getProperty("server-port")).isEqualTo("25570");
        assertThat(result.getProperty("level-name")).isEqualTo("dora-worlds/mundo");
        assertThat(result.getProperty("max-players")).isEqualTo("42");
        assertThat(result.getProperty("motd")).isEqualTo("Servidor de Ram\u00F3n");
    }

    @Test
    void escribirPuertoYMotd_noDebenSobrescribirPropertiesIlegible() throws Exception {
        Path serverDir = tempDir.resolve("broken-server");
        Files.createDirectories(serverDir);
        Path properties = serverDir.resolve("server.properties");
        String original = "level-name=dora-worlds/mundo\nmotd=\\uZZZZ roto\nmax-players=42\n";
        Files.writeString(properties, original);

        assertThatThrownBy(() -> Utilidades.escribirPuertoEnProperties(serverDir, 25570))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("server.properties");
        assertThatThrownBy(() -> Utilidades.escribirMotdEnProperties(serverDir, "Hola"))
                .isInstanceOf(RuntimeException.class);

        assertThat(Files.readString(properties)).isEqualTo(original);
    }

    @Test
    void guardarPropertiesUtf8_debeReemplazarElArchivoSinDejarTemporales() throws Exception {
        Path serverDir = tempDir.resolve("atomic-server");
        Files.createDirectories(serverDir);
        Path properties = serverDir.resolve("server.properties");
        Files.writeString(properties, "motd=antes\n");
        java.util.Properties values = new java.util.Properties();
        values.setProperty("motd", "despu\u00E9s");

        Utilidades.guardarPropertiesUtf8(properties, values, null);

        assertThat(Utilidades.cargarPropertiesUtf8(properties).getProperty("motd")).isEqualTo("despu\u00E9s");
        try (var files = Files.list(serverDir)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("server.properties");
        }
    }

    @Test
    void encontrarEjecutableJar_debeDevolverElUnicoJarDisponible() throws Exception {
        Path serverDir = tempDir.resolve("server");
        Files.createDirectories(serverDir);
        Path jar = Files.writeString(serverDir.resolve("paper.jar"), "jar");

        assertThat(Utilidades.encontrarEjecutableJar(serverDir)).isEqualTo(jar);
    }

    @Test
    void encontrarEjecutableJar_debeFallarSiHayMasDeUnJar() throws Exception {
        Path serverDir = tempDir.resolve("server");
        Files.createDirectories(serverDir);
        Files.writeString(serverDir.resolve("a.jar"), "a");
        Files.writeString(serverDir.resolve("b.jar"), "b");

        assertThatIllegalStateException()
                .isThrownBy(() -> Utilidades.encontrarEjecutableJar(serverDir))
                .withMessageContaining("m\u00E1s de un jar");
    }

    @Test
    @Tag("smoke")
    void extraerPorcentaje_debeLeerNumeroAntesDelSimboloPorcentaje() {
        assertThat(Utilidades.extraerPorcentaje("Downloading libraries 83%")).isEqualTo(83);
    }

    @Test
    void limpiarSecuenciasConsola_debeEliminarColoresAnsiDeNombresDeJugador() {
        String raw = "\u001B[38;2;255;255;85mManuJeroZX joined the game";

        assertThat(Utilidades.limpiarSecuenciasConsola(raw)).isEqualTo("ManuJeroZX joined the game");
    }

    @Test
    void limpiarSecuenciasConsola_debeEliminarColoresAnsiSiSePerdioEscapeInicial() {
        String raw = "[12:34:56] [Server thread/INFO]: [38;2;255;255;85mManuJeroZX joined the game";

        assertThat(Utilidades.limpiarSecuenciasConsola(raw))
                .isEqualTo("[12:34:56] [Server thread/INFO]: ManuJeroZX joined the game");
    }

    @Test
    void resolveSystemPicturesDirectory_debePriorizarPicturesDeWindows() throws Exception {
        Path home = tempDir.resolve("home");
        Path pictures = home.resolve("Pictures");
        Files.createDirectories(pictures);

        assertThat(Utilidades.resolveSystemPicturesDirectory("Windows 11", home.toString(), Map.of("USERPROFILE", home.toString())))
                .isEqualTo(pictures.toFile());
    }

    @Test
    void resolveSystemPicturesDirectory_debeLeerXdgPicturesDir() throws Exception {
        Path home = tempDir.resolve("home");
        Path configDir = tempDir.resolve("config");
        Path pictures = home.resolve("Media").resolve("Capturas");
        Files.createDirectories(pictures);
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("user-dirs.dirs"), "XDG_PICTURES_DIR=\"$HOME/Media/Capturas\"");

        assertThat(Utilidades.resolveSystemPicturesDirectory(
                "Linux",
                home.toString(),
                Map.of("XDG_CONFIG_HOME", configDir.toString())
        )).isEqualTo(pictures.toFile());
    }
}
