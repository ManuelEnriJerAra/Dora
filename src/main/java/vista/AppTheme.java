package vista;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.Icon;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;

import com.formdev.flatlaf.ui.FlatLineBorder;

import controlador.Main;

public final class AppTheme {
    private static final int REFRESH_ROTATION_TIMER_MS = 16;
    private static final double REFRESH_ROTATION_STEP_RADIANS = Math.toRadians(6);
    private static final String PROP_REFRESH_TIMER = "dora.refreshTimer";
    private static final String PROP_REFRESH_ICON = "dora.refreshIcon";
    private static final String PROP_REFRESH_HOVER = "dora.refreshHover";
    private static final String PROP_REFRESH_INSTALLED = "dora.refreshInstalled";
    private static final Color FALLBACK_BACKGROUND = Color.LIGHT_GRAY;
    private static final Color FALLBACK_ACCENT = new Color(0, 120, 215);
    private static final Color FALLBACK_BORDER = new Color(0, 0, 0, 60);
    private static final Color FALLBACK_FOREGROUND = Color.BLACK;
    private static final Color FALLBACK_MUTED_FOREGROUND = Color.GRAY;
    private static final Color FALLBACK_LINK = new Color(20, 90, 180);
    private static final Color FALLBACK_DIALOG_BORDER = Color.LIGHT_GRAY;
    private static final Color FALLBACK_SUBTLE_BORDER = new Color(0, 0, 0, 30);
    private static final Color LIGHT_THEME_CONSOLE_BACKGROUND = Color.decode("#1D2036");
    private static final Color DEFAULT_CONSOLE_FOREGROUND = Color.WHITE;
    private static final Color DEFAULT_CONSOLE_CHAT = Color.CYAN;
    private static final Color DEFAULT_CONSOLE_ERROR = Color.RED;
    private static final Color DEFAULT_SUCCESS = new Color(129, 247, 135);
    private static final Color DEFAULT_DANGER = new Color(255, 87, 87);
    private static final Color DEFAULT_WARNING = new Color(255, 214, 99);

    private AppTheme() {
    }

    public static int getArc() {
        int arc = UIManager.getInt("Component.arc");
        return arc > 0 ? arc : Main.DEFAULT_ARC;
    }

    private static Color getBaseThemeBackground() {
        Color bg = UIManager.getColor("Panel.background");
        return bg != null ? bg : FALLBACK_BACKGROUND;
    }

    public static Color getBackground() {
        return darken(getBaseThemeBackground(), 0.07f);
    }

    public static Color getPanelBackground() {
        return getBaseThemeBackground();
    }

    public static Color getSurfaceBackground() {
        Color panel = getPanelBackground();
        float luminance = (0.299f * panel.getRed() + 0.587f * panel.getGreen() + 0.114f * panel.getBlue()) / 255f;
        return luminance >= 0.5f
                ? darken(panel, 0.035f)
                : tint(panel, Color.WHITE, 0.05f);
    }

    public static Color getMainAccent() {
        Color accent = UIManager.getColor("Component.focusColor");
        return accent != null ? accent : FALLBACK_ACCENT;
    }

    public static Color getBorderColor() {
        Color border = UIManager.getColor("Component.borderColor");
        if (border == null) border = UIManager.getColor("Separator.foreground");
        return border != null ? border : FALLBACK_BORDER;
    }

    public static Color getForeground() {
        Color fg = UIManager.getColor("Label.foreground");
        return fg != null ? fg : FALLBACK_FOREGROUND;
    }

    public static Color getMutedForeground() {
        Color fg = UIManager.getColor("Label.disabledForeground");
        return fg != null ? fg : FALLBACK_MUTED_FOREGROUND;
    }

    public static Color getLinkForeground() {
        Color link = UIManager.getColor("Component.linkColor");
        return link != null ? link : FALLBACK_LINK;
    }

    public static Color getDialogBorderColor() {
        return FALLBACK_DIALOG_BORDER;
    }

    public static Color getSubtleBorderColor() {
        return FALLBACK_SUBTLE_BORDER;
    }

    public static Color getConsoleBackground() {
        return isLightTheme() ? LIGHT_THEME_CONSOLE_BACKGROUND : getPanelBackground().darker().darker();
    }

    public static Color getConsoleForeground() {
        return DEFAULT_CONSOLE_FOREGROUND;
    }

    public static Color getConsoleInfoForeground() {
        return DEFAULT_CONSOLE_FOREGROUND;
    }

    public static Color getConsoleChatForeground() {
        return DEFAULT_CONSOLE_CHAT;
    }

