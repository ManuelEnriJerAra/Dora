package vista;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class ArranqueRapidoTest {

    @Test
    void vistaPrevia_seConvierteAUnFormatoRapidoConLosMismosPixeles() {
        BufferedImage png = new BufferedImage(4, 3, BufferedImage.TYPE_3BYTE_BGR);
        png.setRGB(1, 2, 0x12AB34);

        BufferedImage convertida = PanelMundo.comoImagenRapidaDePintar(png);

        assertThat(convertida.getType()).isEqualTo(BufferedImage.TYPE_INT_RGB);
        assertThat(convertida.getWidth()).isEqualTo(4);
        assertThat(convertida.getHeight()).isEqualTo(3);
        assertThat(convertida.getRGB(1, 2) & 0xFFFFFF).isEqualTo(0x12AB34);
        assertThat(convertida.getAccelerationPriority()).isZero();
    }

    @Test
    void vistaPrevia_conTransparenciaLaConserva() {
        BufferedImage png = new BufferedImage(2, 2, BufferedImage.TYPE_4BYTE_ABGR);

        assertThat(PanelMundo.comoImagenRapidaDePintar(png).getType()).isEqualTo(BufferedImage.TYPE_INT_ARGB);
        assertThat(PanelMundo.comoImagenRapidaDePintar(null)).isNull();
    }

    @Test
    void puntoDeEstado_esUnIconoDeColor() {
        PanelServidores.PuntoEstadoIcon icono = new PanelServidores.PuntoEstadoIcon(Color.RED);

        assertThat(icono.getColor()).isEqualTo(Color.RED);
        assertThat(icono.getIconWidth()).isEqualTo(icono.getIconHeight()).isPositive();
    }
}
