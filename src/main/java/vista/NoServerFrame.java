/*
 * Fichero: NoServerFrame.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Esta clase extiende de JFrame y muestra al usuario la ventana de bienvenida cuando no tiene ningún servidor guardado.
 * Muestra información del creador y permite importar y crear un servidor.
 * */

package vista;

import com.formdev.flatlaf.extras.components.FlatButton;
import controlador.GestorServidores;
import modelo.Server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;

public class NoServerFrame extends JFrame {

    private static final int LEFT_PANEL_WIDTH = 286;
    private static final int ACTION_BUTTON_TARGET_WIDTH = 170;
    private static final int ACTION_BUTTON_TARGET_HEIGHT = 42;
    private static final int LINK_BUTTON_TARGET_WIDTH = 220;
    private static final int LINK_BUTTON_TARGET_HEIGHT = 38;
    private static final Insets OUTER_INSETS = new Insets(8, 8, 8, 8);
    private static final Insets SURFACE_INSETS = new Insets(0, 8, 8, 8);
    private static final int WRAP_FALLBACK_WIDTH = 120;
    private static final int TEXT_MAX_WIDTH = 520;

    public static ImageIcon logo;

    public NoServerFrame(GestorServidores gestorServidores) {
        this.setTitle("Dora - Minecraft Server Manager");
        this.setSize(920, 620);
        this.setMinimumSize(new Dimension(760, 540));
        this.setLocationRelativeTo(null);
        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                intentarCerrarAplicacion(gestorServidores);
            }
        });

        logo = cargarLogo(128);
        setContentPane(crearContenido(gestorServidores));
    }

    private ImageIcon cargarLogo(int size) {
        URL urlImagen = getClass().getResource("/doraapp/logo.png");
        if (urlImagen == null) {
            throw new RuntimeException("No se ha encontrado el logo");
        }
        ImageIcon icon = new ImageIcon(urlImagen);
        Image imagen = icon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(imagen);
    }

    private JPanel crearContenido(GestorServidores gestorServidores) {
        JPanel panelPrincipal = new JPanel(new BorderLayout(8, 0));
        panelPrincipal.setBackground(AppTheme.getBackground());
        panelPrincipal.setBorder(BorderFactory.createEmptyBorder(
                OUTER_INSETS.top,
                OUTER_INSETS.left,
                OUTER_INSETS.bottom,
                OUTER_INSETS.right
        ));

        panelPrincipal.add(crearPanelMarca(), BorderLayout.WEST);
        panelPrincipal.add(crearPanelInicio(gestorServidores), BorderLayout.CENTER);
        return panelPrincipal;
    }

    private JPanel crearPanelMarca() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(true);
        panel.setBackground(AppTheme.getPanelBackground());
        panel.setPreferredSize(new Dimension(LEFT_PANEL_WIDTH, 1));
        panel.setMinimumSize(new Dimension(240, 0));
        panel.setBorder(AppTheme.createRoundedBorder(SURFACE_INSETS, AppTheme.getBorderColor(), 1f));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        JLabel logoLabel = new JLabel(logo);
        c.insets = new Insets(0, 0, 20, 0);
        panel.add(logoLabel, c);

        JLabel nombreAppLabel = new JLabel("Dora");
        nombreAppLabel.setFont(nombreAppLabel.getFont().deriveFont(Font.BOLD, 38f));
        nombreAppLabel.setForeground(AppTheme.getCardTitleColor());
        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        panel.add(nombreAppLabel, c);

        JLabel subtituloLabel = new JLabel("Minecraft Server Manager");
        subtituloLabel.setFont(subtituloLabel.getFont().deriveFont(Font.PLAIN, 14f));
        subtituloLabel.setForeground(AppTheme.getMutedForeground());
        c.gridy++;
        c.insets = new Insets(0, 0, 26, 0);
        panel.add(subtituloLabel, c);

        JComponent autorLabel = crearTextoEnvolvente(
                "Por Manuel Enrique<br>Jerónimo Aragón",
                13.5f,
                AppTheme.getForeground(),
                Font.PLAIN,
                SwingConstants.LEFT
        );
        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        panel.add(autorLabel, c);

        JComponent descripcionLabel = crearTextoEnvolvente(
                "Gestiona tus servidores de Minecraft de forma fácil y rápida con Dora, la aplicación todo en uno para administrar tus mundos, jugadores, consola, extensiones y configuración desde una interfaz moderna y sencilla.",
                12.5f,
                AppTheme.getMutedForeground(),
                Font.PLAIN,
                SwingConstants.LEFT
        );
        c.gridy++;
        c.insets = new Insets(14, 0, 0, 0);
        panel.add(descripcionLabel, c);

        c.gridy++;
        c.weighty = 1.0;
        c.insets = new Insets(0, 0, 0, 0);
        c.fill = GridBagConstraints.BOTH;
        panel.add(Box.createVerticalGlue(), c);

        JPanel enlaces = new JPanel();
        enlaces.setOpaque(false);
        enlaces.setLayout(new BoxLayout(enlaces, BoxLayout.Y_AXIS));
        enlaces.add(crearLinkButton("Ko-fi", "doraicons/ko-fi.svg", DoraAboutLinks.KOFI_URL, "Abrir Ko-fi"));
        enlaces.add(Box.createVerticalStrut(8));
        enlaces.add(crearLinkButton("GitHub", "doraicons/github.svg", DoraAboutLinks.GITHUB_URL, "Abrir repositorio de Dora"));
        enlaces.add(Box.createVerticalStrut(8));
        enlaces.add(crearLinkButton("LinkedIn", "doraicons/linkedin.svg", DoraAboutLinks.LINKEDIN_URL, "Abrir perfil de LinkedIn"));
        enlaces.add(Box.createVerticalStrut(8));
        enlaces.add(crearLinkButton("Reddit", "doraicons/reddit.svg", DoraAboutLinks.REDDIT_URL, "Abrir perfil de Reddit"));

        c.gridy++;
        c.weighty = 0.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.SOUTHWEST;
        panel.add(enlaces, c);
        return panel;
    }

    private JPanel crearPanelInicio(GestorServidores gestorServidores) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(true);
        panel.setBackground(AppTheme.getPanelBackground());
        panel.setBorder(AppTheme.createRoundedBorder(SURFACE_INSETS, AppTheme.getBorderColor(), 1f));

        JPanel contenido = new JPanel(new GridBagLayout());
        contenido.setOpaque(false);
        GridBagConstraints contentC = new GridBagConstraints();
        contentC.gridx = 0;
        contentC.weightx = 1.0;
        contentC.fill = GridBagConstraints.HORIZONTAL;
        contentC.anchor = GridBagConstraints.CENTER;

        JComponent encabezado = crearTextoEnvolvente(
                "Prepara tu primer servidor con Dora",
                30f,
                AppTheme.getCardTitleColor(),
                Font.BOLD,
                SwingConstants.CENTER
        );
        contentC.gridy = 0;
        contentC.insets = new Insets(0, 0, 0, 0);
        contenido.add(encabezado, contentC);

        JComponent descripcion = crearTextoEnvolvente(
                "Todavía no hay servidores configurados. Crea un servidor nuevo o importa una carpeta existente para empezar a gestionar mundos, consola, jugadores, extensiones y configuración desde un solo lugar.",
                14f,
                AppTheme.getMutedForeground(),
                Font.PLAIN,
                SwingConstants.CENTER
        );
        contentC.gridy++;
        contentC.insets = new Insets(16, 0, 28, 0);
        contenido.add(descripcion, contentC);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        acciones.setOpaque(false);

        JButton crearNuevo = crearActionButton("Crear servidor", "doraicons/plus.svg", true);
        crearNuevo.addActionListener(e -> abrirServidorCreado(gestorServidores.crearServidor(), gestorServidores));
        acciones.add(crearNuevo);

        JButton importar = crearActionButton("Importar", "doraicons/download.svg", false);
        importar.addActionListener(e -> abrirServidorCreado(gestorServidores.importarServidor(), gestorServidores));
        acciones.add(importar);
        contentC.gridy++;
        contentC.insets = new Insets(0, 0, 0, 0);
        contentC.fill = GridBagConstraints.NONE;
        contenido.add(acciones, contentC);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1.0;
        c.weighty = 1.0;
        c.anchor = GridBagConstraints.CENTER;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(contenido, c);
        return panel;
    }

    private JComponent crearTextoEnvolvente(String text, float size, Color color, int style, int horizontalAlignment) {
        WrappedTextBlock block = new WrappedTextBlock(text, color, horizontalAlignment);
        Font baseFont = UIManager.getFont("Label.font");
        if (baseFont == null) {
            baseFont = new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size));
        }
        block.setFont(baseFont.deriveFont(style, size));
        return block;
    }

    private static final class WrappedTextBlock extends JComponent {
        private final String rawText;
        private final int textAlignment;
        private final Color textColor;
        private int lastLayoutWidth = -1;

        private WrappedTextBlock(String text, Color textColor, int textAlignment) {
            this.rawText = text == null ? "" : text.replace("<br>", "\n");
            this.textColor = textColor;
            this.textAlignment = textAlignment;
            setOpaque(false);
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    int width = getWidth();
                    if (width != lastLayoutWidth) {
                        lastLayoutWidth = width;
                        revalidate();
                        repaint();
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return measure(resolveMeasureWidth());
        }

        @Override
        public Dimension getMinimumSize() {
            return measure(WRAP_FALLBACK_WIDTH);
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension preferred = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, preferred.height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font font = getFont();
            if (font == null) {
                font = UIManager.getFont("Label.font");
            }
            if (font == null) {
                font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            }
            g2.setFont(font);
            g2.setColor(textColor != null ? textColor : AppTheme.getForeground());

            Insets insets = getInsets();
            FontMetrics metrics = g2.getFontMetrics();
            int componentWidth = Math.max(1, getWidth() - insets.left - insets.right);
            int availableWidth = Math.max(1, Math.min(TEXT_MAX_WIDTH, componentWidth));
            int contentLeft = insets.left + Math.max(0, (componentWidth - availableWidth) / 2);
            int y = insets.top + metrics.getAscent();
            for (String line : wrapLines(metrics, availableWidth)) {
                int lineWidth = metrics.stringWidth(line);
                int x = switch (textAlignment) {
                    case SwingConstants.CENTER -> contentLeft + Math.max(0, (availableWidth - lineWidth) / 2);
                    case SwingConstants.RIGHT -> contentLeft + availableWidth - lineWidth;
                    default -> contentLeft;
                };
                g2.drawString(line, x, y);
                y += metrics.getHeight();
            }
            g2.dispose();
        }

        private int resolveMeasureWidth() {
            int width = getWidth();
            if (width <= 0 && getParent() != null) {
                width = getParent().getWidth();
            }
            Insets insets = getInsets();
            width -= insets.left + insets.right;
            if (width <= 0) {
                width = TEXT_MAX_WIDTH;
            }
            return Math.max(WRAP_FALLBACK_WIDTH, Math.min(TEXT_MAX_WIDTH, width));
        }

        private Dimension measure(int width) {
            Insets insets = getInsets();
            Font font = getFont();
            if (font == null) {
                font = UIManager.getFont("Label.font");
            }
            if (font == null) {
                font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            }
            FontMetrics metrics = getFontMetrics(font);
            int availableWidth = Math.max(1, width - insets.left - insets.right);
            int lineCount = Math.max(1, wrapLines(metrics, availableWidth).size());
            return new Dimension(width, insets.top + insets.bottom + lineCount * metrics.getHeight());
        }

        private java.util.List<String> wrapLines(FontMetrics metrics, int availableWidth) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            for (String paragraph : rawText.split("\\R", -1)) {
                if (paragraph.isBlank()) {
                    lines.add("");
                    continue;
                }
                StringBuilder current = new StringBuilder();
                for (String word : paragraph.trim().split("\\s+")) {
                    if (word.isEmpty()) continue;
                    if (current.isEmpty()) {
                        appendWord(lines, current, word, metrics, availableWidth);
                        continue;
                    }
                    String candidate = current + " " + word;
                    if (metrics.stringWidth(candidate) <= availableWidth) {
                        current.append(' ').append(word);
                    } else {
                        lines.add(current.toString());
                        current.setLength(0);
                        appendWord(lines, current, word, metrics, availableWidth);
                    }
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
            }
            return lines;
        }

        private void appendWord(java.util.List<String> lines, StringBuilder current, String word, FontMetrics metrics, int availableWidth) {
            if (metrics.stringWidth(word) <= availableWidth) {
                current.append(word);
                return;
            }
            StringBuilder fragment = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                String candidate = fragment.toString() + word.charAt(i);
                if (!fragment.isEmpty() && metrics.stringWidth(candidate) > availableWidth) {
                    lines.add(fragment.toString());
                    fragment.setLength(0);
                }
                fragment.append(word.charAt(i));
            }
            current.append(fragment);
        }
    }

    private JButton crearActionButton(String text, String iconPath, boolean primary) {
        JButton button = new FlatButton();
        button.setText(text);
        button.setIcon(SvgIconFactory.create(iconPath, 18, 18, primary ? () -> Color.WHITE : AppTheme::getForeground));
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setIconTextGap(8);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13.5f));
        if (primary) {
            AppTheme.applyAccentButtonStyle(button);
        } else {
            AppTheme.applyActionButtonStyle(button);
        }
        Dimension preferred = button.getPreferredSize();
        Dimension size = new Dimension(
                Math.max(ACTION_BUTTON_TARGET_WIDTH, preferred.width),
                Math.max(ACTION_BUTTON_TARGET_HEIGHT, preferred.height)
        );
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        return button;
    }

    private JButton crearLinkButton(String text, String iconPath, String url, String tooltip) {
        JButton button = new FlatButton();
        button.setText(text);
        button.setToolTipText(tooltip);
        button.setIcon(SvgIconFactory.createOriginal(iconPath, 20, 20));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setIconTextGap(10);
        AppTheme.applyActionButtonStyle(button);
        Dimension preferred = button.getPreferredSize();
        Dimension size = new Dimension(
                Math.max(LINK_BUTTON_TARGET_WIDTH, preferred.width),
                Math.max(LINK_BUTTON_TARGET_HEIGHT, preferred.height)
        );
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, size.height));
        button.addActionListener(e -> abrirEnlace(url));
        return button;
    }

    private void abrirServidorCreado(Server server, GestorServidores gestorServidores) {
        if (server == null) {
            return;
        }
        VentanaPrincipal ventanaPrincipal = new VentanaPrincipal(gestorServidores, server);
        ventanaPrincipal.setVisible(true);
        gestorServidores.mostrarAvisoArranqueSiProcede(ventanaPrincipal);
        this.dispose();
    }

    private void abrirEnlace(String url) {
        if (url == null || url.isBlank() || !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            JOptionPane.showMessageDialog(this, "No se ha podido abrir el enlace.", "Abrir enlace", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IllegalArgumentException | IOException ex) {
            JOptionPane.showMessageDialog(this, "No se ha podido abrir el enlace.", "Abrir enlace", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void intentarCerrarAplicacion(GestorServidores gestorServidores) {
        List<Server> activos = gestorServidores.getServidoresActivos();
        if (activos.isEmpty()) {
            this.dispose();
            System.exit(0);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Hay servidores activos.\n\n");
        for (Server s : activos) {
            String nombre = (s.getDisplayName() == null || s.getDisplayName().isBlank()) ? s.getId() : s.getDisplayName();
            sb.append("- ").append(nombre).append("\n");
        }
        sb.append("\n¿Quieres cerrarlos antes de salir?");

        Object[] opciones = {"Cerrar servidores y salir", "Cancelar"};
        int res = JOptionPane.showOptionDialog(
                this,
                sb.toString(),
                "Servidores activos",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                opciones,
                opciones[0]
        );
        if (res != 0) return;

        SalidaServidoresActivos.detenerYSalir(this, gestorServidores, () -> {
            try {
                NoServerFrame.this.dispose();
            } finally {
                System.exit(0);
            }
        });
    }
}
