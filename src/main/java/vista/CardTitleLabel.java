package vista;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicGraphicsUtils;

final class CardTitleLabel extends JLabel {
    private static final String MUESTRA_ACENTOS = "ÁÉÍÓÚÑ";

    CardTitleLabel() {
        super();
    }

    CardTitleLabel(String text) {
        super(text);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize();
        String text = getText();
        if (text == null || text.isBlank() || getFont() == null) {
            return preferred;
        }

        FontMetrics metrics = getFontMetrics(getFont());
        if (metrics == null) {
            return preferred;
        }

        FontRenderContext frc = metrics.getFontRenderContext();
        // Se mide también con mayúsculas acentuadas: con la altura justa del texto se recortaban las tildes
        // ("Estadisticas", "Ultimas conexiones") y cada título quedaba de una altura distinta.
        GlyphVector glyphVector = getFont().createGlyphVector(frc, text + MUESTRA_ACENTOS);
        Rectangle2D visualBounds = glyphVector.getVisualBounds();
        Insets insets = getInsets();
        int tightHeight = (int) Math.ceil(visualBounds.getHeight()) + insets.top + insets.bottom;
        return new Dimension(preferred.width, Math.max(1, tightHeight));
    }

    /**
     * Con la altura ajustada, JLabel centra la caja completa de la fuente y recorta lo que sobresale por arriba,
     * como la tilde de la "Ú". Aquí la línea base se coloca según los límites reales de los glifos.
     */
    @Override
    protected void paintComponent(Graphics g) {
        String text = getText();
        if (getIcon() != null || text == null || text.isBlank() || getFont() == null) {
            super.paintComponent(g);
            return;
        }
        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        FontMetrics metrics = getFontMetrics(getFont());
        Rectangle2D visualBounds = getFont()
                .createGlyphVector(metrics.getFontRenderContext(), text + MUESTRA_ACENTOS)
                .getVisualBounds();
        Insets insets = getInsets();
        int disponible = getHeight() - insets.top - insets.bottom;
        int sobrante = Math.max(0, disponible - (int) Math.ceil(visualBounds.getHeight()));
        int baseline = insets.top + sobrante / 2 + (int) Math.ceil(-visualBounds.getY());
        String visible = SwingUtilities.layoutCompoundLabel(this, metrics, text, null,
                getVerticalAlignment(), getHorizontalAlignment(), getVerticalTextPosition(), getHorizontalTextPosition(),
                new Rectangle(insets.left, insets.top, Math.max(0, getWidth() - insets.left - insets.right), Math.max(0, disponible)),
                new Rectangle(), new Rectangle(), 0);
        g.setColor(isEnabled() ? getForeground() : UIManager.getColor("Label.disabledForeground"));
        BasicGraphicsUtils.drawString(this, (Graphics2D) g, visible, insets.left, baseline);
    }
}
