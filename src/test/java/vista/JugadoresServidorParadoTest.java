package vista;

import modelo.Server;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JugadoresServidorParadoTest {

    @Test
    void servidorSinProceso_noTieneJugadores() {
        assertThat(PanelJugadores.servidorConJugadores(new Server())).isFalse();
        assertThat(PanelJugadores.servidorConJugadores(null)).isFalse();
    }

    @Test
    void servidorDeteniendose_noTieneJugadores() throws Exception {
        Server server = new Server();
        Process proceso = new ProcessBuilder(ProcessHandle.current().info().command().orElse("java"), "-version")
                .redirectErrorStream(true).start();
        try {
            server.setServerProcess(proceso);
            server.setDeteniendo(true);
            assertThat(PanelJugadores.servidorConJugadores(server)).isFalse();
        } finally {
            proceso.destroyForcibly();
        }
    }
}
