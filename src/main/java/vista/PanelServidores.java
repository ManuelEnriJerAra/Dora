/*
 * Fichero: PanelServidores.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Este panel extiende de JScrollPane y es el encargado de mostrar al usuario la lista de servidores guardados.
 * Es interactuable y manda la información del servidor seleccionado a ---------------------------------------
 * */

package vista;

import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.Scrollable;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.extras.components.FlatScrollPane;
import com.formdev.flatlaf.ui.FlatLineBorder;

import controlador.GestorServidores;
import controlador.AppErrorReporter;
import modelo.Server;
import modelo.extensions.ServerPlatform;

@SuppressWarnings("unused")
public class PanelServidores extends FlatScrollPane {
    private static final Dimension MIN_SIZE = new Dimension(240, 240); // ancho justo para que no tape el estado "Inactivo"
    private static final int FAVORITE_CORNER_GAP = 6;
    int arc;
    Color acento;
    Color base;
    Color bgLista;
    Color bgNormal;
    Color bgHover;
    Color bgPresionado;
    Color bgSelected;

    Insets insets = new Insets(8, 8, 8, 8);
    // Bordes
    private FlatLineBorder bordeRedondo;
    private FlatLineBorder bordeHover;
    private FlatLineBorder bordeSeleccionado;

    JPanel filaSeleccionada;
    private boolean uiReady;

    private static final int DRAG_THRESHOLD = 6;
    private JPanel filaArrastreCandidata;
    private JPanel filaArrastrada;
    private int indiceOriginalArrastre = -1;
    private int indiceInsercionArrastre = -1;
    private int alturaArrastre = -1;
    private Point puntoPresionArrastre;
    private Point puntoPresionPantalla;
    private Point offsetArrastre;
    private Point ubicacionArrastre = new Point();
    private Point ultimoPuntoArrastrePantalla;
    private BufferedImage imagenArrastre;
    private boolean dragActiva;
    private AWTEventListener dragEventListener;
    private JComponent glassPaneArrastre;
    private Component glassPaneAnterior;
    private boolean glassPaneAnteriorVisible;
    private JRootPane rootPaneArrastre;

    public interface ServidorSeleccionadoListener{
        void servidorSeleccionado(Server server);
    }

    public interface ServidorContextMenuListener{
        void abrirConfiguracion(Server server);
        void abrirMundos(Server server);
    }

    private ServidorSeleccionadoListener listener;
    private ServidorContextMenuListener contextMenuListener;

    public void setServidorSeleccionadoListener(ServidorSeleccionadoListener listener){
        this.listener = listener;
    }

    public void setServidorContextMenuListener(ServidorContextMenuListener contextMenuListener){
        this.contextMenuListener = contextMenuListener;
    }

    public void seleccionarServidor(Server server){
        seleccionarServidor(server, true, true);
    }

    public void mostrarSeleccionServidor(Server server){
        seleccionarServidor(server, false, false);
    }

    private void seleccionarServidor(Server server, boolean notificar, boolean asegurarVisible){
        if(server == null) return;
        refrescarTema(true);

        Component vista = getViewport().getView();
        if(!(vista instanceof JPanel panelContenedor)) return;

        for(Component componente : panelContenedor.getComponents()){
            if(!(componente instanceof JPanel fila)) continue;

            Object serverObj = fila.getClientProperty("server");
            if(!(serverObj instanceof Server filaServer)) continue;

            boolean coincidePorId = Objects.equals(filaServer.getId(), server.getId());
            boolean coincidePorDir = filaServer.getId() == null
                    && server.getId() == null
                    && Objects.equals(filaServer.getServerDir(), server.getServerDir());

            if(!coincidePorId && !coincidePorDir) continue;

            marcarFilaSeleccionada(fila);
            if(asegurarVisible){
                asegurarFilaVisible(fila);
            }
            if(notificar && listener != null){
                listener.servidorSeleccionado(filaServer);
            }
            return;
        }
    }

    public void seleccionarPrimero(){
        refrescarTema(true);
        Component vista = getViewport().getView();
        if(!(vista instanceof JPanel panelContenedor)) return;
        for(Component componente : panelContenedor.getComponents()){
            if(!(componente instanceof JPanel fila)) continue;
            Object serverObj = fila.getClientProperty("server");
            if(!(serverObj instanceof Server server)) continue;
            marcarFilaSeleccionada(fila);
            asegurarFilaVisible(fila);
            if(listener != null){
                listener.servidorSeleccionado(server);
            }
            return;
        }
    }

    public void refrescarListado() {
        SwingUtilities.invokeLater(() -> recargarPanel(gestorServidores.getListaServidores(), gestorServidores));
    }

    GestorServidores gestorServidores;

    // Constructor de PanelServidores
    PanelServidores(GestorServidores gestorServidores){
        this.gestorServidores = gestorServidores;
        this.setBorder(null);
        this.setMinimumSize(MIN_SIZE);
        refrescarTema(true);
        this.setOpaque(true);
        this.setBackground(bgLista);
        if(this.getViewport() != null){
            this.getViewport().setMinimumSize(MIN_SIZE);
            this.getViewport().setOpaque(true);
            this.getViewport().setBackground(bgLista);
            // SIMPLE repinta el viewport completo y evita artefactos al hacer scroll con filas translúcidas.
            this.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        }

        // Scroll más rápido con la rueda del ratón
        try{
            JScrollBar v = this.getVerticalScrollBar();
            if(v != null){
                v.setUnitIncrement(24);
                v.setBlockIncrement(90);
            }
        } catch (Exception ignored){
        }

        UIManager.addPropertyChangeListener(evt -> {
            if(!"lookAndFeel".equals(evt.getPropertyName())) return;
            SwingUtilities.invokeLater(() -> {
                refrescarTema(true);
                if(getViewport() != null){
                    getViewport().setOpaque(true);
                    getViewport().setBackground(bgLista);
                    // mantener el mismo modo al cambiar L&F
                    getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
                }
            });
        });

        gestorServidores.addPropertyChangeListener(evt -> {
            // Garantizamos que cualquier actualización del árbol Swing ocurra en el EDT.
            if(!"listaServidores".equals(evt.getPropertyName())) return;

            @SuppressWarnings("unchecked")
            List<Server> servidores = (List<Server>) evt.getNewValue();

            SwingUtilities.invokeLater(() -> recargarPanel(servidores, gestorServidores));
        });

        this.setViewportView(construirPanelContenedor(gestorServidores.getListaServidores()));
        // Importante: updateUI() puede ser llamado desde el constructor de JScrollPane (super),
        // antes de que se ejecuten los inicializadores de campos. A partir de aquí ya es seguro
        // recalcular bordes/arcos usando UIManager.
        uiReady = true;
    }

