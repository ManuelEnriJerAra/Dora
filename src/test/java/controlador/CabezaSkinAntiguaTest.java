package controlador;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class CabezaSkinAntiguaTest {

    private static BufferedImage skin(int alto, int colorSombrero) {
        BufferedImage skin = new BufferedImage(64, alto, BufferedImage.TYPE_INT_ARGB);
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                skin.setRGB(x, y, 0xFFC08060); // cara
            }
        }
        for (int y = 0; y < 32; y++) {
            for (int x = 32; x < 64; x++) {
                skin.setRGB(x, y, colorSombrero);
            }
        }
        return skin;
    }

    @Test
    void skinAntiguaConSombreroOpaco_muestraLaCara() {
        BufferedImage cabeza = MojangAPI.extraerCabezaDesdeSkin(skin(32, 0xFF000000));

        assertThat(cabeza.getRGB(3, 3)).isEqualTo(0xFFC08060);
    }

    @Test
    void skinAntiguaConSombreroTransparente_usaElSombrero() {
        BufferedImage piel = skin(32, 0x00000000);
        piel.setRGB(40, 8, 0xFF0000FF);

        BufferedImage cabeza = MojangAPI.extraerCabezaDesdeSkin(piel);

        assertThat(cabeza.getRGB(0, 0)).isEqualTo(0xFF0000FF);
        assertThat(cabeza.getRGB(3, 3)).isEqualTo(0xFFC08060);
    }

    @Test
    void skinModernaConSombreroOpaco_loRespeta() {
        BufferedImage cabeza = MojangAPI.extraerCabezaDesdeSkin(skin(64, 0xFF000000));

        assertThat(cabeza.getRGB(3, 3)).isEqualTo(0xFF000000);
    }
}
