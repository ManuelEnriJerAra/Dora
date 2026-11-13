package vista;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import javax.swing.border.Border;

import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.extras.components.FlatScrollPane;
import com.formdev.flatlaf.extras.components.FlatTextField;

import controlador.GestorServidores;
import controlador.IPLocal;
import controlador.IPPublica;
import controlador.Utilidades;
import controlador.world.WorldPreviewCatalogService;
import controlador.world.WorldPreviewOption;
import modelo.Server;

@SuppressWarnings("unused")
public class PanelPrevisualizacion extends JPanel {
    private static final String[] DORA_IMAGE_RESOURCES = {
            "doraimages/default_image.png",
            "doraimages/dora.png"
    };

    private final GestorServidores gestorServidores;

    private Server server = new Server();

    PanelPrevisualizacion(GestorServidores gestorServidores){
        this.gestorServidores = gestorServidores;
        this.server = gestorServidores.getServidorSeleccionado();
        this.setLayout(new BorderLayout());
        this.setOpaque(false);

        ImageIcon icono = server.getServerIconOrUseDefault();
        ImagenRedondaLabel iconoRedondo = new ImagenRedondaLabel(icono, 10, 96, 96);
        AppTheme.applyHandCursor(iconoRedondo);
        iconoRedondo.setToolTipText("Cambiar icono del servidor");
        iconoRedondo.setHoverOverlayEnabled(true);
        iconoRedondo.setHoverIcon(SvgIconFactory.create(
                "doraicons/gallery-edit.svg",
                80,
                80,
                AppTheme::getImageHoverTextColor
        ));
        iconoRedondo.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(!SwingUtilities.isLeftMouseButton(e)) return;
                cambiarIconoServidor(gestorServidores, server, iconoRedondo);
            }
        });

        JPanel iconWrap = new JPanel(new GridBagLayout());
        iconWrap.setOpaque(false);
        iconWrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        iconWrap.add(iconoRedondo);
        this.add(iconWrap, BorderLayout.WEST);

        JPanel panelDatos = new JPanel();
        panelDatos.setOpaque(false);
        panelDatos.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        panelDatos.setLayout(new BoxLayout(panelDatos, BoxLayout.Y_AXIS));

        String nombre = server.getDisplayName();
        String motdRaw = Utilidades.leerMotdDesdeProperties(Path.of(server.getServerDir()));
        String motd = MotdRenderUtil.stripCodes(motdRaw);
        if(motd != null){
            motd = motd.replace("\r", "");
            String[] parts = motd.split("\\R", -1);
            motd = parts.length <= 2 ? motd : (parts[0] + "\n" + parts[1]);
        }
        String version = server.getVersion();

        JLabel nombreLabel = new JLabel(nombre);
        nombreLabel.setFont(nombreLabel.getFont().deriveFont(Font.BOLD, 18f));

        FlatTextField nombreField = new FlatTextField();
        nombreField.setText(nombre);
        nombreField.setFont(nombreLabel.getFont());
        Border bordeVacio = BorderFactory.createEmptyBorder(0, 0, 0, 0);
        Font fontNormal = nombreField.getFont();
        nombreField.setBorder(bordeVacio);
        nombreField.setOpaque(false);
        nombreField.setEditable(false);
        nombreField.setForeground(nombreLabel.getForeground());
        nombreField.setHorizontalAlignment(SwingConstants.LEFT);
        nombreField.putClientProperty("fullText", nombre == null ? "" : nombre);
        nombreField.setToolTipText(nombre);
        Dimension nombrePreferredSize = nombreField.getPreferredSize();
        nombreField.setPreferredSize(new Dimension(0, nombrePreferredSize.height));
        nombreField.setMinimumSize(new Dimension(0, nombrePreferredSize.height));
        nombreField.setMaximumSize(new Dimension(Integer.MAX_VALUE, nombrePreferredSize.height));

        AppTheme.applyHandCursor(nombreField);
        nombreField.setToolTipText("Click para editar (Enter o fuera para guardar)");
        nombreField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if(nombreField.isEditable()) return;
                if(fontNormal != null){
                    java.util.Map<java.awt.font.TextAttribute, Object> attrs = new java.util.HashMap<>(fontNormal.getAttributes());
                    attrs.put(java.awt.font.TextAttribute.UNDERLINE, java.awt.font.TextAttribute.UNDERLINE_ON);
                    nombreField.setFont(fontNormal.deriveFont(attrs));
                }
                nombreField.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if(nombreField.isEditable()) return;
                nombreField.setOpaque(false);
                if(fontNormal != null){
                    nombreField.setFont(fontNormal);
                }
                nombreField.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if(!SwingUtilities.isLeftMouseButton(e)) return;
                if(nombreField.isEditable()) return;
                nombreField.setEditable(true);
                nombreField.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                if(fontNormal != null){
                    nombreField.setFont(fontNormal);
                }
                nombreField.setOpaque(false);
                nombreField.requestFocusInWindow();
                nombreField.selectAll();
            }
        });

        final JLabel[] motdLabelRef = new JLabel[1];

        Runnable commitNombre = () -> {
            if(!nombreField.isEditable()) return;
            String nuevo = nombreField.getText();
            nuevo = (nuevo == null) ? "" : nuevo.strip();
            if(!nuevo.isBlank()){
                server.setDisplayName(nuevo);
                nombreField.putClientProperty("fullText", nuevo);
                try{
                    gestorServidores.guardarServidor(server);
                } catch (RuntimeException ignored){
                }
            } else {
                String actual = server.getDisplayName();
                nombreField.setText(actual == null ? "" : actual);
                nombreField.putClientProperty("fullText", actual == null ? "" : actual);
            }
            nombreField.setEditable(false);
            nombreField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            nombreField.setOpaque(false);
            nombreField.setBorder(bordeVacio);
            if(fontNormal != null){
                nombreField.setFont(fontNormal);
            }
            aplicarEllipsisTextos(nombreField, motdLabelRef[0]);
        };
        nombreField.addActionListener(e -> commitNombre.run());
        nombreField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                commitNombre.run();
            }
        });

        String motdRawDisplay = (motdRaw == null) ? "" : motdRaw.replace("\r", "").replace('&', '\u00A7');
        String[] motdRawParts = motdRawDisplay.split("\\R", -1);
        motdRawDisplay = motdRawParts.length <= 2 ? motdRawDisplay : (motdRawParts[0] + "\n" + motdRawParts[1]);

        JLabel motdLabel = new JLabel(MotdRenderUtil.toHtml(motdRawDisplay));
        motdLabel.putClientProperty("motdRawDisplay", motdRawDisplay);
        motdLabel.setFont(AppTheme.getConsoleLikeFont(15f));
        motdLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        motdLabel.setHorizontalAlignment(SwingConstants.LEFT);
        motdLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        motdLabel.setToolTipText("Click para editar MOTD");
        motdLabel.putClientProperty("fullText", motd == null ? "" : motd);
        motdLabel.setToolTipText(motd);
        motdLabel.setForeground(AppTheme.getConsoleForeground());
        installAnimatedMotdHtml(motdLabel, motdRawDisplay);
        motdLabelRef[0] = motdLabel;

        Font motdFontNormal = motdLabel.getFont();
        motdLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if(motdFontNormal == null) return;
                java.util.Map<java.awt.font.TextAttribute, Object> attrs = new java.util.HashMap<>(motdFontNormal.getAttributes());
                attrs.put(java.awt.font.TextAttribute.UNDERLINE, java.awt.font.TextAttribute.UNDERLINE_ON);
                motdLabel.setFont(motdFontNormal.deriveFont(attrs));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if(motdFontNormal != null) motdLabel.setFont(motdFontNormal);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if(!SwingUtilities.isLeftMouseButton(e)) return;
                String base = Utilidades.leerMotdDesdeProperties(Path.of(server.getServerDir()));
                String nuevo = MotdEditorDialog.show(PanelPrevisualizacion.this, base);
                if(nuevo == null) return;
                Utilidades.escribirMotdEnProperties(Path.of(server.getServerDir()), nuevo);
                String limpio = MotdRenderUtil.stripCodes(nuevo);
                if(limpio != null){
                    limpio = limpio.replace("\r", "");
                    String[] parts = limpio.split("\\R", -1);
                    limpio = parts.length <= 2 ? limpio : (parts[0] + "\n" + parts[1]);
                }
                motdLabel.putClientProperty("fullText", limpio == null ? "" : limpio);
                String nuevoDisplay = (nuevo == null) ? "" : nuevo.replace("\r", "").replace('&', '\u00A7');
                String[] nuevoParts = nuevoDisplay.split("\\R", -1);
                nuevoDisplay = nuevoParts.length <= 2 ? nuevoDisplay : (nuevoParts[0] + "\n" + nuevoParts[1]);
                motdLabel.putClientProperty("motdRawDisplay", nuevoDisplay);
                motdLabel.setText(MotdRenderUtil.toHtml(nuevoDisplay));
                installAnimatedMotdHtml(motdLabel, nuevoDisplay);
                aplicarEllipsisTextos(nombreField, motdLabel);
                try{
                    gestorServidores.guardarServidor(server);
                } catch (RuntimeException ignored){
                }
            }
        });

        JLabel ipServidor = new JLabel("(cargando...)");
        ipServidor.putClientProperty("fullText", "(cargando...)");
        ipServidor.setFont(ipServidor.getFont().deriveFont(Font.BOLD, 16f));
        ipServidor.setForeground(AppTheme.getLinkForeground());
        ipServidor.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel versionLabel = new JLabel((version == null || version.isBlank()) ? "(sin versión)" : version);
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 15f));
        versionLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        versionLabel.setHorizontalAlignment(SwingConstants.LEFT);

        String tipo = (server == null || server.getTipo() == null || server.getTipo().isBlank())
                ? "desconocido"
                : server.getTipo();
        JLabel tipoLabel = new JLabel(tipo);
        tipoLabel.setFont(tipoLabel.getFont().deriveFont(Font.PLAIN, 15f));

        JPanel infoServidor = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        infoServidor.setOpaque(false);
        infoServidor.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        infoServidor.setAlignmentX(Component.LEFT_ALIGNMENT);

        Runnable ajustarInfoServidor = () -> {
            int fixedWidth = versionLabel.getPreferredSize().width
                    + tipoLabel.getPreferredSize().width
                    + 44;
            int maxWidth = Math.max(0, infoServidor.getWidth() - fixedWidth);
            aplicarEllipsisLabel(ipServidor, maxWidth);
        };

        Runnable actualizarTextoIp = () -> {
            String ip = (String) ipServidor.getClientProperty("ipPublica");
            if(ip == null || ip.isBlank()){
                // Sin IP pública (sin internet o aún buscándola): se muestra la dirección de la red local,
                // que sirve para jugar desde la misma red.
                String ipLocal = (String) ipServidor.getClientProperty("ipLocal");
                if(ipLocal == null || ipLocal.isBlank()){
                    ipServidor.putClientProperty("fullText", "(buscando dirección...)");
                    ipServidor.putClientProperty("copyText", null);
                    ipServidor.setToolTipText(null);
                    SwingUtilities.invokeLater(ajustarInfoServidor);
                    return;
                }
                int puertoLocal = resolveCurrentServerPort();
                String direccion = puertoLocal > 0 ? (ipLocal + ":" + puertoLocal) : ipLocal;
                ipServidor.putClientProperty("fullText", direccion + " (red local)");
                ipServidor.putClientProperty("copyText", direccion);
                ipServidor.setToolTipText("Sin IP pública. Esta dirección sirve para conectarse desde tu red local. Haz clic para copiarla.");
                SwingUtilities.invokeLater(ajustarInfoServidor);
                return;
            }
            ipServidor.setToolTipText("Dirección pública. Haz clic para copiarla.");
            int puerto = resolveCurrentServerPort();
            String texto = puerto > 0 ? (ip + ":" + puerto) : (ip + ":(sin puerto)");
            ipServidor.putClientProperty("fullText", texto);
            ipServidor.putClientProperty("copyText", puerto > 0 ? (ip + ":" + puerto) : ip);
            SwingUtilities.invokeLater(ajustarInfoServidor);
        };

        IPLocal.getAsync(ipLocal -> {
            ipServidor.putClientProperty("ipLocal", ipLocal);
            actualizarTextoIp.run();
        });
        IPPublica.getAsync(ip -> {
            ipServidor.putClientProperty("ipPublica", ip);
            actualizarTextoIp.run();
        });

        // Antes se releía server.properties cada 500 ms. Ahora los cambios hechos desde Dora llegan
        // por el evento "configuracionServidor" y para ediciones a mano basta con mirar la fecha
        // de modificación del archivo cada pocos segundos.
        Timer refreshPuertoTimer = new Timer(3000, e -> {
            if(puertoCache.haCambiado(resolveCurrentServerPropertiesPath())){
                actualizarTextoIp.run();
            }
        });
        refreshPuertoTimer.setRepeats(true);
        refreshPuertoTimer.start();

        PropertyChangeListener listenerEstado = null;
        if(server != null){
            listenerEstado = evt -> {
                String propertyName = evt.getPropertyName();
                if(!"estadoServidor".equals(propertyName) && !"listaServidores".equals(propertyName) && !"configuracionServidor".equals(propertyName)) return;
                if("estadoServidor".equals(propertyName) || "configuracionServidor".equals(propertyName)){
                    Object v = evt.getNewValue();
                    if(!(v instanceof Server s)) return;
                    Server current = resolveCurrentServer();
                    if(current == null || current.getId() == null || s.getId() == null || !s.getId().equals(current.getId())) return;
                }
                SwingUtilities.invokeLater(actualizarTextoIp);
            };
            gestorServidores.addPropertyChangeListener(listenerEstado);
        }

        ipServidor.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Object copyObj = ipServidor.getClientProperty("copyText");
                if(!(copyObj instanceof String copyText) || copyText.isBlank()) return;
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(copyText), null);
                JOptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(PanelPrevisualizacion.this),
                        "Copiado al portapapeles: " + copyText,
                        "Copiado",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        });

        infoServidor.add(ipServidor);
        infoServidor.add(crearSeparadorInfo());
        infoServidor.add(versionLabel);
        infoServidor.add(crearSeparadorInfo());
        infoServidor.add(tipoLabel);
        infoServidor.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                ajustarInfoServidor.run();
            }
        });

        nombreField.setAlignmentX(Component.LEFT_ALIGNMENT);
        motdLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel motdBox = new RoundedBackgroundPanel(AppTheme.getConsoleBackground(), AppTheme.getArc());
        motdBox.setBorder(AppTheme.createRoundedBorder(
                new Insets(6, 8, 6, 8),
                AppTheme.getConsoleOutlineColor(),
                1f
        ));
        motdBox.add(motdLabel, BorderLayout.CENTER);
        motdBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        panelDatos.add(nombreField);
        panelDatos.add(motdBox);
        panelDatos.add(infoServidor);
        panelDatos.add(Box.createVerticalGlue());

        JPanel panelDatosWrap = new JPanel(new BorderLayout());
        panelDatosWrap.setOpaque(false);
        panelDatosWrap.add(panelDatos, BorderLayout.NORTH);
        this.add(panelDatosWrap, BorderLayout.CENTER);

        Runnable ajustar = () -> aplicarEllipsisTextos(nombreField, motdLabel);
        SwingUtilities.invokeLater(ajustar);
        panelDatosWrap.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                ajustar.run();
            }
        });

        PropertyChangeListener finalListenerEstado = listenerEstado;
        final boolean[] wasDisplayable = {false};
        this.addHierarchyListener(e -> {
            if(isDisplayable()){
                wasDisplayable[0] = true;
                if(!refreshPuertoTimer.isRunning()){
                    refreshPuertoTimer.start();
                }
                return;
            }
            if(wasDisplayable[0]){
                refreshPuertoTimer.stop();
                if(finalListenerEstado != null){
                    gestorServidores.removePropertyChangeListener(finalListenerEstado);
                }
            }
        });
    }

    private final PuertoPropertiesCache puertoCache = new PuertoPropertiesCache();

    private Path resolveCurrentServerPropertiesPath() {
        Server current = resolveCurrentServer();
        if(current == null || current.getServerDir() == null || current.getServerDir().isBlank()){
            return null;
        }
        try{
            return Path.of(current.getServerDir()).resolve("server.properties");
        } catch (RuntimeException ex){
            return null;
        }
    }

    /** Recuerda qué archivo y qué fecha de modificación se leyeron por última vez. */
    static final class PuertoPropertiesCache {
        private Path ruta;
        private long modificado = Long.MIN_VALUE;

        boolean haCambiado(Path nuevaRuta) {
            long mtime = Long.MIN_VALUE;
            if(nuevaRuta != null){
                try{
                    mtime = Files.getLastModifiedTime(nuevaRuta).toMillis();
                } catch (IOException | RuntimeException ex){
                    mtime = -1L;
                }
            }
            boolean cambiado = !java.util.Objects.equals(ruta, nuevaRuta) || mtime != modificado;
            ruta = nuevaRuta;
            modificado = mtime;
            return cambiado;
        }
    }

    private int resolveCurrentServerPort() {
        Server current = resolveCurrentServer();
        Path propertiesPath = resolveCurrentServerPropertiesPath();
        puertoCache.haCambiado(propertiesPath);
        if(current != null && propertiesPath != null){
            try{
                Properties properties = Utilidades.cargarPropertiesUtf8(propertiesPath);
                String value = properties.getProperty("server-port");
                if(value != null && !value.isBlank()){
                    int parsed = Integer.parseInt(value.strip());
                    if(parsed > 0 && parsed <= 65535){
                        return parsed;
                    }
                }
            } catch (IOException | RuntimeException ex){
                // Fall back to the in-memory config below.
            }
        }
        int configPort = current == null || current.getServerConfig() == null ? 0 : current.getServerConfig().getPuerto();
        return configPort > 0 && configPort <= 65535 ? configPort : 0;
    }

    private Server resolveCurrentServer() {
        if(gestorServidores == null){
            return server;
        }
        Server selected = gestorServidores.getServidorSeleccionado();
        if(server == null){
            server = selected;
            return selected;
        }
        if(selected != null && server.getId() != null && server.getId().equals(selected.getId())){
            server = selected;
            return selected;
        }
        Server byId = server.getId() == null ? null : gestorServidores.getServerById(server.getId());
        if(byId != null){
            server = byId;
            return byId;
        }
        return server;
    }

    private void cambiarIconoServidor(GestorServidores gestorServidores, Server server, ImagenRedondaLabel iconoRedondo){
        if(server == null) return;
        String dir = server.getServerDir();
        if(dir == null || dir.isBlank()) return;

        Path outPath = Path.of(dir).resolve("server-icon.png");
        try{
            ImageSelectionResult selection = seleccionarImagenServidor(server);
            if(selection == null || selection.image() == null) return;

            BufferedImage icon64 = selection.useDirectly()
                    ? selection.image()
                    : CropIconDialog.show(this, selection.image());
            if(icon64 == null) return;
            Files.createDirectories(outPath.getParent());
            ImageIO.write(icon64, "png", outPath.toFile());

            iconoRedondo.setImageIcon(new ImageIcon(icon64));
            iconoRedondo.repaint();

            try{
                gestorServidores.guardarServidor(server);
            } catch (RuntimeException ignored){
            }
        } catch (IOException ex){
            JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Error guardando server-icon.png: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static JLabel crearSeparadorInfo(){
        JLabel separador = new JLabel("|");
        separador.setForeground(AppTheme.getMutedForeground());
        separador.setFont(separador.getFont().deriveFont(Font.PLAIN, 15f));
        return separador;
    }

    private static void installAnimatedMotdHtml(JLabel label, String motdRaw){
        if(label == null) return;
        Object oldTimer = label.getClientProperty("motdAnimationTimer");
        if(oldTimer instanceof Timer timer){
            timer.stop();
        }

        if(!MotdRenderUtil.hasObfuscated(motdRaw)){
            label.putClientProperty("motdAnimationToken", null);
            label.putClientProperty("motdAnimationTimer", null);
            label.setText(MotdRenderUtil.toHtml(motdRaw));
            return;
        }

        Timer timer = new Timer(140, e -> label.setText(MotdRenderUtil.toHtml(motdRaw)));
        Object token = new Object();
        label.putClientProperty("motdAnimationToken", token);
        label.putClientProperty("motdAnimationTimer", timer);
        Runnable syncTimer = () -> {
            if(label.getClientProperty("motdAnimationToken") != token){
                timer.stop();
                return;
            }
            if(label.isShowing()){
                if(!timer.isRunning()) timer.start();
                label.setText(MotdRenderUtil.toHtml(motdRaw));
            } else {
                timer.stop();
            }
        };
        label.addHierarchyListener(e -> {
            syncTimer.run();
        });
        SwingUtilities.invokeLater(syncTimer);
    }

    private static void aplicarEllipsisLabel(JLabel label, int maxWidth){
        if(label == null) return;
        Object fullObj = label.getClientProperty("fullText");
        String full = (fullObj instanceof String s) ? s : label.getText();
        if(full == null) full = "";
        label.setToolTipText(full);

        label.setText(TextEllipsizer.right(full, label.getFontMetrics(label.getFont()), maxWidth));
    }

    private ImageSelectionResult seleccionarImagenServidor(Server server) throws IOException {
        List<DoraImageOption> doraImages = obtenerDoraImages();
        List<Server> servidores = gestorServidores != null && gestorServidores.getListaServidores() != null
                ? gestorServidores.getListaServidores()
                : List.of();
        Window owner = SwingUtilities.getWindowAncestor(this);
        return SelectorImagenServidorDialog.show(owner, servidores, server, doraImages, this::elegirImagenNativa);
    }

    private File elegirImagenNativa(){
        Window owner = SwingUtilities.getWindowAncestor(this);
        Frame frameOwner = (owner instanceof Frame f) ? f : null;

        FileDialog dialog = new FileDialog(frameOwner, "Selecciona una imagen", FileDialog.LOAD);
        dialog.setMultipleMode(false);

        try{
            File pictures = Utilidades.resolveSystemPicturesDirectory();
            if(pictures.isDirectory()){
                dialog.setDirectory(pictures.getAbsolutePath());
            }
        } catch (RuntimeException ignored){
        }

        dialog.setFilenameFilter((dir, name) -> {
            if(name == null) return false;
            String n = name.toLowerCase();
            return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp") || n.endsWith(".bmp") || n.endsWith(".gif");
        });

        dialog.setVisible(true);
        String file = dialog.getFile();
        String dir = dialog.getDirectory();
        if(file == null || dir == null) return null;
        return new File(dir, file);
    }

    private record DoraImageOption(String name, String resourcePath) {}
    private record ImageSelectionResult(BufferedImage image, boolean useDirectly) {}

    @FunctionalInterface
    private interface ImageFilePicker {
        File pick();
    }

    private List<DoraImageOption> obtenerDoraImages() {
        List<DoraImageOption> images = new ArrayList<>();
        for(String resourcePath : DORA_IMAGE_RESOURCES){
            if(resourcePath == null || resourcePath.isBlank()) continue;
            if(getClass().getClassLoader().getResource(resourcePath) == null) continue;

            String fileName = resourcePath;
            int slash = fileName.lastIndexOf('/');
            if(slash >= 0 && slash + 1 < fileName.length()){
                fileName = fileName.substring(slash + 1);
            }
            images.add(new DoraImageOption(fileName, resourcePath));
        }
        return images;
    }

    private static final class SelectorImagenServidorDialog {
        private static final class ViewportFillPanel extends JPanel implements Scrollable {
            private ViewportFillPanel() {
            }

            @Override
            public Dimension getPreferredScrollableViewportSize() {
                return getPreferredSize();
            }

            @Override
            public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
                return 16;
            }

            @Override
            public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
                return orientation == SwingConstants.VERTICAL ? visibleRect.height - 32 : visibleRect.width - 32;
            }

            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }

            @Override
            public boolean getScrollableTracksViewportHeight() {
                return false;
            }
        }

        private static ImageSelectionResult show(Window owner, List<Server> servers, Server selectedServer, List<DoraImageOption> doraImages, ImageFilePicker picker) throws IOException {
            JDialog dialog = new JDialog(owner, "Seleccionar imagen del servidor", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

            ImageSelectionResult[] result = new ImageSelectionResult[1];

            JPanel content = new JPanel(new BorderLayout(12, 12));
            content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
            content.setBackground(AppTheme.getPanelBackground());

            JPanel header = new JPanel();
            header.setOpaque(false);
            header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
            content.add(header, BorderLayout.NORTH);

            JPanel center = new JPanel(new BorderLayout(12, 12));
            center.setOpaque(false);
            center.add(crearZonaArrastre(dialog, result, picker), BorderLayout.NORTH);
            center.add(crearListadoImagenes(dialog, servers, selectedServer, doraImages, result), BorderLayout.CENTER);
            content.add(center, BorderLayout.CENTER);

            JButton cancel = new FlatButton();
            cancel.setText("Cancelar");
            cancel.addActionListener(e -> {
                result[0] = null;
                dialog.dispose();
            });

            JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            footer.setOpaque(false);
            footer.add(cancel);
            content.add(footer, BorderLayout.SOUTH);

            dialog.setContentPane(content);
            dialog.setMinimumSize(new Dimension(760, 560));
            dialog.setSize(860, 620);
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
            return result[0];
        }

        private static JComponent crearZonaArrastre(JDialog dialog, ImageSelectionResult[] result, ImageFilePicker picker) {
            Runnable abrirSelector = () -> {
                try{
                    BufferedImage image = cargarImagenElegida(picker);
                    if(image == null) return;
                    result[0] = new ImageSelectionResult(image, false);
                    dialog.dispose();
                } catch (IOException ex){
                    mostrarError(dialog, ex.getMessage());
                }
            };

            JPanel uploadPanel = new JPanel(new BorderLayout(0, 10));
            uploadPanel.setOpaque(true);
            uploadPanel.setBackground(AppTheme.getSurfaceBackground());
            AppTheme.applyHandCursor(uploadPanel);
            uploadPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createDashedBorder(AppTheme.getMainAccent(), 1f, 6f, 3f, true),
                    BorderFactory.createEmptyBorder(28, 28, 28, 28)
            ));
            uploadPanel.setPreferredSize(new Dimension(100, 170));

            JLabel title = new JLabel("Arrastra una imagen aquí", SwingConstants.CENTER);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

            JLabel subtitle = new JLabel("o selecciónala desde tu ordenador", SwingConstants.CENTER);
            subtitle.setForeground(AppTheme.getMutedForeground());

            JPanel textWrap = new JPanel(new GridBagLayout());
            textWrap.setOpaque(false);
            JPanel centeredText = new JPanel();
            centeredText.setOpaque(false);
            centeredText.setLayout(new BoxLayout(centeredText, BoxLayout.Y_AXIS));
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
            centeredText.add(title);
            centeredText.add(Box.createVerticalStrut(6));
            centeredText.add(subtitle);

            textWrap.add(centeredText);

            uploadPanel.add(textWrap, BorderLayout.CENTER);
            installImageDropHandler(uploadPanel, dialog, result);
            uploadPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if(!SwingUtilities.isLeftMouseButton(e)) return;
                    abrirSelector.run();
                }
            });
            return uploadPanel;
        }

        private static JComponent crearListadoImagenes(JDialog dialog, List<Server> servers, Server selectedServer, List<DoraImageOption> doraImages, ImageSelectionResult[] result) {
            JPanel sections = new ViewportFillPanel();
            sections.setOpaque(false);
            sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));

            sections.add(crearSeccionDoraImages(dialog, doraImages, result));
            sections.add(Box.createVerticalStrut(12));
            sections.add(crearSeccionPreviews(dialog, servers, selectedServer, result));

            FlatScrollPane scrollPane = new FlatScrollPane();
            scrollPane.setViewportView(sections);
            scrollPane.setBorder(null);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setOpaque(false);
            scrollPane.getViewport().setOpaque(false);
            scrollPane.getViewport().setBackground(AppTheme.getPanelBackground());
            return scrollPane;
        }

        private static JComponent crearSeccionDoraImages(JDialog dialog, List<DoraImageOption> doraImages, ImageSelectionResult[] result) {
            JPanel wrap = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 12));
            wrap.setOpaque(false);

            if(doraImages == null || doraImages.isEmpty()){
                wrap.add(crearTarjetaVacia("No hay imágenes internas disponibles ahora mismo."));
            } else {
                for(DoraImageOption image : doraImages){
                    wrap.add(crearBotonDoraImage(dialog, image, result));
                }
            }

            JPanel section = BoxCategory.createTitledSection("Dora Images", 8);
            section.add(wrap, BorderLayout.CENTER);
            return section;
        }

        private static JComponent crearSeccionPreviews(JDialog dialog, List<Server> servers, Server selectedServer, ImageSelectionResult[] result) {
            JPanel section = BoxCategory.createTitledSection("Previews de mundos", 6);

            JComboBox<Server> serverCombo = new JComboBox<>((servers == null ? List.<Server>of() : servers).toArray(Server[]::new));
            serverCombo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if(value instanceof Server server){
                        String name = server.getDisplayName();
                        if(name == null || name.isBlank()){
                            name = server.getServerDir() == null ? "(sin nombre)" : server.getServerDir();
                        }
                        setText(name);
                    }
                    return this;
                }
            });
            if(selectedServer != null){
                serverCombo.setSelectedItem(selectedServer);
            }
            JButton resetButton = new FlatButton();
            resetButton.setText("Reiniciar");

            JPanel wrap = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 8));
            wrap.setOpaque(false);
            Runnable recargarPreviews = () -> {
                wrap.removeAll();
                Object selected = serverCombo.getSelectedItem();
                List<WorldPreviewOption> previews = selected instanceof Server server
                        ? cargarPreviewsServidor(server)
                        : List.of();
                if(previews.isEmpty()){
                    wrap.add(crearTarjetaVacia("No hay previews generadas todavía para este servidor."));
                } else {
                    for(WorldPreviewOption preview : previews){
                        wrap.add(crearTarjetaPreview(dialog, preview, result));
                    }
                }
                wrap.revalidate();
                wrap.repaint();
            };
            serverCombo.addActionListener(e -> recargarPreviews.run());
            resetButton.addActionListener(e -> {
                if(selectedServer != null){
                    serverCombo.setSelectedItem(selectedServer);
                }
            });
            recargarPreviews.run();

            JPanel top = new JPanel(new BorderLayout(6, 0));
            top.setOpaque(false);
            top.add(serverCombo, BorderLayout.CENTER);
            top.add(resetButton, BorderLayout.EAST);
            section.add(top, BorderLayout.NORTH);
            section.add(wrap, BorderLayout.CENTER);
            return section;
        }

        private static JComponent crearTarjetaVacia(String texto) {
            JPanel empty = BoxCategory.createSurfacePanel(new BorderLayout(), new Insets(18, 18, 18, 18));
            JLabel label = new JLabel(texto);
            label.setForeground(AppTheme.getMutedForeground());
            empty.add(label, BorderLayout.CENTER);
            return empty;
        }

        private static JComponent crearTarjetaPreview(JDialog dialog, WorldPreviewOption preview, ImageSelectionResult[] result) {
            JButton card = new FlatButton();
            card.setLayout(new BorderLayout(0, 6));
            card.setPreferredSize(new Dimension(198, 154));
            BoxCategory.createSurfaceButtonCard(card, new Insets(8, 8, 8, 8));

            JLabel imageLabel = new JLabel("Cargando...", SwingConstants.CENTER);
            imageLabel.setPreferredSize(new Dimension(180, 110));
            imageLabel.setOpaque(true);
            imageLabel.setBackground(AppTheme.getPanelBackground());
            imageLabel.setBorder(BorderFactory.createLineBorder(AppTheme.getSubtleBorderColor()));
            cargarMiniaturaPreview(preview.previewPath(), imageLabel);

            JLabel nameLabel = new JLabel(preview.worldName());
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));

            JPanel textPanel = new JPanel();
            textPanel.setOpaque(false);
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.add(nameLabel);

            card.add(imageLabel, BorderLayout.CENTER);
            card.add(textPanel, BorderLayout.SOUTH);
            card.addActionListener(e -> {
                try{
                    BufferedImage image = ImageIO.read(preview.previewPath().toFile());
                    if(image == null){
                        mostrarError(dialog, "No se ha podido leer la preview seleccionada.");
                        return;
                    }
                    result[0] = new ImageSelectionResult(image, false);
                    dialog.dispose();
                } catch (IOException ex){
                    mostrarError(dialog, "No se ha podido leer la preview: " + ex.getMessage());
                }
            });
            return card;
        }

        private static JComponent crearBotonDoraImage(JDialog dialog, DoraImageOption imageOption, ImageSelectionResult[] result) {
            JButton button = new FlatButton();
            button.setPreferredSize(new Dimension(64, 64));
            button.setMinimumSize(new Dimension(64, 64));
            button.setMaximumSize(new Dimension(64, 64));
            button.setFocusPainted(false);
            button.setBorderPainted(false);
            button.setContentAreaFilled(false);
            button.setOpaque(false);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.setToolTipText(imageOption.name());
            cargarMiniaturaDoraImage(imageOption.resourcePath(), button);
            button.addActionListener(e -> {
                try{
                    BufferedImage image = leerImagenRecurso(imageOption.resourcePath());
                    if(image == null){
                        mostrarError(dialog, "No se ha podido leer la imagen seleccionada.");
                        return;
                    }
                    result[0] = new ImageSelectionResult(image, true);
                    dialog.dispose();
                } catch (IOException ex){
                    mostrarError(dialog, "No se ha podido leer la imagen interna: " + ex.getMessage());
                }
            });
            return button;
        }

        private static void cargarMiniaturaPreview(Path previewPath, JLabel imageLabel) {
            SwingWorker<ImageIcon, Void> worker = new SwingWorker<>() {
                @Override
                protected ImageIcon doInBackground() throws Exception {
                    BufferedImage image = ImageIO.read(previewPath.toFile());
                    if(image == null) return null;
                    Dimension fit = ajustarDentro(image.getWidth(), image.getHeight(), 190, 120);
                    Image scaled = image.getScaledInstance(fit.width, fit.height, Image.SCALE_SMOOTH);
                    return new ImageIcon(scaled);
                }

                @Override
                protected void done() {
                    try{
                        ImageIcon icon = get();
                        if(icon != null){
                            imageLabel.setText(null);
                            imageLabel.setIcon(icon);
                        } else {
                            imageLabel.setText("Preview no válida");
                        }
                    } catch (Exception ex){
                        imageLabel.setText("No disponible");
                    }
                }
            };
            worker.execute();
        }

        private static void cargarMiniaturaDoraImage(String resourcePath, AbstractButton target) {
            SwingWorker<ImageIcon, Void> worker = new SwingWorker<>() {
                @Override
                protected ImageIcon doInBackground() throws Exception {
                    BufferedImage image = leerImagenRecurso(resourcePath);
                    return image == null ? null : new ImageIcon(image);
                }

                @Override
                protected void done() {
                    try{
                        ImageIcon icon = get();
                        if(icon != null){
                            target.setText(null);
                            target.setIcon(icon);
                        } else {
                            target.setText("!");
                        }
                    } catch (Exception ex){
                        target.setText("!");
                    }
                }
            };
            worker.execute();
        }

        private static List<WorldPreviewOption> cargarPreviewsServidor(Server server) {
            return WorldPreviewCatalogService.listWorldPreviews(server);
        }

        private static Dimension ajustarDentro(int srcWidth, int srcHeight, int maxWidth, int maxHeight) {
            if(srcWidth <= 0 || srcHeight <= 0) {
                return new Dimension(Math.max(1, maxWidth), Math.max(1, maxHeight));
            }
            double scale = Math.min((double) maxWidth / srcWidth, (double) maxHeight / srcHeight);
            scale = Math.max(0.0d, Math.min(1.0d, scale));
            int width = Math.max(1, (int) Math.round(srcWidth * scale));
            int height = Math.max(1, (int) Math.round(srcHeight * scale));
            return new Dimension(width, height);
        }

        private static void installImageDropHandler(JComponent target, JDialog dialog, ImageSelectionResult[] result) {
            target.setTransferHandler(new TransferHandler() {
                @Override
                public boolean canImport(TransferSupport support) {
                    return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
                }

                @Override
                @SuppressWarnings("unchecked")
                public boolean importData(TransferSupport support) {
                    if(!canImport(support)) return false;
                    try{
                        Transferable transferable = support.getTransferable();
                        List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        if(files == null || files.isEmpty()) return false;
                        BufferedImage image = ImageIO.read(files.get(0));
                        if(image == null){
                            mostrarError(dialog, "El archivo arrastrado no es una imagen compatible.");
                            return false;
                        }
                        result[0] = new ImageSelectionResult(image, false);
                        dialog.dispose();
                        return true;
                    } catch (Exception ex){
                        mostrarError(dialog, "No se ha podido abrir la imagen arrastrada.");
                        return false;
                    }
                }
            });
        }

        private static BufferedImage cargarImagenElegida(ImageFilePicker picker) throws IOException {
            if(picker == null) return null;
            File selected = picker.pick();
            if(selected == null || !selected.exists()) return null;
            BufferedImage image = ImageIO.read(selected);
            if(image == null){
                throw new IOException("No se ha podido leer la imagen seleccionada.");
            }
            return image;
        }

        private static BufferedImage leerImagenRecurso(String resourcePath) throws IOException {
            if(resourcePath == null || resourcePath.isBlank()) return null;
            try(var in = PanelPrevisualizacion.class.getClassLoader().getResourceAsStream(resourcePath)){
                if(in == null) return null;
                return ImageIO.read(in);
            }
        }

        private static void mostrarError(Component parent, String message) {
            JOptionPane.showMessageDialog(parent, message, "Imagen no válida", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static BufferedImage scaleSquare(BufferedImage croppedSquare, int size){
        if(croppedSquare == null) return null;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.drawImage(croppedSquare, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    private static String stripMinecraftCodes(String s){
        return MotdRenderUtil.stripCodes(s);
    }

    private static void aplicarEllipsisTextos(JTextField nombreField, JLabel motdLabel){
        if(nombreField == null || motdLabel == null) return;
        if(nombreField.isEditable()) return;

        Container parent = nombreField.getParent();
        if(parent == null) return;
        int maxWidth = nombreField.getWidth() > 0 ? nombreField.getWidth() : parent.getWidth();
        if(maxWidth <= 0) return;
        Insets nameInsets = nombreField.getInsets();
        if(nameInsets != null){
            maxWidth -= nameInsets.left + nameInsets.right;
        }
        maxWidth = Math.max(0, maxWidth);

        Object fullNameObj = nombreField.getClientProperty("fullText");
        String fullName = fullNameObj == null ? "" : String.valueOf(fullNameObj);
        fullName = fullName.strip();

        nombreField.setText(TextEllipsizer.right(fullName, nombreField.getFontMetrics(nombreField.getFont()), maxWidth));
        nombreField.setToolTipText(fullName);

        Object fullMotdObj = motdLabel.getClientProperty("fullText");
        String fullMotd = fullMotdObj == null ? "" : String.valueOf(fullMotdObj);
        motdLabel.setToolTipText(fullMotd);
    }

}
