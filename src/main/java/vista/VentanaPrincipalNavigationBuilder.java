package vista;

import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.ui.FlatLineBorder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

final class VentanaPrincipalNavigationBuilder {
    static final String NAV_ICON_PATH_PROPERTY = "navIconPath";
    static final String NAV_ICON_UNSELECTED_PATH_PROPERTY = "navIconUnselectedPath";
    static final String NAV_SELECTED_PROPERTY = "navSelected";
    static final float NAV_UNSELECTED_OPACITY = 0.70f;

    record Result(JPanel panel, Map<VentanaPrincipal.PaginaDerecha, JButton> navButtons) {
    }

    Result build(
            Consumer<VentanaPrincipal.PaginaDerecha> pageNavigator,
            Runnable openThemeSelector,
            Consumer<JButton> selectionRestorer
    ) {
        CardPanel barra = new CardPanel("");
        barra.setBackground(AppTheme.getPanelBackground());
        barra.setPreferredSize(new Dimension(56, 0));

        Map<VentanaPrincipal.PaginaDerecha, JButton> navButtons = new EnumMap<>(VentanaPrincipal.PaginaDerecha.class);

        JPanel botones = new JPanel();
        botones.setOpaque(false);
        botones.setLayout(new BoxLayout(botones, BoxLayout.Y_AXIS));

        JButton home = crearNavButton("doraicons/home.svg", "Home", VentanaPrincipal.PaginaDerecha.HOME, pageNavigator, navButtons);
        JButton mundo = crearNavButton("doraicons/earth.svg", "Mundos", VentanaPrincipal.PaginaDerecha.MUNDO, pageNavigator, navButtons);
        JButton config = crearNavButton("doraicons/settings.svg", "Configuración del servidor", VentanaPrincipal.PaginaDerecha.CONFIG, pageNavigator, navButtons);
        JButton stats = crearNavButton("doraicons/chart.svg", "Estadísticas", VentanaPrincipal.PaginaDerecha.STATS, pageNavigator, navButtons);
        JButton automation = crearNavButton("doraicons/clock.svg", "Automatización", VentanaPrincipal.PaginaDerecha.AUTOMATION, pageNavigator, navButtons);
        JButton extensiones = crearNavButton("doraicons/box.svg", "Extensiones", VentanaPrincipal.PaginaDerecha.EXTENSIONES, pageNavigator, navButtons);
        JButton temas = crearActionButton("doraicons/pallete-unselected.svg", "Temas", openThemeSelector);
        JButton info = crearNavButton("doraicons/info.svg", "Información", VentanaPrincipal.PaginaDerecha.INFO, pageNavigator, navButtons);

        botones.add(home);
        botones.add(Box.createVerticalStrut(8));
        botones.add(mundo);
        botones.add(Box.createVerticalStrut(8));
        botones.add(config);
        botones.add(Box.createVerticalStrut(8));
        botones.add(automation);
        botones.add(Box.createVerticalStrut(8));
        botones.add(stats);
        botones.add(Box.createVerticalStrut(8));
        botones.add(extensiones);
        botones.add(Box.createVerticalGlue());
        botones.add(temas);
        botones.add(Box.createVerticalStrut(6));
        botones.add(info);
        botones.add(Box.createVerticalStrut(4));

        MouseAdapter navHover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (e.getSource() instanceof JButton button) {
                    aplicarHover(button);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (selectionRestorer != null && e.getSource() instanceof JButton button) {
                    selectionRestorer.accept(button);
                }
            }
        };
        home.addMouseListener(navHover);
        mundo.addMouseListener(navHover);
        config.addMouseListener(navHover);
        stats.addMouseListener(navHover);
        automation.addMouseListener(navHover);
        extensiones.addMouseListener(navHover);
        info.addMouseListener(navHover);

