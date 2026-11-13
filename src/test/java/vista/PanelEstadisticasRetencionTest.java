package vista;

import modelo.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PanelEstadisticasRetencionTest {

    @Test
    void ventanaReciente_valoresAntiguosDeMesesVuelvenAlValorPorDefecto() {
        assertThat(PanelEstadisticas.normalizarVentanaReciente(30 * 24 * 60 * 60))
                .isEqualTo(Server.VENTANA_RECIENTE_POR_DEFECTO_SEGUNDOS);
        assertThat(PanelEstadisticas.normalizarVentanaReciente(null))
                .isEqualTo(Server.VENTANA_RECIENTE_POR_DEFECTO_SEGUNDOS);
        assertThat(PanelEstadisticas.normalizarVentanaReciente(0))
                .isEqualTo(Server.VENTANA_RECIENTE_POR_DEFECTO_SEGUNDOS);
        assertThat(PanelEstadisticas.normalizarVentanaReciente(3600)).isEqualTo(3600);
    }

    @Test
    void opcionesDeVentana_noSuperanElMaximoNiIncluyenMeses() {
        assertThat(java.util.Arrays.stream(PanelEstadisticas.RECENT_WINDOW_OPTIONS).boxed().toList())
                .allMatch(s -> s > 0 && s <= Server.VENTANA_RECIENTE_MAXIMA_SEGUNDOS)
                .contains(Server.VENTANA_RECIENTE_POR_DEFECTO_SEGUNDOS);
    }

    @Test
    void ventanaReciente_seMuestraEnHorasODias() {
        assertThat(PanelEstadisticas.formatRecentWindowValue(3600)).isEqualTo("1 h");
        assertThat(PanelEstadisticas.formatRecentWindowValue(6 * 3600)).isEqualTo("6 h");
        assertThat(PanelEstadisticas.formatRecentWindowValue(24 * 3600)).isEqualTo("1 día");
        assertThat(PanelEstadisticas.formatRecentWindowValue(72 * 3600)).isEqualTo("3 días");
    }

    @Test
    void servidorNuevo_usaLaVentanaPorDefecto() {
        assertThat(new Server().getEstadisticasVentanaRecienteSegundos())
                .isEqualTo(Server.VENTANA_RECIENTE_POR_DEFECTO_SEGUNDOS);
    }

    @Test
    void cierreForzado_soloSeOfreceTrasLaEspera() {
        assertThat(PanelControlServidor.permiteForzarCierre(0)).isFalse();
        assertThat(PanelControlServidor.permiteForzarCierre(PanelControlServidor.FORCE_STOP_DELAY_MS - 1)).isFalse();
        assertThat(PanelControlServidor.permiteForzarCierre(PanelControlServidor.FORCE_STOP_DELAY_MS)).isTrue();
    }

    @TempDir
    Path tempDir;

    @Test
    void cargarHistorialAntiguo_agregaLasMuestrasViejasAlHistorico() throws Exception {
        long ahora = System.currentTimeMillis();
        // Alineado a la hora para que las dos muestras viejas caigan siempre en el mismo bucket del histórico;
        // sin esto el test fallaba si el segundo de ejecución cruzaba un borde de bucket.
        long unaHora = 3600L * 1000;
        long haceDosDias = ((ahora - 48L * unaHora) / unaHora) * unaHora;
        String muestras = "[{\"value\":10,\"timestampMillis\":" + haceDosDias + ",\"shutdownMarker\":false},"
                + "{\"value\":20,\"timestampMillis\":" + (haceDosDias + 1000) + ",\"shutdownMarker\":false},"
                + "{\"value\":30,\"timestampMillis\":" + ahora + ",\"shutdownMarker\":false}]";
        Files.writeString(tempDir.resolve("dora-stats.json"),
                "{\"recentCpu\":" + muestras + ",\"recentRam\":" + muestras
                        + ",\"recentDisk\":" + muestras + ",\"recentPlayers\":" + muestras + "}");
        Server server = new Server();
        server.setId("retencion-test");
        server.setServerDir(tempDir.toString());

        Method load = PanelEstadisticas.class.getDeclaredMethod("loadHistoryFromDisk", Server.class, String.class);
        load.setAccessible(true);
        Object history = load.invoke(null, server, server.getId());

        assertThat(sizeOf(history, "cpuSamples")).isEqualTo(1);
        assertThat(sizeOf(history, "cpuArchive")).isEqualTo(1);
        Field dirty = history.getClass().getDeclaredField("dirty");
        dirty.setAccessible(true);
        assertThat(dirty.getBoolean(history)).isTrue();
    }

    private static int sizeOf(Object history, String fieldName) throws Exception {
        Field field = history.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(history);
        if (value instanceof Collection<?> c) return c.size();
        return ((Map<?, ?>) value).size();
    }
}
