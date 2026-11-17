package vista;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PanelConsolaTest {

    @Test
    void historial_recorreLosComandosYRecuperaElBorrador() {
        PanelConsola.HistorialComandos historial = new PanelConsola.HistorialComandos(10);
        historial.registrar("list");
        historial.registrar("say hola");

        assertThat(historial.anterior("op St")).isEqualTo("say hola");
        assertThat(historial.anterior("say hola")).isEqualTo("list");
        assertThat(historial.anterior("list")).isNull();
        assertThat(historial.siguiente()).isEqualTo("say hola");
        assertThat(historial.siguiente()).isEqualTo("op St");
        assertThat(historial.siguiente()).isNull();
    }

    @Test
    void historial_noRepiteComandosSeguidosYRespetaElMaximo() {
        PanelConsola.HistorialComandos historial = new PanelConsola.HistorialComandos(2);
        historial.registrar("a");
        historial.registrar("a");
        historial.registrar("b");
        historial.registrar("c");

        assertThat(historial.anterior("")).isEqualTo("c");
        assertThat(historial.anterior("c")).isEqualTo("b");
        assertThat(historial.anterior("b")).isNull();
    }

    @Test
    void autoscroll_soloSiElUsuarioEstaAlFinal() {
        assertThat(PanelConsola.debeSeguirAlFinal(900, 100, 1000)).isTrue();
        assertThat(PanelConsola.debeSeguirAlFinal(890, 100, 1000)).isTrue();
        assertThat(PanelConsola.debeSeguirAlFinal(500, 100, 1000)).isFalse();
    }
}