        temas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                aplicarHover(temas);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                restaurarIconoBase(temas);
                restaurarEstadoBase(temas);
                temas.repaint();
            }
        });

        barra.getContentPanel().add(botones, BorderLayout.CENTER);
        return new Result(barra, navButtons);
    }

    private JButton crearNavButton(
            String iconPath,
            String tooltip,
            VentanaPrincipal.PaginaDerecha pagina,
            Consumer<VentanaPrincipal.PaginaDerecha> pageNavigator,
            Map<VentanaPrincipal.PaginaDerecha, JButton> navButtons
    ) {
        FlatButton button = createBaseButton(iconPath, tooltip);
        button.addActionListener(e -> {
            if (pageNavigator != null) {
                pageNavigator.accept(pagina);
            }
        });
        navButtons.put(pagina, button);
        return button;
    }

    private JButton crearActionButton(String iconPath, String tooltip, Runnable action) {
        FlatButton button = createBaseButton(iconPath, tooltip);
        button.addActionListener(e -> {
            if (action != null) {
                action.run();
            }
        });
        return button;
    }

    private FlatButton createBaseButton(String iconPath, String tooltip) {
        FlatButton button = new FlatButton();
        button.putClientProperty(NAV_ICON_PATH_PROPERTY, iconPath);
        button.putClientProperty(NAV_ICON_UNSELECTED_PATH_PROPERTY, toUnselectedIconPath(iconPath));
        button.putClientProperty(NAV_SELECTED_PROPERTY, Boolean.FALSE);
        button.setIcon(SvgIconFactory.createWithOpacity(toUnselectedIconPath(iconPath), 32, 32, NAV_UNSELECTED_OPACITY));
        button.setFocusPainted(false);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        button.setToolTipText(tooltip);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 18f));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorderPainted(true);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorder(new FlatLineBorder(new Insets(6, 6, 6, 6), AppTheme.getTransparentColor(), 1f, AppTheme.getArc()));
        button.putClientProperty("JButton.buttonType", "roundRect");
        return button;
    }

    static void actualizarIconoNavegacion(AbstractButton button, boolean selected) {
        if (button == null) {
            return;
        }
        button.putClientProperty(NAV_SELECTED_PROPERTY, selected);
        Object selectedPath = button.getClientProperty(NAV_ICON_PATH_PROPERTY);
        Object unselectedPath = button.getClientProperty(NAV_ICON_UNSELECTED_PATH_PROPERTY);
        if (!(selectedPath instanceof String iconPath) || !(unselectedPath instanceof String unselectedIconPath)) {
            return;
        }
        button.setIcon(SvgIconFactory.createWithOpacity(
                selected ? iconPath : unselectedIconPath,
                32,
                32,
                selected ? 1f : NAV_UNSELECTED_OPACITY
        ));
        restaurarEstadoBase(button);
    }

    private static String toUnselectedIconPath(String iconPath) {
        if (iconPath == null || iconPath.isBlank() || iconPath.endsWith("-unselected.svg")) {
            return iconPath;
        }
        int extensionIndex = iconPath.lastIndexOf(".svg");
        if (extensionIndex < 0) {
            return iconPath + "-unselected";
        }
        return iconPath.substring(0, extensionIndex) + "-unselected.svg";
    }


    private void aplicarHover(JButton button) {
        Object selectedPath = button.getClientProperty(NAV_ICON_PATH_PROPERTY);
        Object unselectedPath = button.getClientProperty(NAV_ICON_UNSELECTED_PATH_PROPERTY);
        if (!(selectedPath instanceof String iconPath) || !(unselectedPath instanceof String unselectedIconPath)) {
            return;
        }
        button.setIcon(SvgIconFactory.createWithOpacity(
                isSelected(button) ? iconPath : unselectedIconPath,
                32,
                32,
                1f
        ));
        restaurarEstadoBase(button);
        button.repaint();
    }

    private static boolean isSelected(AbstractButton button) {
        return Boolean.TRUE.equals(button.getClientProperty(NAV_SELECTED_PROPERTY));
    }

    private static void restaurarIconoBase(AbstractButton button) {
        Object selectedPath = button.getClientProperty(NAV_ICON_PATH_PROPERTY);
        Object unselectedPath = button.getClientProperty(NAV_ICON_UNSELECTED_PATH_PROPERTY);
        if (!(selectedPath instanceof String iconPath) || !(unselectedPath instanceof String unselectedIconPath)) {
            return;
        }
        boolean selected = isSelected(button);
        button.setIcon(SvgIconFactory.createWithOpacity(
                selected ? iconPath : unselectedIconPath,
                32,
                32,
                selected ? 1f : NAV_UNSELECTED_OPACITY
        ));
    }

    private static void restaurarEstadoBase(AbstractButton button) {
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBackground(null);
        button.setBorder(new FlatLineBorder(new Insets(6, 6, 6, 6), AppTheme.getTransparentColor(), 1f, AppTheme.getArc()));
    }
}



