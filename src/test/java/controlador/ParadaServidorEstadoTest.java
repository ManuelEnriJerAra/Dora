package controlador;

import modelo.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ParadaServidorEstadoTest {

    @TempDir
    Path tempDir;

    private Process proceso;

    @AfterEach
    void cleanUp() {
        if (proceso != null) {
            proceso.destroyForcibly();
        }
    }

    @Test
    void esComandoParada_reconoceStopConBarraYMayusculas() {
        assertThat(GestorServidores.esComandoParada("stop")).isTrue();
        assertThat(GestorServidores.esComandoParada(" /STOP ")).isTrue();
        assertThat(GestorServidores.esComandoParada("end")).isTrue();
        assertThat(GestorServidores.esComandoParada("stopwatch")).isFalse();
        assertThat(GestorServidores.esComandoParada("say stop")).isFalse();
        assertThat(GestorServidores.esComandoParada(null)).isFalse();
    }

    @Test
    void mandarStop_marcaElServidorComoDeteniendoHastaQueTermina() throws Exception {
        Path lector = tempDir.resolve("Lector.java");
        Files.writeString(lector, """
                class Lector {
                    public static void main(String[] a) throws Exception {
                        var in = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
                        String l;
                        while ((l = in.readLine()) != null) { if (l.equals("stop")) return; }
                    }
                }
                """);
        String java = ProcessHandle.current().info().command().orElseThrow();
        proceso = new ProcessBuilder(java, lector.toString()).start();

        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Server server = new Server();
        server.setId("parada-test");
        server.setServerProcess(proceso);

        assertThat(server.getDeteniendo()).isFalse();
        long antes = System.currentTimeMillis();
        gestor.mandarComando(server, "list", false);
        assertThat(server.getDeteniendo()).isFalse();

        gestor.safePararServidor(server);

        assertThat(server.getDeteniendo()).isTrue();
        assertThat(server.getParadaSolicitadaMillis()).isGreaterThanOrEqualTo(antes);
        assertThat(proceso.waitFor(30, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void forzarParada_sobreProcesoMuerto_limpiaElEstadoDeteniendo() {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Server server = new Server();
        server.setId("parada-muerto");
        server.setDeteniendo(true);

        gestor.forzarPararServidor(server);

        assertThat(server.getDeteniendo()).isFalse();
        assertThat(server.getServerProcess()).isNull();
    }
}
