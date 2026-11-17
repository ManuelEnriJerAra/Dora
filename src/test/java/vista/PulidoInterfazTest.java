package vista;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PulidoInterfazTest {

    @Test
    void botonesClarosUsanTextoOscuroYLosOscurosBlanco() {
        assertThat(AppTheme.getReadableForegroundOn(AppTheme.getSuccessColor())).isEqualTo(AppTheme.DARK_TEXT_ON_COLOR);
        assertThat(AppTheme.getReadableForegroundOn(AppTheme.getWarningColor())).isEqualTo(AppTheme.DARK_TEXT_ON_COLOR);
        assertThat(AppTheme.getReadableForegroundOn(AppTheme.getDangerColor())).isEqualTo(Color.WHITE);
        assertThat(AppTheme.getReadableForegroundOn(Color.RED)).isEqualTo(Color.WHITE);
    }

    @Test
    void elTextoElegidoTieneContrasteSuficiente() {
        for (Color fondo : new Color[]{AppTheme.getSuccessColor(), AppTheme.getWarningColor(), AppTheme.getDangerColor()}) {
            assertThat(AppTheme.contrastRatio(fondo, AppTheme.getReadableForegroundOn(fondo))).isGreaterThanOrEqualTo(3.0);
        }
    }

    @Test
    void describeLosRequisitosDelModpack() {
        Map<String, String> deps = new LinkedHashMap<>();
        deps.put("minecraft", "1.20.1");
        deps.put("fabric-loader", "0.15.11");
        assertThat(VentanaPrincipal.describirRequisitos(deps)).isEqualTo("Minecraft 1.20.1 con Fabric 0.15.11");
        assertThat(VentanaPrincipal.describirRequisitos(Map.of())).isNull();
        assertThat(VentanaPrincipal.describirRequisitos(Map.of("neoforge", "21.1.1"))).isEqualTo("NeoForge 21.1.1");
    }
}
