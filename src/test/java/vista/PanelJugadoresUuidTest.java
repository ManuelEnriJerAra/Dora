package vista;

import modelo.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PanelJugadoresUuidTest {

    @TempDir
    Path tempDir;

    @Test
    void uuidOffline_coincideConElDeMinecraft() {
        // UUID offline conocido de "Notch" (OfflinePlayer:Notch, MD5 tipo 3).
        assertThat(PanelJugadores.uuidOffline("Notch")).isEqualTo("b50ad385-829d-3141-a216-7e7d7539ba7f");
    }

    @Test
    void onlineMode_seLeeDeServerProperties() throws Exception {
        Server server = new Server();
        server.setServerDir(tempDir.toString());
        assertThat(PanelJugadores.esServidorOnlineMode(server)).isTrue();

        Files.writeString(tempDir.resolve("server.properties"), "online-mode=false\n");
        assertThat(PanelJugadores.esServidorOnlineMode(server)).isFalse();

        Files.writeString(tempDir.resolve("server.properties"), "online-mode=true\n");
        assertThat(PanelJugadores.esServidorOnlineMode(server)).isTrue();
    }
}
