package vista;

import controlador.GestorServidores;
import modelo.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PausaPaginasOcultasTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void aislarConfiguracion() {
        System.setProperty("dora.appRoot", tempDir.resolve("app-root").toString());
    }

    @AfterEach
    void limpiar() {
        System.clearProperty("dora.appRoot");
    }

    @Test
    void consolaOculta_guardaLasLineasSinTocarElDocumento() throws Exception {
        PanelConsola consola = new PanelConsola(new GestorServidores());

        for (int i = 0; i < 20; i++) {
            consola.escribirLinea("[12:00:00] [INFO] linea " + i);
        }
        esperarEdt();

        assertThat(consola.isShowing()).isFalse();
        assertThat(consola.getLineasEnPausaParaTests()).isEqualTo(20);
        assertThat(textoConsola(consola)).doesNotContain("linea 0");
    }

    @Test
    void consolaOculta_conMasLineasDeLasQueCabenReconstruyeUnaVez() throws Exception {
        PanelConsola consola = new PanelConsola(new GestorServidores());

        for (int i = 0; i < 5200; i++) {
            consola.escribirLinea("[12:00:00] [INFO] linea " + i);
        }
        esperarEdt();

        assertThat(consola.getLineasEnPausaParaTests()).isZero();
        assertThat(consola.isReconstruccionPendienteParaTests()).isTrue();
    }

    @Test
    void estadisticasOcultas_noArrancanElRefrescoPeriodico() throws Exception {
        Server server = new Server();
        server.setServerDir(tempDir.resolve("srv").toString());
        server.setEstadisticasPersistenciaActiva(false);
        PanelEstadisticas[] panel = new PanelEstadisticas[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new PanelEstadisticas(null, server));
        esperarEdt();

        assertThat(panel[0].isRefrescoActivoParaTests()).isFalse();
    }

    private static void esperarEdt() throws Exception {
        // El drenaje de la consola se programa con invokeLater y puede reprogramarse en lotes.
        for (int i = 0; i < 30; i++) {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }

    private static String textoConsola(Container raiz) {
        for (Component c : raiz.getComponents()) {
            if (c instanceof JTextPane pane && !pane.isEditable()) {
                return pane.getText();
            }
            if (c instanceof Container hijo) {
                String texto = textoConsola(hijo);
                if (texto != null) return texto;
            }
        }
        return null;
    }
}