    public static Color getConsoleErrorForeground() {
        return DEFAULT_CONSOLE_ERROR;
    }

    public static Color getConsoleOutlineColor() {
        return withAlpha(Color.WHITE, 25);
    }

    public static Font getConsoleLikeFont(float size) {
        return getConsoleLikeFont(Font.PLAIN, size);
    }

    public static Font getConsoleLikeFont(int style, float size) {
        String family = resolveConsoleLikeFontFamily();
        return new Font(family, style, Math.round(size)).deriveFont(style, size);
    }

    private static String resolveConsoleLikeFontFamily() {
        String[] preferred = {
                "Consolas",
                "Cascadia Mono",
                "JetBrains Mono",
                "Lucida Console",
                "Menlo",
                "Monaco",
                "DejaVu Sans Mono",
                "Liberation Mono",
                Font.MONOSPACED
        };
        try {
            String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            for (String candidate : preferred) {
                for (String family : available) {
                    if (family.equalsIgnoreCase(candidate)) {
                        return family;
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return Font.MONOSPACED;
    }

    public static Color getSuccessColor() {
        return DEFAULT_SUCCESS;
    }

    public static Color getDangerColor() {
        return DEFAULT_DANGER;
    }

    public static Color getWarningColor() {
        return DEFAULT_WARNING;
    }

    public static Color getDestructiveColor() {
        return Color.RED;
    }

    public static Color getTransparentColor() {
        return new Color(0, 0, 0, 0);
    }

    public static Color getImageHoverOverlayColor() {
        return withAlpha(Color.BLACK, 90);
    }

    public static Color getImageHoverTextColor() {
        return withAlpha(Color.WHITE, 230);
    }

    public static boolean isLightTheme() {
        Color panel = getPanelBackground();
        float luminance = (0.299f * panel.getRed() + 0.587f * panel.getGreen() + 0.114f * panel.getBlue()) / 255f;
        return luminance >= 0.5f;
    }

    public static Color getCropBackground() {
        return Color.DARK_GRAY;
    }

    public static Color getCropOverlayColor() {
        return withAlpha(Color.BLACK, 120);
    }

    public static Color getCropSelectionBorderColor() {
        return withAlpha(Color.WHITE, 220);
    }

    public static Color getCropHandleColor() {
        return withAlpha(Color.WHITE, 240);
    }

    public static void applyCardTitleStyle(JLabel label) {
        if (label == null) return;
        label.setFont(label.getFont().deriveFont(Font.BOLD, Math.max(14f, label.getFont().getSize2D())));
        label.setForeground(getCardTitleColor());
    }

    public static Color getCardTitleColor() {
        Color panel = getPanelBackground();
        float luminance = (0.299f * panel.getRed() + 0.587f * panel.getGreen() + 0.114f * panel.getBlue()) / 255f;
        return luminance >= 0.5f
                ? darken(getForeground(), 0.18f)
                : tint(getForeground(), Color.BLACK, 0.22f);
    }

    public static void applyActionButtonStyle(AbstractButton button) {
        if (button == null) return;
        button.setFocusPainted(false);
        applyHandCursor(button);
        button.setOpaque(false);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setBorder(createRoundedBorder(new Insets(6, 12, 6, 12), 1f));
        button.setBackground(getSurfaceBackground());
        button.setForeground(getForeground());
    }

    public static void applyHeaderIconButtonStyle(AbstractButton button) {
        if (button == null) return;
        button.setText(null);
        button.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setVerticalAlignment(SwingConstants.CENTER);
        button.setIconTextGap(0);
    }

    public static void configureHeaderIconButton(
            AbstractButton button,
            String tooltip,
            String iconPath,
            int iconSize,
            Supplier<Color> colorSupplier
    ) {
        if (button == null) return;
        applyHeaderIconButtonStyle(button);
        button.setToolTipText(tooltip);
        SvgIconFactory.apply(button, iconPath, iconSize, iconSize, colorSupplier);
    }

    public static void configureDebugIconButton(
            AbstractButton button,
            String tooltip,
            String iconPath,
            Runnable action
    ) {
        configureHeaderIconButton(button, tooltip, iconPath, 18, AppTheme::getForeground);
        if (button == null || action == null) return;
        button.addActionListener(e -> {
            if (DebugMode.isEnabled()) {
                action.run();
            }
        });
    }

    public static void configureRowIconActionButton(
            AbstractButton button,
            String tooltip,
            String iconPath,
            int iconSize,
            Supplier<Color> colorSupplier,
            boolean prominent
    ) {
        if (button == null) return;
        applyHeaderIconButtonStyle(button);
        button.setToolTipText(tooltip);
        button.setIcon(SvgIconFactory.create(iconPath, iconSize, iconSize, colorSupplier));

        int buttonSize = prominent ? 54 : 38;
        Dimension size = new Dimension(buttonSize, buttonSize);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setBackground(getBackground());
        button.setBorder(createRoundedBorder(new Insets(6, 6, 6, 6), getSubtleBorderColor(), 1f));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!button.isEnabled()) return;
                button.setBackground(getSoftSelectionBackground());
                button.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(getBackground());
                button.repaint();
            }
        });
    }

