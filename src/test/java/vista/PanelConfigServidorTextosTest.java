package vista;

import org.junit.jupiter.api.Test;

import javax.swing.SpinnerNumberModel;

import static org.assertj.core.api.Assertions.assertThat;

class PanelConfigServidorTextosTest {

    @Test
    void etiquetaValorTraduceDificultadYModoDeJuego() {
        assertThat(PanelConfigServidor.etiquetaValor("peaceful")).isEqualTo("Pacífico");
        assertThat(PanelConfigServidor.etiquetaValor("HARD")).isEqualTo("Difícil");
        assertThat(PanelConfigServidor.etiquetaValor("survival")).isEqualTo("Supervivencia");
        assertThat(PanelConfigServidor.etiquetaValor("spectator")).isEqualTo("Espectador");
    }

    @Test
    void etiquetaValorDejaIgualLosValoresDesconocidos() {
        assertThat(PanelConfigServidor.etiquetaValor("minecraft:flat")).isEqualTo("minecraft:flat");
        assertThat(PanelConfigServidor.etiquetaValor(null)).isEmpty();
    }

    @Test
    void puertoQuedaLimitadoAlRangoValido() {
        SpinnerNumberModel modelo = PanelConfigServidor.crearModeloNumerico("server-port", 99_999);
        assertThat(modelo.getValue()).isEqualTo(65535);
        assertThat(modelo.getMinimum()).isEqualTo(1);
        assertThat(modelo.getMaximum()).isEqualTo(65535);
        assertThat(PanelConfigServidor.crearModeloNumerico("query.port", 0).getValue()).isEqualTo(1);
    }

    @Test
    void contadoresNoAceptanNegativos() {
        SpinnerNumberModel modelo = PanelConfigServidor.crearModeloNumerico("max-players", -5);
        assertThat(modelo.getValue()).isEqualTo(0);
        assertThat(modelo.getMinimum()).isEqualTo(0);
    }

    @Test
    void otrasPropiedadesConservanSuValor() {
        assertThat(PanelConfigServidor.crearModeloNumerico("op-permission-level", -3).getValue()).isEqualTo(-3);
    }

    @Test
    void textosDeDialogosEnEspanol() {
        assertThat(TextosSwingEspanol.TEXTOS)
                .containsEntry("OptionPane.yesButtonText", "Sí")
                .containsEntry("OptionPane.cancelButtonText", "Cancelar")
                .containsEntry("FileChooser.openButtonText", "Abrir")
                .containsEntry("FileChooser.cancelButtonText", "Cancelar");
    }
}