    private void recargarPanel(List<Server> servidores, GestorServidores gestorServidores){
        refrescarTema(true);
        Server servidorSeleccionadoActual = obtenerServidorSeleccionadoActual();
        Point posicionScroll = null;
        JViewport viewport = getViewport();
        if(viewport != null){
            Point actual = viewport.getViewPosition();
            posicionScroll = actual == null ? null : new Point(actual);
        }
        JPanel panelContenedor = construirPanelContenedor(servidores);

        filaSeleccionada = null;
        this.setViewportView(panelContenedor);
        if(servidorSeleccionadoActual != null){
            mostrarSeleccionServidor(servidorSeleccionadoActual);
        }
        restaurarPosicionScrollTrasLayout(posicionScroll);
        this.revalidate();
        this.repaint();
    }

    private void restaurarPosicionScrollTrasLayout(Point posicionScroll){
        if(posicionScroll == null) return;
        restaurarPosicionScroll(posicionScroll);
        SwingUtilities.invokeLater(() -> restaurarPosicionScroll(posicionScroll));
    }

    private void restaurarPosicionScroll(Point posicionScroll){
        if(posicionScroll == null) return;
        JViewport viewport = getViewport();
        if(viewport == null) return;
        Component vista = viewport.getView();
        if(vista == null) return;

        Dimension tamanioVista = vista.getSize();
        Dimension preferidoVista = vista.getPreferredSize();
        int anchoVista = Math.max(tamanioVista.width, preferidoVista.width);
        int altoVista = Math.max(tamanioVista.height, preferidoVista.height);
        int maxX = Math.max(0, anchoVista - viewport.getWidth());
        int maxY = Math.max(0, altoVista - viewport.getHeight());
        Point ajustada = new Point(
                Math.max(0, Math.min(posicionScroll.x, maxX)),
                Math.max(0, Math.min(posicionScroll.y, maxY))
        );
        viewport.setViewPosition(ajustada);
    }

    private JPanel construirPanelContenedor(List<Server> servidores){
        JPanel panelContenedor = new ServerListPanel();
        panelContenedor.setOpaque(true);
        panelContenedor.setBackground(bgLista);
        panelContenedor.setLayout(new BoxLayout(panelContenedor, BoxLayout.Y_AXIS));

        if(servidores == null) return panelContenedor;

        for(Server servidor : servidores){
            if(servidor == null) continue;
            JPanel fila = crearFila(servidor, servidor.getVersion());
            panelContenedor.add(fila);
        }
        return panelContenedor;
    }

    private Server obtenerServidorSeleccionadoActual(){
        if(gestorServidores != null && gestorServidores.getServidorSeleccionado() != null){
            return gestorServidores.getServidorSeleccionado();
        }
        if(filaSeleccionada == null) return null;
        Object serverObj = filaSeleccionada.getClientProperty("server");
        return serverObj instanceof Server server ? server : null;
    }

    private String construirTextoVersionPlataforma(Server servidor, String version){
        String versionTexto = (version == null || version.isBlank()) ? "(sin versión)" : version.trim();
        String plataforma = obtenerNombrePlataforma(servidor);
        return plataforma == null ? versionTexto : versionTexto + " - " + plataforma;
    }

    private String obtenerNombrePlataforma(Server servidor){
        if(servidor == null) return null;

        ServerPlatform platform = servidor.getPlatform();
        if(platform != null && platform != ServerPlatform.UNKNOWN){
            return platform.getLegacyTypeName();
        }

        String tipo = servidor.getTipo();
        if(tipo == null || tipo.isBlank()) return null;

        ServerPlatform platformMigrada = ServerPlatform.fromLegacyType(tipo);
        if(platformMigrada != ServerPlatform.UNKNOWN){
            return platformMigrada.getLegacyTypeName();
        }
        return tipo.trim().toUpperCase(Locale.ROOT);
    }