    // Texto oscuro para botones de color claro (verde, amarillo).
    static final Color DARK_TEXT_ON_COLOR = new Color(27, 38, 30);

    /**
     * Blanco si se lee bien sobre el fondo (contraste 3:1, suficiente para texto grande en negrita);
     * si no, un texto casi negro. El blanco sobre el verde de Iniciar daba 1,35:1.
     */
    public static Color getReadableForegroundOn(Color background) {
        if (background == null) return Color.WHITE;
        return contrastRatio(background, Color.WHITE) >= 3.0 ? Color.WHITE : DARK_TEXT_ON_COLOR;
    }

    static double contrastRatio(Color a, Color b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double relativeLuminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int value) {
        double v = value / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    public static void applyLargeControlButtonStyle(AbstractButton button, Icon icon) {
        if (button == null) return;
        button.setForeground(getReadableForegroundOn(button.getBackground()));
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setVerticalTextPosition(SwingConstants.CENTER);
        button.setIconTextGap(8);
        button.setFont(button.getFont().deriveFont(Font.BOLD, button.getFont().getSize2D() + 5f));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(true);
        button.putClientProperty("JButton.arc", getArc());
        button.setIcon(icon);
    }

    public static void applyRefreshIconButtonStyle(AbstractButton button) {
        if (button == null) return;
        applyHeaderIconButtonStyle(button);

        SvgIconFactory.RotatingIcon rotatingIcon = SvgIconFactory.createRotating(
                "doraicons/refresh.svg",
                18,
                18,
                AppTheme::getForeground
        );
        button.putClientProperty(PROP_REFRESH_ICON, rotatingIcon);
        button.setIcon(rotatingIcon);

        Timer existingTimer = (Timer) button.getClientProperty(PROP_REFRESH_TIMER);
        if (existingTimer != null) {
            existingTimer.stop();
        }

        Timer timer = new Timer(REFRESH_ROTATION_TIMER_MS, e -> {
            SvgIconFactory.RotatingIcon icon = (SvgIconFactory.RotatingIcon) button.getClientProperty(PROP_REFRESH_ICON);
            if (icon == null) {
                ((Timer) e.getSource()).stop();
                return;
            }

            boolean hovered = Boolean.TRUE.equals(button.getClientProperty(PROP_REFRESH_HOVER));
            if (!hovered) {
                icon.setAngleRadians(0d);
                storeIconAngle(button, 0d);
                button.repaint();
                ((Timer) e.getSource()).stop();
                return;
            }

            double currentAngle = iconAngle(button) + REFRESH_ROTATION_STEP_RADIANS;
            icon.setAngleRadians(currentAngle);
            storeIconAngle(button, currentAngle);
            button.repaint();
        });
        button.putClientProperty(PROP_REFRESH_TIMER, timer);

        if (!Boolean.TRUE.equals(button.getClientProperty(PROP_REFRESH_INSTALLED))) {
            button.putClientProperty(PROP_REFRESH_INSTALLED, Boolean.TRUE);
            button.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    button.putClientProperty(PROP_REFRESH_HOVER, Boolean.TRUE);
                    Timer hoverTimer = (Timer) button.getClientProperty(PROP_REFRESH_TIMER);
                    if (hoverTimer != null && !hoverTimer.isRunning()) {
                        hoverTimer.start();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    button.putClientProperty(PROP_REFRESH_HOVER, Boolean.FALSE);
                    Timer hoverTimer = (Timer) button.getClientProperty(PROP_REFRESH_TIMER);
                    if (hoverTimer != null && !hoverTimer.isRunning()) {
                        hoverTimer.start();
                    }
                }
            });
        }
    }

    public static void applyAccentButtonStyle(AbstractButton button) {
        if (button == null) return;
        applyActionButtonStyle(button);
        button.setBackground(getMainAccent());
        button.setForeground(Color.WHITE);
        button.setBorder(createAccentBorder(new Insets(6, 12, 6, 12), 1f));
    }

    public static void applySurfacePreviewStyle(JComponent component, Insets padding) {
        if (component == null) return;
        Insets resolvedPadding = padding != null ? padding : new Insets(10, 10, 10, 10);
        component.setOpaque(true);
        component.setBackground(getSurfaceBackground());
        Border border = createRoundedBorder(resolvedPadding, 1f);
        component.setBorder(border);
    }

    public static JProgressBar createLoadingProgressBar(boolean indeterminate) {
        JProgressBar progressBar = new JProgressBar();
        applyLoadingProgressBarStyle(progressBar, indeterminate);
        return progressBar;
    }

    public static void applyLoadingProgressBarStyle(JProgressBar progressBar, boolean indeterminate) {
        if (progressBar == null) return;
        progressBar.setMinimum(0);
        progressBar.setMaximum(100);
        progressBar.setValue(0);
        progressBar.setIndeterminate(indeterminate);
        progressBar.setStringPainted(false);
        progressBar.setForeground(getMainAccent());
        progressBar.setBackground(getSurfaceBackground());
        progressBar.setBorder(BorderFactory.createEmptyBorder());
        progressBar.setPreferredSize(new Dimension(1, 10));
        progressBar.putClientProperty("JProgressBar.largeHeight", Boolean.TRUE);
    }

    public static void applyHandCursor(Component component) {
        if (component == null) return;
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public static void applyStandardScrollSpeed(JScrollPane scrollPane) {
        applyStandardScrollSpeed(scrollPane, 24, 96);
    }

    public static void applyStandardScrollSpeed(JScrollPane scrollPane, int unitIncrement, int blockIncrement) {
        if (scrollPane == null) return;
        if (scrollPane.getVerticalScrollBar() != null) {
            scrollPane.getVerticalScrollBar().setUnitIncrement(Math.max(1, unitIncrement));
            scrollPane.getVerticalScrollBar().setBlockIncrement(Math.max(1, blockIncrement));
        }
        if (scrollPane.getHorizontalScrollBar() != null) {
            scrollPane.getHorizontalScrollBar().setUnitIncrement(Math.max(1, unitIncrement));
            scrollPane.getHorizontalScrollBar().setBlockIncrement(Math.max(1, blockIncrement));
        }
    }

    public static Color getHoverBackground() {
        return darken(getPanelBackground(), 0.06f);
    }

    public static Color getPressedBackground() {
        return darken(getPanelBackground(), 0.12f);
    }

    public static Color getSelectionBackground() {
        return tint(getPanelBackground(), getMainAccent(), 0.18f);
    }

    public static Color getSoftSelectionBackground() {
        return tint(getPanelBackground(), getMainAccent(), 0.10f);
    }

    public static Color tint(Color base, Color accent, float t) {
        if (base == null) base = getPanelBackground();
        if (accent == null) accent = getMainAccent();
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (base.getRed() + (accent.getRed() - base.getRed()) * t);
        int g = (int) (base.getGreen() + (accent.getGreen() - base.getGreen()) * t);
        int b = (int) (base.getBlue() + (accent.getBlue() - base.getBlue()) * t);
        return new Color(r, g, b);
    }

    public static Color darken(Color color, float amount) {
        if (color == null) color = getPanelBackground();
        amount = Math.max(0f, Math.min(1f, amount));
        int r = (int) (color.getRed() * (1f - amount));
        int g = (int) (color.getGreen() * (1f - amount));
        int b = (int) (color.getBlue() * (1f - amount));
        return new Color(r, g, b);
    }

    public static Color withAlpha(Color color, int alpha) {
        Color base = color != null ? color : getForeground();
        int a = Math.max(0, Math.min(255, alpha));
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
    }

    public static FlatLineBorder createRoundedBorder(Insets insets, float thickness) {
        return new FlatLineBorder(insets, getBorderColor(), thickness, getArc());
    }

    public static FlatLineBorder createRoundedBorder(Insets insets, Color color, float thickness) {
        return new FlatLineBorder(insets, color, thickness, getArc());
    }

    public static FlatLineBorder createRoundedBorder(Insets insets, Color color, float thickness, int arc) {
        int resolvedArc = arc > 0 ? arc : getArc();
        return new FlatLineBorder(insets, color, thickness, resolvedArc);
    }

    public static FlatLineBorder createAccentBorder(Insets insets, float thickness) {
        return createRoundedBorder(insets, getMainAccent(), thickness);
    }

    private static double iconAngle(AbstractButton button) {
        Object value = button.getClientProperty("dora.refreshAngle");
        return value instanceof Number number ? number.doubleValue() : 0d;
    }

    private static void storeIconAngle(AbstractButton button, double angle) {
        button.putClientProperty("dora.refreshAngle", angle);
    }
}
