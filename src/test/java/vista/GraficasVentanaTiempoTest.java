package vista;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class GraficasVentanaTiempoTest {

    private static final long FIN = 1_800_000_000_000L;
    private static final int RECIENTE_24H = 24 * 3600;
    private static final int CUBETA_1MIN = 60;

    private static PanelEstadisticas.StatsHistory historialConUnaHoraPorSegundo() {
        PanelEstadisticas.StatsHistory historial = new PanelEstadisticas.StatsHistory();
        for (int i = 3599; i >= 0; i--) {
            historial.addCpuSample(i < 1800 ? 80 : 20, FIN - i * 1000L, RECIENTE_24H, CUBETA_1MIN);
        }
        return historial;
    }

    @Test
    void unaHora_seAgrupaEnComoMuchoElMaximoDePuntos() {
        PanelEstadisticas.StatsHistory historial = historialConUnaHoraPorSegundo();

        List<PanelEstadisticas.ChartSample> puntos = historial.snapshotForRangeCpu(3600, FIN);

        assertThat(puntos).hasSize(PanelEstadisticas.MAX_CHART_POINTS);
        assertThat(puntos).allMatch(Objects::nonNull);
        assertThat(puntos.get(0).value()).isEqualTo(20);
        assertThat(puntos.get(puntos.size() - 1).value()).isEqualTo(80);
        assertThat(puntos.get(puntos.size() - 1).timestampMillis()).isEqualTo(FIN);
    }

    @Test
    void sesentaSegundos_unPuntoPorSegundo() {
        List<PanelEstadisticas.ChartSample> puntos = historialConUnaHoraPorSegundo().snapshotForRangeCpu(60, FIN);

        assertThat(puntos).hasSize(60).allMatch(Objects::nonNull);
        assertThat(puntos.get(0).timestampMillis()).isEqualTo(FIN - 59_000L);
    }

    @Test
    void ventanaDesplazada_terminaEnLaHoraPedida() {
        PanelEstadisticas.StatsHistory historial = historialConUnaHoraPorSegundo();

        long fin = historial.getWindowEndTimestamp(1800);
        List<PanelEstadisticas.ChartSample> puntos = historial.snapshotForRangeCpu(600, fin);

        assertThat(fin).isEqualTo(FIN - 1_800_000L);
        assertThat(puntos).allMatch(p -> p != null && p.value() == 20 && p.timestampMillis() <= fin);
        assertThat(historial.getMaxOffsetSeconds(600)).isEqualTo(3599 - 600);
    }

    @Test
    void treintaDias_sinHistoricoAntiguoDejaHuecos() {
        List<PanelEstadisticas.ChartSample> puntos = historialConUnaHoraPorSegundo()
                .snapshotForRangeCpu(30 * 24 * 3600, FIN);

        assertThat(puntos).hasSize(PanelEstadisticas.MAX_CHART_POINTS);
        assertThat(puntos.get(0)).isNull();
        assertThat(puntos.get(puntos.size() - 1)).isNotNull();
    }

    @Test
    void sinHistorico_noHayVentana() {
        PanelEstadisticas.StatsHistory vacio = new PanelEstadisticas.StatsHistory();

        assertThat(vacio.getWindowEndTimestamp(0)).isZero();
        assertThat(vacio.getMaxOffsetSeconds(60)).isZero();
        assertThat(vacio.snapshotForRangeCpu(60, 0L)).hasSize(60).containsOnlyNulls();
    }

    @Test
    void ventanaEnVivo_soloSiTerminaAhora() {
        assertThat(PanelEstadisticas.esVentanaEnVivo(System.currentTimeMillis())).isTrue();
        assertThat(PanelEstadisticas.esVentanaEnVivo(System.currentTimeMillis() - 60_000L)).isFalse();
        assertThat(PanelEstadisticas.esVentanaEnVivo(0L)).isFalse();
    }
}