    private JPanel crearFila(Server servidor, String version){
        final int ALTURA_FILA = 64 + 8 + 8;
        refrescarTema(false);

        RoundedBackgroundPanel fila = new RoundedBackgroundPanel(bgNormal, arc);
        fila.setLayout(new BorderLayout(8, 0));
        fila.setBorder(bordeRedondo);
        fila.setBackground(bgNormal);
        fila.setOpaque(false); // el fondo lo pintamos manualmente con bordes redondeados
        fila.setBorder(bordeRedondo);

        // En BoxLayout (Y_AXIS), el ancho se puede estirar si el maxWidth es grande.
        // Dejamos altura fija y ancho flexible para que se adapte al tamaño de la ventana/scroll.
        fila.setPreferredSize(new Dimension(MIN_SIZE.width, ALTURA_FILA));
        fila.setMinimumSize(new Dimension(MIN_SIZE.width, ALTURA_FILA));
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, ALTURA_FILA));
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Imagen del servidor a la izquierda, escalada y redondeada
        ImageIcon icono = servidor.getServerIconOrUseDefault();
        ImagenRedondaLabel iconoRedondo = new ImagenRedondaLabel(icono, 10, 64, 64);

        JPanel panelIcono = new JPanel(new GridBagLayout());
        panelIcono.setOpaque(false); // deja que el clip redondeado del padre marque el fondo
        // panelIcono.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
        panelIcono.add(iconoRedondo);

        panelIcono.setPreferredSize(new Dimension(64,64));
        panelIcono.setMinimumSize(new Dimension(64, 64));

        fila.add(panelIcono, BorderLayout.WEST);

        // Panel de texto, nombre arriba, MOTD centro, versión abajo
        JPanel panelDerecho = new JPanel(new GridBagLayout());
        panelDerecho.setOpaque(false);
        // panelDerecho.setBorder(BorderFactory.createEmptyBorder(FAVORITE_CORNER_GAP, 0, 8, FAVORITE_CORNER_GAP));

        // Info arriba
        JPanel infoPanel = new JPanel();
        infoPanel.setOpaque(false);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

        JLabel nombreLabel = new JLabel();
        nombreLabel.setFont(nombreLabel.getFont().deriveFont(Font.BOLD, 16f));
        nombreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        String nombreServidor = servidor.getDisplayName();
        if(nombreServidor == null || nombreServidor.isBlank()){
            nombreServidor = "(sin nombre)";
        }
        nombreLabel.putClientProperty("fullText", nombreServidor);

        JLabel versionLabel = new JLabel();
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 14f));
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        String versionTexto = construirTextoVersionPlataforma(servidor, version);
        versionLabel.putClientProperty("fullText", versionTexto);

        JLabel estado = new JLabel();
        estado.setFont(estado.getFont().deriveFont(Font.BOLD, 14f));
        estado.setForeground(AppTheme.getForeground());
        actualizarEstadoLabel(estado, servidor);
        estado.setAlignmentX(Component.LEFT_ALIGNMENT);

        infoPanel.add(nombreLabel);
        infoPanel.add(versionLabel);
        infoPanel.add(estado);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.add(infoPanel, BorderLayout.CENTER);

        JButton favoritoButton = crearBotonFavorito(servidor, fila);
        JPanel favoritoWrap = new JPanel(new BorderLayout());
        favoritoWrap.setOpaque(false);
        favoritoWrap.add(favoritoButton, BorderLayout.NORTH);
        topPanel.add(favoritoWrap, BorderLayout.EAST);

        GridBagConstraints textGbc = new GridBagConstraints();
        textGbc.gridx = 0;
        textGbc.gridy = 0;
        textGbc.weightx = 1d;
        textGbc.fill = GridBagConstraints.HORIZONTAL;
        textGbc.anchor = GridBagConstraints.CENTER;
        panelDerecho.add(topPanel, textGbc);

        // Indicador de estado (Abajo)
        fila.add(panelDerecho, BorderLayout.CENTER);

        // Ajustar los textos al ancho disponible (con "..." si no caben).
        Runnable ajustarTextos = () -> {
            int anchoDisponible = panelDerecho.getWidth();
            if(anchoDisponible <= 0) return;

            // un pequeño margen para que no “choque” con bordes/paddings
            int maxWidth = Math.max(0, anchoDisponible - 10);

            aplicarEllipsisLabel(nombreLabel, maxWidth);
            aplicarEllipsisLabel(versionLabel, maxWidth);
        };

        // Primera vez (cuando Swing ya ha calculado tamaños)
        SwingUtilities.invokeLater(ajustarTextos);

        // Recalcular si cambia el tamaño del contenedor
        panelDerecho.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                ajustarTextos.run();
            }
        });

        // Interacción y propiedades
        fila.putClientProperty("server", servidor);
        fila.putClientProperty("estadoLabel", estado);
        fila.putClientProperty("panelIcono", panelIcono);
        fila.putClientProperty("favoritoButton", favoritoButton);
        fila.putClientProperty("hovered", Boolean.FALSE);
        MouseAdapter filaMouse = new MouseAdapter() {
            private void maybeShow(MouseEvent e){
                if(dragActiva) return;
                if(!e.isPopupTrigger()) return;
                JPopupMenu menu = crearMenuServidor(servidor);
                menu.show(e.getComponent(), e.getX(), e.getY());
            }

            @Override
            public void mousePressed(MouseEvent e){
                maybeShow(e);
                if(e.isPopupTrigger() || !SwingUtilities.isLeftMouseButton(e)) return;

                filaArrastreCandidata = fila;
                puntoPresionArrastre = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), fila);
                puntoPresionPantalla = obtenerPuntoPantalla(e);
                refrescarTema(false);
                fila.setBackground(bgPresionado);
                syncIconBg(fila);
                fila.repaint();
                instalarListenerGlobalArrastre();
            }

            @Override
            public void mouseDragged(MouseEvent e){
                if(dragActiva){
                    actualizarArrastreDesdePantalla(obtenerPuntoPantalla(e));
                    return;
                }
                if(filaArrastreCandidata != fila || puntoPresionArrastre == null || puntoPresionPantalla == null) return;
                Point screenPoint = obtenerPuntoPantalla(e);
                if(screenPoint == null || screenPoint.distance(puntoPresionPantalla) < DRAG_THRESHOLD) return;
                iniciarArrastreSiProcede(screenPoint);
            }

            @Override
            public void mouseReleased(MouseEvent e){
                maybeShow(e);
            }

            @Override
            public void mouseEntered(MouseEvent e){
                if(dragActiva && fila == filaArrastrada) return;
                aplicarEstadoHoverFila(fila, true);
            }

            @Override
            public void mouseExited(MouseEvent e){
                if(dragActiva && fila == filaArrastrada) return;
                try{
                    Point p = obtenerPuntoPantalla(e);
                    if(p == null) return;
                    SwingUtilities.convertPointFromScreen(p, fila);
                    if(fila.contains(p)) return;
                } catch (IllegalComponentStateException ignored){
                }
                aplicarEstadoHoverFila(fila, false);
            }
        };
        favoritoButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                aplicarEstadoHoverFila(fila, true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                try{
                    Point p = obtenerPuntoPantalla(e);
                    if(p == null) return;
                    SwingUtilities.convertPointFromScreen(p, fila);
                    if(fila.contains(p)) return;
                } catch (IllegalComponentStateException ignored){
                }
                aplicarEstadoHoverFila(fila, false);
            }
        });
        hacerFilaClickable(fila, filaMouse);
        fila.setBorder(bordeRedondo);
        actualizarBotonFavorito(favoritoButton, servidor, false);
        return fila;
    }

    private void seleccionarFila(JPanel fila, boolean notificarSiYaEstabaSeleccionada){
        if(fila == null) return;

        Object serverObj = fila.getClientProperty("server");
        if(!(serverObj instanceof Server serverSeleccionado)) return;

        Point posicionScrollAntes = null;
        JViewport viewport = getViewport();
        if(viewport != null && viewport.getViewPosition() != null){
            posicionScrollAntes = new Point(viewport.getViewPosition());
        }

        boolean yaEstabaSeleccionada = fila == filaSeleccionada;
        marcarFilaSeleccionada(fila);

        if(listener != null && (!yaEstabaSeleccionada || notificarSiYaEstabaSeleccionada)){
            listener.servidorSeleccionado(serverSeleccionado);
        }

        if(posicionScrollAntes != null){
            Point posicionFinal = new Point(posicionScrollAntes);
            SwingUtilities.invokeLater(() -> restaurarPosicionScroll(posicionFinal));
        }
    }

    private void iniciarArrastreSiProcede(Point screenPoint){
        if(dragActiva) return;
        if(filaArrastreCandidata == null || puntoPresionArrastre == null || puntoPresionPantalla == null) return;
        if(screenPoint == null) return;
        if(screenPoint.distance(puntoPresionPantalla) < DRAG_THRESHOLD) return;

        Component vista = getViewport().getView();
        if(!(vista instanceof JPanel panelContenedor)) return;

        int indiceOriginal = indiceComponente(panelContenedor, filaArrastreCandidata);
        if(indiceOriginal < 0) return;

        filaArrastrada = filaArrastreCandidata;
        indiceOriginalArrastre = indiceOriginal;
        indiceInsercionArrastre = indiceOriginal;
        dragActiva = true;
        offsetArrastre = new Point(puntoPresionArrastre);
        ultimoPuntoArrastrePantalla = new Point(screenPoint);
        alturaArrastre = obtenerAlturaArrastre(filaArrastrada);
        imagenArrastre = capturarFila(filaArrastrada);

        instalarGlassPaneArrastre();
        aplicarCursorArrastre(panelContenedor, true);
        actualizarArrastreDesdePantalla(screenPoint);
    }

    private void actualizarArrastreDesdePantalla(Point screenPoint){
        if(!dragActiva || screenPoint == null) return;
        ultimoPuntoArrastrePantalla = new Point(screenPoint);
        actualizarGhost(screenPoint);
        autoscrollDuranteArrastre(screenPoint);
        actualizarIndicadorInsercion(screenPoint);
    }

    private void finalizarArrastre(boolean cancelar){
        if(!dragActiva) return;

        Component vista = getViewport().getView();
        JPanel panelContenedor = vista instanceof JPanel panel ? panel : null;

        if(panelContenedor != null && !cancelar && ultimoPuntoArrastrePantalla != null){
            actualizarIndicadorInsercion(ultimoPuntoArrastrePantalla);
        }

        desinstalarGlassPaneArrastre();
        aplicarCursorArrastre(panelContenedor, false);

        if(panelContenedor != null && filaArrastrada != null){
            int indiceInsercionReal = cancelar
                    ? Math.max(0, Math.min(indiceOriginalArrastre, panelContenedor.getComponentCount() - 1))
                    : calcularIndiceRealInsercion(panelContenedor, indiceInsercionArrastre);

            panelContenedor.remove(filaArrastrada);
            if(!cancelar && indiceOriginalArrastre >= 0 && indiceOriginalArrastre < indiceInsercionReal){
                indiceInsercionReal--;
            }
            indiceInsercionReal = Math.max(0, Math.min(indiceInsercionReal, panelContenedor.getComponentCount()));
            panelContenedor.add(filaArrastrada, indiceInsercionReal);
            filaArrastrada.setVisible(true);
            panelContenedor.revalidate();
            panelContenedor.repaint();

            if(!cancelar){
                List<String> ids = construirOrdenIds(panelContenedor);
                if(!ids.isEmpty()){
                    gestorServidores.reordenarServidores(ids);
                }
            }
        }

        filaArrastrada = null;
        indiceOriginalArrastre = -1;
        indiceInsercionArrastre = -1;
        alturaArrastre = -1;
        offsetArrastre = null;
        ultimoPuntoArrastrePantalla = null;
        imagenArrastre = null;
        ubicacionArrastre = new Point();
        dragActiva = false;
        limpiarEstadoArrastrePendiente();
    }

    private void gestionarSoltadoSinArrastre(Point screenPoint){
        JPanel fila = filaArrastreCandidata;
        limpiarEstadoVisualPendiente(fila);
        if(fila == null || screenPoint == null) return;

        Point p = new Point(screenPoint);
        try{
            SwingUtilities.convertPointFromScreen(p, fila);
        } catch (IllegalComponentStateException ex){
            return;
        }

        if(!fila.contains(p)) return;
        seleccionarFila(fila, true);
    }

    private void limpiarEstadoVisualPendiente(JPanel fila){
        if(fila == null) return;
        refrescarTema(false);
        if(fila == filaSeleccionada){
            fila.setBorder(bordeSeleccionado);
            fila.setBackground(bgSelected);
        } else {
            fila.setBorder(bordeRedondo);
            fila.setBackground(bgNormal);
        }
        syncIconBg(fila);
        fila.repaint();
    }

    private void limpiarEstadoArrastrePendiente(){
        limpiarEstadoVisualPendiente(filaArrastreCandidata);
        filaArrastreCandidata = null;
        puntoPresionArrastre = null;
        puntoPresionPantalla = null;
    }

    private Point obtenerPuntoPantalla(MouseEvent e){
        if(e != null){
            try{
                return new Point(e.getXOnScreen(), e.getYOnScreen());
            } catch (IllegalComponentStateException ignored){
            }

            Component origen = e.getComponent();
            if(origen != null && origen.isShowing()){
                Point p = e.getPoint();
                SwingUtilities.convertPointToScreen(p, origen);
                return p;
            }
        }

        try{
            PointerInfo pointerInfo = MouseInfo.getPointerInfo();
            if(pointerInfo != null && pointerInfo.getLocation() != null){
                return pointerInfo.getLocation();
            }
        } catch (IllegalComponentStateException ignored){
        }

        return ultimoPuntoArrastrePantalla == null ? null : new Point(ultimoPuntoArrastrePantalla);
    }

    private void actualizarGhost(Point screenPoint){
        if(glassPaneArrastre == null || offsetArrastre == null) return;
        Point p = new Point(screenPoint);
        SwingUtilities.convertPointFromScreen(p, glassPaneArrastre);
        ubicacionArrastre = new Point(p.x - offsetArrastre.x, p.y - offsetArrastre.y);
        glassPaneArrastre.repaint();
    }

    private void actualizarIndicadorInsercion(Point screenPoint){
        Component vista = getViewport().getView();
        if(!(vista instanceof JPanel panelContenedor) || filaArrastrada == null || screenPoint == null) return;

        Point p = new Point(screenPoint);
        SwingUtilities.convertPointFromScreen(p, panelContenedor);

        int centroArrastre = p.y - (offsetArrastre == null ? 0 : offsetArrastre.y) + (Math.max(1, alturaArrastre) / 2);
        int nuevoIndice = calcularIndiceInsercionVisible(panelContenedor, centroArrastre);
        if(nuevoIndice == indiceInsercionArrastre) return;
        indiceInsercionArrastre = nuevoIndice;
        if(glassPaneArrastre != null){
            glassPaneArrastre.repaint();
        }
    }

    private int calcularIndiceInsercionVisible(JPanel panelContenedor, int centroArrastre){
        int indice = 0;
        for(Component component : panelContenedor.getComponents()){
            if(component == filaArrastrada) continue;
            Rectangle bounds = component.getBounds();
            int mitad = bounds.y + (bounds.height / 2);
            if(centroArrastre < mitad){
                return indice;
            }
            indice++;
        }
        return indice;
    }

    private int calcularIndiceRealInsercion(JPanel panelContenedor, int indiceVisible){
        if(indiceVisible <= 0) return 0;

        int visiblesRecorridos = 0;
        Component[] components = panelContenedor.getComponents();
        for(int i = 0; i < components.length; i++){
            Component component = components[i];
            if(component == filaArrastrada) continue;
            if(visiblesRecorridos >= indiceVisible){
                return i;
            }
            visiblesRecorridos++;
        }
        return panelContenedor.getComponentCount();
    }

    private int obtenerAlturaArrastre(JPanel fila){
        if(fila == null) return 1;
        int altura = fila.getHeight();
        if(altura > 0) return altura;
        Dimension preferred = fila.getPreferredSize();
        return preferred == null ? 1 : Math.max(1, preferred.height);
    }

    private int indiceComponente(Container parent, Component child){
        if(parent == null || child == null) return -1;
        Component[] components = parent.getComponents();
        for(int i = 0; i < components.length; i++){
            if(components[i] == child) return i;
        }
        return -1;
    }

    private BufferedImage capturarFila(JPanel fila){
        int width = Math.max(1, fila.getWidth());
        int height = Math.max(1, fila.getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        fila.paint(g2);
        g2.dispose();
        return image;
    }

    private void instalarGlassPaneArrastre(){
        if(imagenArrastre == null) return;
        rootPaneArrastre = SwingUtilities.getRootPane(this);
        if(rootPaneArrastre == null) return;

        glassPaneAnterior = rootPaneArrastre.getGlassPane();
        glassPaneAnteriorVisible = glassPaneAnterior != null && glassPaneAnterior.isVisible();
        glassPaneArrastre = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if(imagenArrastre == null) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f));
                g2.drawImage(imagenArrastre, ubicacionArrastre.x, ubicacionArrastre.y, null);
                pintarIndicadorInsercion(g2);
                g2.dispose();
            }
        };
        MouseAdapter dragOverlayMouse = new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if(dragActiva){
                    Point screenPoint = obtenerPuntoPantalla(e);
                    if(screenPoint != null){
                        actualizarArrastreDesdePantalla(screenPoint);
                    }
                    finalizarArrastre(false);
                    desinstalarListenerGlobalArrastre();
                }
            }
        };
        glassPaneArrastre.addMouseListener(dragOverlayMouse);
        glassPaneArrastre.setOpaque(false);
        glassPaneArrastre.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        rootPaneArrastre.setGlassPane(glassPaneArrastre);
        glassPaneArrastre.setVisible(true);
        glassPaneArrastre.repaint();
    }

    private void pintarIndicadorInsercion(Graphics2D g2){
        if(glassPaneArrastre == null || indiceInsercionArrastre < 0) return;
        if(indiceInsercionArrastre == indiceOriginalArrastre) return;
        Component vista = getViewport().getView();
        if(!(vista instanceof JPanel panelContenedor)) return;

        int yPanel = calcularYInsercionIndicador(panelContenedor, indiceInsercionArrastre);
        Point inicio = SwingUtilities.convertPoint(panelContenedor, panelContenedor.getInsets().left + 6, yPanel, glassPaneArrastre);
        Point fin = SwingUtilities.convertPoint(panelContenedor, Math.max(panelContenedor.getInsets().left + 24, panelContenedor.getWidth() - panelContenedor.getInsets().right - 6), yPanel, glassPaneArrastre);

        g2.setColor(acento);
        g2.fillRoundRect(inicio.x, inicio.y - 2, Math.max(12, fin.x - inicio.x), 4, 4, 4);
    }

    private int calcularYInsercionIndicador(JPanel panelContenedor, int indiceVisible){
        int top = panelContenedor.getInsets().top;
        int visiblesRecorridos = 0;
        int ultimoBottom = top;

        for(Component component : panelContenedor.getComponents()){
            if(component == filaArrastrada) continue;
            Rectangle bounds = component.getBounds();
            if(visiblesRecorridos >= indiceVisible){
                return bounds.y;
            }
            ultimoBottom = bounds.y + bounds.height;
            visiblesRecorridos++;
        }

        return ultimoBottom;
    }

    private void desinstalarGlassPaneArrastre(){
        if(rootPaneArrastre != null && glassPaneAnterior != null){
            rootPaneArrastre.setGlassPane(glassPaneAnterior);
            glassPaneAnterior.setVisible(glassPaneAnteriorVisible);
        } else if(glassPaneArrastre != null){
            glassPaneArrastre.setVisible(false);
        }
        glassPaneArrastre = null;
        glassPaneAnterior = null;
        rootPaneArrastre = null;
    }

    private List<String> construirOrdenIds(JPanel panelContenedor){
        List<String> ids = new ArrayList<>();
        for(Component component : panelContenedor.getComponents()){
            if(!(component instanceof JComponent comp)) continue;
            Object serverObj = comp.getClientProperty("server");
            if(!(serverObj instanceof Server server)) continue;
            if(server.getId() == null || server.getId().isBlank()) continue;
            ids.add(server.getId());
        }
        return ids;
    }

    private void instalarListenerGlobalArrastre(){
        if(dragEventListener != null) return;
        dragEventListener = event -> {
            if(!(event instanceof MouseEvent e)) return;

            if(e.getID() == MouseEvent.MOUSE_DRAGGED){
                Point screenPoint = obtenerPuntoPantalla(e);
                if(dragActiva){
                    actualizarArrastreDesdePantalla(screenPoint);
                } else if(filaArrastreCandidata != null && puntoPresionPantalla != null && screenPoint != null
                        && screenPoint.distance(puntoPresionPantalla) >= DRAG_THRESHOLD){
                    iniciarArrastreSiProcede(screenPoint);
                }
                return;
            }

            if(e.getID() != MouseEvent.MOUSE_RELEASED) return;

            Point releasePoint = obtenerPuntoPantalla(e);
            if(dragActiva){
                if(releasePoint != null){
                    actualizarArrastreDesdePantalla(releasePoint);
                }
                finalizarArrastre(false);
            } else if(filaArrastreCandidata != null){
                gestionarSoltadoSinArrastre(releasePoint);
                limpiarEstadoArrastrePendiente();
            }
            desinstalarListenerGlobalArrastre();
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(
                dragEventListener,
                AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
        );
    }

    private void desinstalarListenerGlobalArrastre(){
        if(dragEventListener == null) return;
        Toolkit.getDefaultToolkit().removeAWTEventListener(dragEventListener);
        dragEventListener = null;
    }

    private void autoscrollDuranteArrastre(Point screenPoint){
        Component vista = getViewport().getView();
        if(!(vista instanceof JPanel panelContenedor)) return;
        Point p = new Point(screenPoint);
        SwingUtilities.convertPointFromScreen(p, panelContenedor);
        int margen = 40;
        Rectangle visible = getViewport().getViewRect();
        if(p.y < visible.y + margen || p.y > visible.y + visible.height - margen){
            panelContenedor.scrollRectToVisible(new Rectangle(0, Math.max(0, p.y - margen), Math.max(1, panelContenedor.getWidth()), margen * 2));
        }
    }

    private JPopupMenu crearMenuServidor(Server server){
        JPopupMenu menu = new JPopupMenu();

        boolean activo = server != null && server.getServerProcess() != null && server.getServerProcess().isAlive();
        String serverDir = server == null ? null : server.getServerDir();
        boolean carpetaDisponible = serverDir != null && !serverDir.isBlank() && new File(serverDir).isDirectory();

        JMenuItem iniciar = new JMenuItem("Iniciar");
        iniciar.setEnabled(!activo);
        iniciar.addActionListener(e -> {
            try {
                gestorServidores.iniciarServidor(server);
            } catch (IOException | RuntimeException ex) {
                AppErrorReporter.reportSwing("No se ha podido iniciar el servidor desde la lista.", ex);
                if (server != null) {
                    server.appendConsoleLinea("[ERROR] No se ha podido iniciar el servidor: " + ex.getMessage());
                }
            }
        });
        menu.add(iniciar);

        JMenuItem parar = new JMenuItem("Parar");
        parar.setEnabled(activo);
        parar.addActionListener(e -> gestorServidores.safePararServidor(server));
        menu.add(parar);

        JMenuItem reiniciar = new JMenuItem("Reiniciar");
        reiniciar.setEnabled(activo);
        reiniciar.addActionListener(e -> {
            server.setRestartPending(true);
            gestorServidores.safePararServidor(server);
        });
        menu.add(reiniciar);

        menu.addSeparator();

        JMenuItem configuracion = new JMenuItem("Configuración");
        configuracion.addActionListener(e -> {
            if(contextMenuListener != null){
                contextMenuListener.abrirConfiguracion(server);
            }
        });
        menu.add(configuracion);

        JMenuItem mundos = new JMenuItem("Mundos");
        mundos.addActionListener(e -> {
            if(contextMenuListener != null){
                contextMenuListener.abrirMundos(server);
            }
        });
        menu.add(mundos);

        JMenuItem explorador = new JMenuItem("Abrir en explorador de archivos");
        explorador.setEnabled(carpetaDisponible);
        explorador.addActionListener(e -> abrirEnExplorador(server));
        menu.add(explorador);

        menu.addSeparator();

        boolean esFavorito = Boolean.TRUE.equals(server.getFavorito());
        JMenuItem favorito = new JMenuItem(esFavorito ? "\u2606 Quitar de favoritos" : "\u2605 Marcar como favorito");
        favorito.addActionListener(e -> gestorServidores.establecerFavorito(server, !esFavorito));
        menu.add(favorito);

        return menu;
    }

    private void abrirEnExplorador(Server server){
        if(server == null) return;
        String serverDir = server.getServerDir();
        if(serverDir == null || serverDir.isBlank()){
            JOptionPane.showMessageDialog(this, "El servidor seleccionado no tiene una carpeta válida.", "Abrir carpeta", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File carpeta = new File(serverDir);
        if(!carpeta.isDirectory()){
            JOptionPane.showMessageDialog(this, "El servidor seleccionado no tiene una carpeta válida.", "Abrir carpeta", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try{
            Desktop.getDesktop().open(carpeta);
        } catch (IOException ex){
            JOptionPane.showMessageDialog(this, "No se ha podido abrir la carpeta: " + ex.getMessage(), "Abrir carpeta", JOptionPane.ERROR_MESSAGE);
        } catch (UnsupportedOperationException ex){
            JOptionPane.showMessageDialog(this, "Este sistema no soporta abrir carpetas desde Java.", "Abrir carpeta", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void aplicarCursorArrastre(Component component, boolean arrastrando){
        if(component == null) return;
        component.setCursor(arrastrando ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if(component instanceof Container cont){
            for(Component child : cont.getComponents()){
                aplicarCursorArrastre(child, arrastrando);
            }
        }
    }

    private void hacerFilaClickable(Component c, MouseAdapter adapter){
        if(c == null) return;
        if(c instanceof JComponent component && Boolean.TRUE.equals(component.getClientProperty("excludeRowMouseHandling"))){
            return;
        }
        c.addMouseListener(adapter);
        c.addMouseMotionListener(adapter);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if(c instanceof Container cont){
            for(Component child : cont.getComponents()){
                hacerFilaClickable(child, adapter);
            }
        }
    }

    public void actualizarEstados(){
        Component vista = getViewport().getView();
        if(!(vista instanceof JPanel panelContenedor)) return;

        for(Component componente : panelContenedor.getComponents()){
            if(!(componente instanceof JPanel fila)) continue;

            Object serverObj = fila.getClientProperty("server");
            Object estadoObj = fila.getClientProperty("estadoLabel");

            if(!(serverObj instanceof Server server)) continue;
            if(!(estadoObj instanceof JLabel estado)) continue;

            actualizarEstadoLabel(estado, server);
        }

    }

    private void actualizarEstadoLabel(JLabel estado, Server server){
        boolean vivo = server != null && server.getServerProcess() != null && server.getServerProcess().isAlive();
        boolean deteniendo = vivo && Boolean.TRUE.equals(server.getDeteniendo());
        boolean iniciando = vivo && !deteniendo && Boolean.TRUE.equals(server.getIniciando());
        String dotColor = (iniciando || deteniendo) ? "#FF9800" : (vivo ? "#00C853" : "#D50000");
        String texto = deteniendo ? "Deteniendo" : (iniciando ? "Iniciando" : (vivo ? "Activo" : "Inactivo"));
        String html = "<html><span style='color:" + dotColor + ";'>●</span> " + texto + "</html>";
        // setText con HTML vuelve a parsear y a maquetar la fila; solo se hace si el estado cambió.
        if(!html.equals(estado.getText())){
            estado.setText(html);
        }
    }

    private JButton crearBotonFavorito(Server servidor, JPanel fila){
        JButton button = new JButton();
        button.putClientProperty("excludeRowMouseHandling", Boolean.TRUE);
        button.setFocusable(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(32, 32));
        button.setMinimumSize(new Dimension(32, 32));
        button.setMaximumSize(new Dimension(32, 32));
        button.addActionListener(e -> {
            boolean nuevoFavorito = !Boolean.TRUE.equals(servidor.getFavorito());
            gestorServidores.establecerFavorito(servidor, nuevoFavorito);
            actualizarBotonFavorito(button, servidor, true);
        });
        return button;
    }

    private void actualizarBotonFavorito(AbstractButton button, Server servidor, boolean hovered){
        if(button == null) return;
        boolean favorito = servidor != null && Boolean.TRUE.equals(servidor.getFavorito());
        String iconPath = favorito ? "doraicons/star.svg" : "doraicons/star-unselected.svg";
        button.setIcon(SvgIconFactory.create(iconPath, 24, 24, this::getFavoriteStarColor));
        button.setToolTipText(favorito ? "Quitar de favoritos" : "Marcar como favorito");
        button.setVisible(favorito || hovered);
        button.repaint();
    }

    private Color getFavoriteStarColor(){
        return AppTheme.isLightTheme() ? new Color(0xE0AA00) : new Color(0xFFC933);
    }

    private void aplicarEstadoHoverFila(JPanel fila, boolean hovered){
        if(fila == null) return;
        fila.putClientProperty("hovered", hovered);
        refrescarTema(false);
        if(fila != filaSeleccionada){
            fila.setBorder(hovered ? bordeHover : bordeRedondo);
            fila.setBackground(hovered ? bgHover : bgNormal);
            syncIconBg(fila);
            fila.repaint();
        }
        Object favoritoButtonObj = fila.getClientProperty("favoritoButton");
        Object serverObj = fila.getClientProperty("server");
        if(favoritoButtonObj instanceof AbstractButton favoritoButton && serverObj instanceof Server server){
            actualizarBotonFavorito(favoritoButton, server, hovered);
        }
    }

    private void marcarFilaSeleccionada(JPanel fila){
        refrescarTema(true);
        if(filaSeleccionada != null){
            filaSeleccionada.setBorder(bordeRedondo);
            filaSeleccionada.setBackground(bgNormal);
            syncIconBg(filaSeleccionada); // mantener el fondo del panel del icono sincronizado con la fila
            filaSeleccionada.repaint();
        }

        filaSeleccionada = fila;
        fila.setBackground(bgSelected);
        fila.setBorder(bordeSeleccionado);
        syncIconBg(fila); // mantener el fondo del panel del icono sincronizado con la fila

        fila.repaint();
    }

    private void syncIconBg(JPanel fila){ // actualiza el fondo del contenedor del icono para evitar "ghosting" al repintar/redimensionar
        if(fila == null) return;
        Object panelIconoObj = fila.getClientProperty("panelIcono");
        if(!(panelIconoObj instanceof JComponent panelIcono)) return;
        // Mantener el fondo transparente evita que sobresalga por los bordes redondeados
        panelIcono.setOpaque(false);
        panelIcono.repaint();
    }

    private void asegurarFilaVisible(JPanel fila){
        if(fila == null) return;
        JViewport viewport = getViewport();
        Component vista = viewport == null ? null : viewport.getView();
        if(viewport == null || !(vista instanceof JComponent vistaComponente)) return;

        Rectangle filaBounds = SwingUtilities.convertRectangle(fila.getParent(), fila.getBounds(), vistaComponente);
        Rectangle visible = viewport.getViewRect();
        if(visible.contains(filaBounds)) {
            return;
        }
        vistaComponente.scrollRectToVisible(filaBounds);
    }

    private void aplicarEllipsisLabel(JLabel label, int maxWidthPx){
        Object fullObj = label.getClientProperty("fullText");
        String full = (fullObj == null) ? "" : String.valueOf(fullObj);
        full = full.strip();

        label.setText(TextEllipsizer.right(full, label.getFontMetrics(label.getFont()), maxWidthPx));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // Mantener este scrollpane sin borde aunque cambie el tema (super.updateUI() puede restaurar el borde por defecto).
        setBorder(null);
        setViewportBorder(null);
        if (!uiReady) return;
        // updateComponentTreeUI() actualiza los UI delegates, pero los bordes/colores creados a mano
        // (FlatLineBorder con arc/color calculados) no se recalculan solos: los re-aplicamos.
        refrescarTema(true);
    }

    private Color oscurecer(Color c, float amount) {
        return AppTheme.darken(c, amount);
    }

    private Color tintar(Color accent, Color base, float t) {
        return AppTheme.tint(base, accent, t);
    }

    private void refrescarTema(boolean aplicarEnFilas){
        arc = AppTheme.getArc();

        acento = AppTheme.getMainAccent();
        base = AppTheme.getBorderColor();

        bgLista = AppTheme.getPanelBackground();
        bgNormal = AppTheme.getBackground();
        bgHover = AppTheme.tint(bgNormal, acento, 0.08f);
        bgPresionado = AppTheme.tint(bgNormal, acento, 0.14f);
        bgSelected = AppTheme.tint(bgNormal, acento, 0.18f);

        bordeRedondo = AppTheme.createRoundedBorder(insets, 1f);
        bordeHover = AppTheme.createAccentBorder(insets, 1f);
        bordeSeleccionado = AppTheme.createAccentBorder(insets, 3f);

        setBackground(bgLista);
        if(getViewport() != null){
            getViewport().setOpaque(true);
            getViewport().setBackground(bgLista);
        }

        if(!aplicarEnFilas) return;
        Component vista = getViewport().getView();
        if(!(vista instanceof JPanel panelContenedor)) return;

        panelContenedor.setOpaque(true);
        panelContenedor.setBackground(bgLista);

        for(Component componente : panelContenedor.getComponents()){
            if(!(componente instanceof JPanel fila)) continue;
            if(fila instanceof RoundedBackgroundPanel rbp) rbp.setArc(arc); // mantiene mismo radio que el borde
            if(Boolean.TRUE.equals(fila.getClientProperty("placeholder"))){
                fila.setBorder(AppTheme.createAccentBorder(insets, 2f));
                fila.setBackground(bgHover);
            } else if(fila == filaSeleccionada){
                fila.setBorder(bordeSeleccionado);
                fila.setBackground(bgSelected);
            } else if(Boolean.TRUE.equals(fila.getClientProperty("hovered"))){
                fila.setBorder(bordeHover);
                fila.setBackground(bgHover);
            } else {
                fila.setBorder(bordeRedondo);
                fila.setBackground(bgNormal);
            }
            Object favoritoButtonObj = fila.getClientProperty("favoritoButton");
            Object serverObj = fila.getClientProperty("server");
            if(favoritoButtonObj instanceof AbstractButton favoritoButton && serverObj instanceof Server server){
                actualizarBotonFavorito(favoritoButton, server, Boolean.TRUE.equals(fila.getClientProperty("hovered")));
            }
            syncIconBg(fila); // sincroniza el panel del icono con el fondo nuevo del tema
            fila.repaint();
        }

        panelContenedor.repaint();
        repaint();
    }

    private static final class ServerListPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(80, visibleRect.height - 24);
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

}
