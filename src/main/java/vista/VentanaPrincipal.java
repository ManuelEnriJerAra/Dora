/*
 * Fichero: VentanaPrincipal.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Esta es la ventana principal, extiende de JFrame y muestra todo el contenido del servidor, es el encargado de mostrar
 * todos los paneles y es la ventana con la que el usuario más va a interactuar.
 * */

package vista;

import controlador.GestorConfiguracion;
import controlador.GestorServidores;
import controlador.Main;

import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.extras.components.FlatSplitPane;

import modelo.Server;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;
import com.formdev.flatlaf.ui.FlatLineBorder;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import com.formdev.flatlaf.extras.FlatSVGIcon;


@SuppressWarnings("unused")
public class VentanaPrincipal extends JFrame {
    private static final String BASE_TITLE = "Dora - Minecraft Server Manager";
    private static final int DEBUG_TOGGLE_INFO_CLICKS = 9;
    private static final int SERVER_LIST_INITIAL_WIDTH = PanelIndicadoresRecursos.READABLE_SINGLE_ROW_WIDTH;
    private static final int SERVER_LIST_MIN_WIDTH = 240;
    private static final int TRAY_SERVER_NAME_COLUMN_WIDTH = 28;
    private static final String PROP_MANAGED_ROUNDED_BORDER = "dora.managedRoundedBorder";
    private static final String PROP_ROUNDED_BORDER_ENABLED = "dora.roundedBorderEnabled";

    private final JPanel panelIzquierdo, panelDerecho;
    private final GestorServidores gestorServidores;
    private JPanel ventanaPrincipalPanel;
    private JPanel rendimientoPanel;
    private JPanel wrapperIzquierdo;
    private JPanel wrapperDerecho;
    private JPanel barraWrapper;
    private CardPanel servidoresCard; // card de la izquierda con borde redondeado
    private JPanel servidoresPanel;
    private Server serverMostrado;
    private Consumer<String> consoleListenerActual;
    private JButton abrirCarpetaServerButton;
    private JButton borrarServerButton;
    private JButton nuevoServerButton;
    private JButton importarServerButton;
    private JButton refrescarListaServidoresButton;
    private JPanel botonesServidoresPanel;
    private final PanelServidores listaServidoresPanel;
    private boolean cambiandoANoServerDialog = false;
    private JSplitPane splitPrincipal;
    private JSplitPane splitHome;
    private PanelConfigServidor panelConfigServidor;
    private boolean divisorPrincipalInicializado;
    private boolean listaServidoresOcultaPorInfo;
    private int divisorPrincipalAntesInfo = -1;

    enum PaginaDerecha { HOME, MUNDO, CONFIG, STATS, AUTOMATION, EXTENSIONES, INFO }
    private record TemaInfo(String name, String className){}

    private PaginaDerecha paginaDerechaActual = PaginaDerecha.HOME;
    private JPanel panelDerechoCards;
    private CardLayout cardDerecho;
    private final Map<PaginaDerecha, JPanel> contenedoresPaginasDerechas = new EnumMap<>(PaginaDerecha.class);
    private VentanaPrincipalRightContentBuilder.Result contenidoDerechoActual;
    private JPanel panelBarraVertical;
    private CardPanel jugadoresCard; // card dentro del split del HOME
    private JPanel consolaCard; // card dentro del split del HOME
    private final Map<PaginaDerecha, JButton> navButtons = new EnumMap<>(PaginaDerecha.class);
    private final PropertyChangeListener debugModeListener;
    private PropertyChangeListener trayServerStateListener;
    private TrayIcon trayIcon;
    private boolean trayDisponible;
    private boolean avisoBandejaMostrado;
    private int infoClicksForDebug;

    public VentanaPrincipal(GestorServidores gestorServidores) {
        this.gestorServidores = gestorServidores;
        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.setSize(1280, 720);
        actualizarTituloDebug();
        configurarIconoVentana();
        // Registrar siempre el muestreo de recursos: si depende de la bandeja,
        // en sistemas sin bandeja (muchos escritorios Linux) CPU/RAM se quedan en 0%.
        PanelEstadisticas.registrarGestorParaMuestras(gestorServidores);
        instalarBandejaSistema();
        this.setLocationRelativeTo(null);
        debugModeListener = evt -> {
            if (!DebugMode.PROPERTY_ENABLED.equals(evt.getPropertyName())) return;
            SwingUtilities.invokeLater(this::actualizarTituloDebug);
        };
        DebugMode.addPropertyChangeListener(debugModeListener);
        ventanaPrincipalPanel = new JPanel(new BorderLayout()); // el panel principal, donde se aloja todo
        Color bgApp = AppTheme.getBackground();
        Color panelBg = AppTheme.getPanelBackground();
        ventanaPrincipalPanel.setBackground(bgApp);
        this.setContentPane(ventanaPrincipalPanel);

        // PANEL IZQUIERDO ---------------------------------------------------------------------------------------------
        panelIzquierdo = new JPanel(new BorderLayout()); // este es un panel auxiliar que engloba toda la parte izq
        panelIzquierdo.setOpaque(true);
        panelIzquierdo.setBackground(bgApp);

        // PANEL DE GRÁFICAS DE RENDIMIENTO
        rendimientoPanel = new JPanel(new BorderLayout());
        rendimientoPanel.setOpaque(true);
        rendimientoPanel.setBackground(bgApp);
        rendimientoPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        rendimientoPanel.add(new PanelIndicadoresRecursos(gestorServidores), BorderLayout.CENTER);
        panelIzquierdo.add(rendimientoPanel, BorderLayout.NORTH);

        // PANEL DE SERVIDORES (card con borde redondeado)
        servidoresCard = new CardPanel("Lista de servidores");
        servidoresCard.setBorder(BorderFactory.createEmptyBorder());
        panelIzquierdo.add(servidoresCard, BorderLayout.CENTER);

        refrescarListaServidoresButton = new FlatButton();
        refrescarListaServidoresButton.setToolTipText("Refrescar");
        AppTheme.applyRefreshIconButtonStyle(refrescarListaServidoresButton);
        servidoresCard.getHeaderActionsPanel().add(refrescarListaServidoresButton);

        servidoresPanel = new JPanel(new BorderLayout());
        servidoresPanel.setOpaque(true); // rellena el fondo bajo el listado para evitar "ghosting"
        servidoresPanel.setBackground(panelBg);
        servidoresCard.getContentPanel().add(servidoresPanel, BorderLayout.CENTER);

        // PANEL DE LISTADO DE SERVIDORES
        listaServidoresPanel = getPanelServidores(gestorServidores);
        servidoresPanel.add(listaServidoresPanel, BorderLayout.CENTER);
        refrescarListaServidoresButton.addActionListener(e -> {
            Server servidorAntes = gestorServidores.getServidorSeleccionado();
            gestorServidores.refrescarServidoresGuardados();
            gestorServidores.mostrarAvisoArranqueSiProcede(VentanaPrincipal.this);
            listaServidoresPanel.refrescarListado();
            boolean seleccionEliminada = servidorAntes != null
                    && gestorServidores.getServerById(servidorAntes.getId()) == null;
            if(seleccionEliminada){
                List<Server> servidoresRestantes = gestorServidores.getListaServidores();
                if(servidoresRestantes != null && !servidoresRestantes.isEmpty()){
                    SwingUtilities.invokeLater(listaServidoresPanel::seleccionarPrimero);
                } else {
                    gestorServidores.setServidorSeleccionado(null);
                    if(serverMostrado != null && consoleListenerActual != null){
                        serverMostrado.removeConsoleListener(consoleListenerActual);
                    }
                    serverMostrado = null;
                    consoleListenerActual = null;
                    borrarServerButton.setEnabled(false);
                    abrirCarpetaServerButton.setEnabled(false);
                    if(panelDerechoCards != null){
                        panelDerechoCards.removeAll();
                        panelDerechoCards.revalidate();
                        panelDerechoCards.repaint();
                    }
                }
            }
        });

        // PANEL DE BOTONES DE SERVIDORES
        botonesServidoresPanel = new JPanel(new GridLayout(1,4));
        botonesServidoresPanel.setOpaque(true);
        botonesServidoresPanel.setBackground(panelBg);
        botonesServidoresPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        servidoresPanel.add(botonesServidoresPanel, BorderLayout.SOUTH);

        nuevoServerButton = new FlatButton();
        nuevoServerButton.setToolTipText("Nuevo servidor");
        importarServerButton = new FlatButton();
                importarServerButton.setToolTipText("Importar");
        borrarServerButton = new FlatButton();
        borrarServerButton.setToolTipText("Eliminar");
        abrirCarpetaServerButton = new FlatButton();
                abrirCarpetaServerButton.setToolTipText("Abrir carpeta");
        abrirCarpetaServerButton.setEnabled(false);
        borrarServerButton.setEnabled(false);

        // Que se vean más: texto en negrita + más grande + más padding
        estilizarBoton(nuevoServerButton);
        estilizarBoton(importarServerButton);
        estilizarBoton(borrarServerButton);
        estilizarBoton(abrirCarpetaServerButton);
        aplicarIconoBotonServidores(nuevoServerButton, "doraicons/plus.svg");
        aplicarIconoBotonServidores(importarServerButton, "doraicons/download.svg");
        aplicarIconoBotonServidores(borrarServerButton, "doraicons/minus.svg");
        aplicarIconoBotonServidores(abrirCarpetaServerButton, "doraicons/folder.svg");
        configurarHoverIconoCarpeta(abrirCarpetaServerButton);

        botonesServidoresPanel.add(nuevoServerButton);
        botonesServidoresPanel.add(importarServerButton);
        botonesServidoresPanel.add(borrarServerButton);
        botonesServidoresPanel.add(abrirCarpetaServerButton);

        nuevoServerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Server server = gestorServidores.crearServidor();
                if(server != null){
                    seleccionarServidor(server);
                }
            }
        });
        importarServerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Server server = gestorServidores.importarServidor();
                if(server != null){
                    listaServidoresPanel.refrescarListado();
                    seleccionarServidor(server);
                }
            }
        });
        borrarServerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Server server = gestorServidores.getServidorSeleccionado();
                if(server == null){
                    borrarServerButton.setEnabled(false);
                    abrirCarpetaServerButton.setEnabled(false);
                    return;
                }
                if(server.getServerProcess() != null && server.getServerProcess().isAlive()){
                    JOptionPane.showMessageDialog(
                            VentanaPrincipal.this,
                            "No puedes eliminar un servidor mientras está activo. Páralo antes de eliminarlo.",
                            "Servidor activo",
                            JOptionPane.WARNING_MESSAGE
                    );
                    return;
                }

                int confirm = JOptionPane.showConfirmDialog(
                        VentanaPrincipal.this,
                        "¿Seguro que quieres eliminar '" + server.getDisplayName() + "' de la lista?\n(No borra la carpeta del disco)",
                        "Eliminar",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if(confirm != JOptionPane.YES_OPTION) return;

                List<Server> antes = gestorServidores.getListaServidores() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(gestorServidores.getListaServidores());
                Server candidato = null;
                for(int i = 0; i < antes.size(); i++){
                    Server s = antes.get(i);
                    if(s != null && s.getId() != null && s.getId().equals(server.getId())){
                        if(i > 0) candidato = antes.get(i - 1);
                        else if(i + 1 < antes.size()) candidato = antes.get(i + 1);
                        break;
                    }
                }

                boolean eliminado = gestorServidores.eliminarServidor(server);
                if(eliminado){
                    gestorServidores.setServidorSeleccionado(null);
                    if(serverMostrado != null && consoleListenerActual != null){
                        serverMostrado.removeConsoleListener(consoleListenerActual);
                    }
                    serverMostrado = null;
                    consoleListenerActual = null;
                    if(panelDerechoCards != null){
                        panelDerechoCards.removeAll();
                        panelDerechoCards.revalidate();
                        panelDerechoCards.repaint();
                    }
                    borrarServerButton.setEnabled(false);
                    abrirCarpetaServerButton.setEnabled(false);

                    if(gestorServidores.getListaServidores() == null || gestorServidores.getListaServidores().isEmpty()){
                        volverANoServerDialog(gestorServidores);
                    } else {
                        Server finalCandidato = candidato;
                        SwingUtilities.invokeLater(() -> {
                            if(finalCandidato != null){
                                seleccionarServidor(finalCandidato);
                            } else {
                                listaServidoresPanel.seleccionarPrimero();
                            }
                        });
                    }
                } else {
                    JOptionPane.showMessageDialog(
                            VentanaPrincipal.this,
                            "No se ha podido eliminar el servidor.",
                            "Eliminar",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
        abrirCarpetaServerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Server server = gestorServidores.getServidorSeleccionado();
                if(server == null){
                    JOptionPane.showMessageDialog(
                            VentanaPrincipal.this,
                            "No hay ningún servidor seleccionado.",
                            "Abrir carpeta",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    return;
                }
                if(server.getServerDir() == null || server.getServerDir().isBlank()){
                    JOptionPane.showMessageDialog(
                            VentanaPrincipal.this,
                            "El servidor seleccionado no tiene una carpeta válida.",
                            "Abrir carpeta",
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }
                try{
                    Desktop.getDesktop().open(new File(server.getServerDir()));
                } catch (IOException ex){
                    JOptionPane.showMessageDialog(
                            VentanaPrincipal.this,
                            "No se ha podido abrir la carpeta: " + ex.getMessage(),
                            "Abrir carpeta",
                            JOptionPane.ERROR_MESSAGE
                    );
                } catch (UnsupportedOperationException ex){
                    JOptionPane.showMessageDialog(
                            VentanaPrincipal.this,
                            "Este sistema no soporta abrir carpetas desde Java.",
                            "Abrir carpeta",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });

        new Timer(1000, e -> {
            // actualizar estado cada 1 segúndo
            listaServidoresPanel.actualizarEstados();
        }).start();

        // Si se vacia la lista por cualquier via, volvemos al dialogo inicial
        gestorServidores.addPropertyChangeListener("listaServidores", evt -> {
            Object nuevo = evt.getNewValue();
            if(!(nuevo instanceof java.util.List<?> lista)) return;
            if(!lista.isEmpty()) return;
            SwingUtilities.invokeLater(() -> volverANoServerDialog(gestorServidores));
        });

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if(trayDisponible){
                    ocultarEnBandeja();
                } else {
                    solicitarSalidaAplicacion();
                }
            }

            @Override
            public void windowClosed(WindowEvent e) {
                DebugMode.removePropertyChangeListener(debugModeListener);
                desinstalarBandejaSistema();
            }
        });


        // PANEL DERECHO -----------------------------------------------------------------------------------------------

        panelDerecho = new JPanel(new BorderLayout()); // este es un panel auxiliar que engloba toda la parte dcha
        panelDerecho.setOpaque(true);
        panelDerecho.setBackground(bgApp);

        // Cards (contenido del panel derecho)
        cardDerecho = new CardLayout();
        panelDerechoCards = new JPanel(cardDerecho);
        panelDerechoCards.setOpaque(true);
        panelDerechoCards.setBackground(bgApp);
        panelDerecho.add(panelDerechoCards, BorderLayout.CENTER);

        // Barra vertical de navegación (va a la IZQUIERDA de la ventana, fuera del split)
        panelBarraVertical = construirBarraVertical();

        // SPLIT PANE

        wrapperIzquierdo = new JPanel(new BorderLayout());
        wrapperIzquierdo.add(panelIzquierdo, BorderLayout.CENTER);
        wrapperIzquierdo.setOpaque(true);
        wrapperIzquierdo.setBackground(bgApp);
        wrapperIzquierdo.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
        wrapperIzquierdo.setPreferredSize(new Dimension(SERVER_LIST_INITIAL_WIDTH, 0));
        wrapperIzquierdo.setMinimumSize(new Dimension(SERVER_LIST_MIN_WIDTH, 0));

        wrapperDerecho = new JPanel(new BorderLayout());
        wrapperDerecho.add(panelDerecho, BorderLayout.CENTER);
        wrapperDerecho.setOpaque(true);
        wrapperDerecho.setBackground(bgApp);
        wrapperDerecho.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 8));
        wrapperDerecho.setMinimumSize(new Dimension(0, 0));

        splitPrincipal = new FlatSplitPane();
        splitPrincipal.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        splitPrincipal.setLeftComponent(wrapperIzquierdo);
        splitPrincipal.setRightComponent(wrapperDerecho);
        configurarSplitPane(splitPrincipal, 8);
        splitPrincipal.setResizeWeight(0.1);
        splitPrincipal.setOpaque(true);
        splitPrincipal.setBackground(bgApp);

        // Barra vertical al extremo izquierdo de la ventana
        barraWrapper = new JPanel(new BorderLayout());
        barraWrapper.setOpaque(true);
        barraWrapper.setBackground(bgApp);
        barraWrapper.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
        barraWrapper.add(panelBarraVertical, BorderLayout.CENTER);
        ventanaPrincipalPanel.add(barraWrapper, BorderLayout.WEST);
        ventanaPrincipalPanel.add(splitPrincipal, BorderLayout.CENTER);
        SwingUtilities.invokeLater(this::aplicarDivisorPrincipalInicial);

        // Fuerza un repintado completo al redimensionar para evitar artefactos visuales con componentes transparentes
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    aplicarDivisorPrincipalInicial();
                    JRootPane root = getRootPane();
                    if(root != null){
                        RepaintManager.currentManager(root).markCompletelyDirty(root);
                        root.repaint();
                    }
                });
            }
        });


        //ventanaPrincipalPanel.add(panelDerecho, BorderLayout.EAST);

        // Seleccion inicial (cuando ya están creados botones y panel derecho)
        SwingUtilities.invokeLater(() -> {
            if(gestorServidores.getServidorSeleccionado() == null
                    && gestorServidores.getListaServidores() != null
                    && !gestorServidores.getListaServidores().isEmpty()){
                listaServidoresPanel.seleccionarPrimero();
            }
        });
    }

    public VentanaPrincipal(GestorServidores gestorServidores, Server servidorASeleccionar){
        this(gestorServidores);
        if(servidorASeleccionar != null){
            SwingUtilities.invokeLater(() -> seleccionarServidor(servidorASeleccionar));
        }
    }

    private void renderizarPanelDerecho(Server server) {
        if (server == null || panelDerechoCards == null) {
            return;
        }

        if (serverMostrado != null && consoleListenerActual != null) {
            serverMostrado.removeConsoleListener(consoleListenerActual);
        }
        consoleListenerActual = null;
        splitHome = null;
        jugadoresCard = null;
        consolaCard = null;
        panelConfigServidor = null;
        serverMostrado = server;

        panelDerechoCards.removeAll();
        contenedoresPaginasDerechas.clear();
        contenidoDerechoActual = new VentanaPrincipalRightContentBuilder()
                .build(server, gestorServidores, this::actualizarServidorTrasConversion);
        prepararContenedoresPaginasDerechas();

        PaginaDerecha paginaAMostrar = paginaDerechaActual != null ? paginaDerechaActual : PaginaDerecha.HOME;
        setPaginaDerecha(paginaAMostrar);
        panelDerechoCards.revalidate();
        panelDerechoCards.repaint();
    }

    private void actualizarServidorTrasConversion(Server server) {
        if (server == null) {
            return;
        }
        gestorServidores.setServidorSeleccionado(server);
        if (listaServidoresPanel != null) {
            listaServidoresPanel.refrescarListado();
            listaServidoresPanel.mostrarSeleccionServidor(server);
        }
        if (panelDerecho != null) {
            renderizarPanelDerecho(server);
        }
    }

    private JPanel construirBarraVertical() {
        VentanaPrincipalNavigationBuilder.Result navigation = new VentanaPrincipalNavigationBuilder()
                .build(this::navegarAPaginaDerecha, this::abrirSelectorTema, button -> setPaginaDerecha(paginaDerechaActual));
        navButtons.clear();
        navButtons.putAll(navigation.navButtons());
        return navigation.panel();
    }

    private void mostrarPanelDerecho(Server server, GestorServidores gestorServidores){
        panelDerechoCards.removeAll();

        JPanel home = new JPanel(new BorderLayout(0, 8));
        home.setOpaque(false);

        PanelTotalServidor panelTotalServidor = new PanelTotalServidor(gestorServidores);
        PanelJugadores panelJugadores = new PanelJugadores(gestorServidores, false);
        PanelConsola panelConsola = new PanelConsola(gestorServidores);
        panelConsola.setPreferredSize(new Dimension(0, 100));

        // Todo lo que está encima de los jugadores en un "card" con borde redondeado (FlatLaf)
        CardPanel headerCard = new CardPanel("Servidor seleccionado");
        headerCard.setBorder(BorderFactory.createEmptyBorder());
        headerCard.getContentPanel().add(panelTotalServidor, BorderLayout.CENTER);
        headerCard.setFullHeightSideComponent(new PanelControlServidor(gestorServidores));

        home.add(headerCard, BorderLayout.NORTH);

        // Jugadores y consola con splitpane y bordes redondeados
        jugadoresCard = new CardPanel("Jugadores");
        jugadoresCard.setBorder(BorderFactory.createEmptyBorder());
        jugadoresCard.getContentPanel().add(panelJugadores, BorderLayout.CENTER);

        consolaCard = new JPanel(new BorderLayout());
        consolaCard.setOpaque(false);
        consolaCard.setBorder(null); // normalmente sin borde
        setBordeRedondoGestionado(consolaCard, false);
        consolaCard.add(panelConsola, BorderLayout.CENTER);

        splitHome = new FlatSplitPane();
        splitHome.setOrientation(JSplitPane.VERTICAL_SPLIT);
        splitHome.setTopComponent(jugadoresCard);
        splitHome.setBottomComponent(consolaCard);
        configurarSplitPane(splitHome, 8);
        splitHome.setResizeWeight(0.7);

        home.add(splitHome, BorderLayout.CENTER);

        // Pintar historial actual
        panelConsola.actualizarConsola();

        // Desenganchar listener del server anterior
        if(serverMostrado != null && consoleListenerActual != null){
            serverMostrado.removeConsoleListener(consoleListenerActual);
        }

        // Reenganchar listener
        consoleListenerActual = panelConsola::escribirLinea;
        server.addConsoleListener(consoleListenerActual);
        serverMostrado = server;

        panelConfigServidor = new PanelConfigServidor(gestorServidores);
        JPanel mundo = new PanelMundo(gestorServidores, () -> {
            if(panelConfigServidor != null){
                panelConfigServidor.reload();
            }
        });
        JPanel config = panelConfigServidor;
        JPanel stats = crearPanelEstadisticas(server);
        JPanel info = new JPanel(new BorderLayout());
        info.setOpaque(false);

        panelDerechoCards.add(home, PaginaDerecha.HOME.name());
        panelDerechoCards.add(mundo, PaginaDerecha.MUNDO.name());
        panelDerechoCards.add(config, PaginaDerecha.CONFIG.name());
        panelDerechoCards.add(stats, PaginaDerecha.STATS.name());
        panelDerechoCards.add(info, PaginaDerecha.INFO.name());

        PaginaDerecha paginaAMostrar = paginaDerechaActual != null ? paginaDerechaActual : PaginaDerecha.HOME;
        setPaginaDerecha(paginaAMostrar);
        panelDerechoCards.revalidate();
        panelDerechoCards.repaint();
    }

    private JPanel crearBarraVertical(){

        CardPanel barra = new CardPanel("");
        barra.setBackground(AppTheme.getPanelBackground());
        barra.setPreferredSize(new Dimension(56, 0)); // más estrecha

        JPanel botones = new JPanel();
        botones.setOpaque(false);
        botones.setLayout(new BoxLayout(botones, BoxLayout.Y_AXIS));

        FlatSVGIcon iconHome = new FlatSVGIcon("doraicons/home.svg", 32, 32);
        FlatSVGIcon iconMundo = new FlatSVGIcon("doraicons/earth.svg", 32, 32);
        FlatSVGIcon iconConfig = new FlatSVGIcon("doraicons/settings.svg", 32, 32);
        FlatSVGIcon iconStats = new FlatSVGIcon("doraicons/chart.svg", 32, 32);
        FlatSVGIcon iconTemas = new FlatSVGIcon("doraicons/pallete.svg", 32, 32);
        FlatSVGIcon iconInfo = new FlatSVGIcon("doraicons/info.svg", 32, 32);

        JButton home = crearSVGButton(iconHome, "Home", PaginaDerecha.HOME);
        JButton mundo = crearSVGButton(iconMundo, "Mundos", PaginaDerecha.MUNDO);
        JButton config = crearSVGButton(iconConfig, "Configuración del servidor", PaginaDerecha.CONFIG);
        JButton stats = crearSVGButton(iconStats, "Estadísticas", PaginaDerecha.STATS);
        JButton temas = crearActionButton(iconTemas, "Temas", this::abrirSelectorTema);
        JButton info = crearSVGButton(iconInfo, "Información", PaginaDerecha.INFO);

        botones.add(home);
        botones.add(Box.createVerticalStrut(8));
        botones.add(mundo);
        botones.add(Box.createVerticalStrut(8));
        botones.add(config);
        botones.add(Box.createVerticalStrut(8));
        botones.add(stats);
        botones.add(Box.createVerticalGlue()); // fijar los siguientes al fondo
        botones.add(temas); // abajo
        botones.add(Box.createVerticalStrut(6));
        botones.add(info); // pegado debajo del pincel
        botones.add(Box.createVerticalStrut(4)); // pequeño margen inferior

        // Hover: se calcula en el momento para que, si el usuario cambia de tema, coja el nuevo arc y color.
        MouseAdapter navHover = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                Object src = e.getSource();
                if(!(src instanceof JButton b)) return;
                if(b.getBackground() != null && b.isOpaque()) return; // ya seleccionado: no alterar
                Color hoverSeleccion = colorSeleccionPanelServidores();
                b.setOpaque(true);
                b.setContentAreaFilled(true);
                b.setBackground(hoverSeleccion);
                b.setBorder(new FlatLineBorder(new Insets(6,6,6,6), AppTheme.getMainAccent(), 1f, AppTheme.getArc()));
                b.repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                setPaginaDerecha(paginaDerechaActual); // restablecer estado real
            }
        };
        home.addMouseListener(navHover);
        mundo.addMouseListener(navHover);
        config.addMouseListener(navHover);
        stats.addMouseListener(navHover);
        info.addMouseListener(navHover);

        MouseAdapter temasHover = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                Color hoverSeleccion = colorSeleccionPanelServidores();
                temas.setOpaque(true);
                temas.setContentAreaFilled(true);
                temas.setBackground(hoverSeleccion);
                temas.setBorder(new FlatLineBorder(new Insets(6,6,6,6), AppTheme.getMainAccent(), 1f, AppTheme.getArc()));
                temas.repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                temas.setOpaque(false);
                temas.setContentAreaFilled(false);
                temas.setBackground(null);
                temas.setBorder(new FlatLineBorder(new Insets(6,6,6,6), AppTheme.getTransparentColor(), 1f, AppTheme.getArc()));
                temas.repaint();
            }
        };
        temas.addMouseListener(temasHover);

        barra.getContentPanel().add(botones, BorderLayout.CENTER);
        return barra;
    }

    private JButton crearSVGButton(FlatSVGIcon icon, String tooltip, PaginaDerecha pagina){
        FlatButton b = new FlatButton();
        b.setIcon(icon);
        b.setFocusPainted(false);
        b.setAlignmentX(Component.LEFT_ALIGNMENT); // fijar a la izquierda
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        b.setToolTipText(tooltip);
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 18f));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorderPainted(true); // permitimos dibujar el borde en hover/seleccion
        b.setContentAreaFilled(false); // sin color de fondo por defecto
        b.setOpaque(false); // se pintará sólo cuando esté seleccionado
        b.setBorder(new FlatLineBorder(new Insets(6,6,6,6), AppTheme.getTransparentColor(), 1f, AppTheme.getArc())); // mantiene tamaño estable y permite mostrar borde en hover
        b.putClientProperty("JButton.buttonType", "roundRect"); // mantener esquinas redondeadas con FlatLaf
        b.addActionListener(e -> navegarAPaginaDerecha(pagina));
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> AppTheme.getForeground())); // adaptar color del icono al tema
        navButtons.put(pagina, b);
        return b;
    }

    private JPanel crearPanelEstadisticas(Server server) {
        return new PanelEstadisticas(gestorServidores, server);
    }

    private JButton crearActionButton(FlatSVGIcon icon, String tooltip, Runnable action){
        FlatButton b = new FlatButton();
        b.setIcon(icon);
        b.setFocusPainted(false);
        b.setAlignmentX(Component.LEFT_ALIGNMENT); // fijar a la izquierda
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        b.setToolTipText(tooltip);
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 18f));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorderPainted(true); // mantener visible el borde redondeado
        b.setContentAreaFilled(false); // fondo transparente
        b.setOpaque(false);
        b.setBorder(new FlatLineBorder(new Insets(6,6,6,6), AppTheme.getTransparentColor(), 1f, AppTheme.getArc()));
        b.putClientProperty("JButton.buttonType", "roundRect"); // borde redondeado al seleccionar
        b.addActionListener(e -> {
            if(action != null) action.run();
        });
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> AppTheme.getForeground())); // adaptar color del icono al tema
        return b;
    }

    private void prepararContenedoresPaginasDerechas() {
        for (PaginaDerecha pagina : PaginaDerecha.values()) {
            JPanel contenedor = new JPanel(new BorderLayout());
            contenedor.setOpaque(false);
            contenedoresPaginasDerechas.put(pagina, contenedor);
            panelDerechoCards.add(contenedor, pagina.name());
        }
    }

    private void cargarPaginaDerechaSiNecesario(PaginaDerecha pagina) {
        if (pagina == null) {
            pagina = PaginaDerecha.HOME;
        }
        JPanel contenedor = contenedoresPaginasDerechas.get(pagina);
        if (contenedor == null || contenidoDerechoActual == null || contenedor.getComponentCount() > 0) {
            return;
        }

        JPanel contenido = switch (pagina) {
            case HOME -> contenidoDerechoActual.homePanel();
            case MUNDO -> contenidoDerechoActual.mundoPanel();
            case CONFIG -> contenidoDerechoActual.configPanel();
            case STATS -> contenidoDerechoActual.statsPanel();
            case AUTOMATION -> contenidoDerechoActual.automationPanel();
            case EXTENSIONES -> contenidoDerechoActual.extensionesPanel();
            case INFO -> contenidoDerechoActual.infoPanel();
        };

        contenedor.add(contenido, BorderLayout.CENTER);

        if (pagina == PaginaDerecha.HOME) {
            splitHome = contenidoDerechoActual.splitHome();
            jugadoresCard = contenidoDerechoActual.jugadoresCard();
            consolaCard = contenidoDerechoActual.consolaCard();
            setBordeRedondoGestionado(consolaCard, false);
            configurarSplitPane(splitHome, 8);

            PanelConsola panelConsola = contenidoDerechoActual.panelConsola();
            panelConsola.actualizarConsola();
            consoleListenerActual = panelConsola::escribirLinea;
            if (serverMostrado != null) {
                serverMostrado.addConsoleListener(consoleListenerActual);
            }
        } else if (pagina == PaginaDerecha.CONFIG) {
            panelConfigServidor = contenidoDerechoActual.panelConfigServidor();
        }

        contenedor.revalidate();
        contenedor.repaint();
    }

    private void setPaginaDerecha(PaginaDerecha pagina){
        if(pagina == null) pagina = PaginaDerecha.HOME;
        paginaDerechaActual = pagina;
        cargarPaginaDerechaSiNecesario(pagina);
        cardDerecho.show(panelDerechoCards, pagina.name());
        actualizarVisibilidadListaServidores(pagina == PaginaDerecha.INFO);

        for(Map.Entry<PaginaDerecha, JButton> e : navButtons.entrySet()){
            JButton b = e.getValue();
            boolean sel = e.getKey() == pagina;
            VentanaPrincipalNavigationBuilder.actualizarIconoNavegacion(b, sel);
        }
    }

    // Calcula el mismo color de selección usado en PanelServidores (tinte del acento sobre el fondo)
    private Color colorSeleccionPanelServidores(){
        return AppTheme.getSelectionBackground();
    }

    private void actualizarVisibilidadListaServidores(boolean ocultar) {
        if (splitPrincipal == null || wrapperIzquierdo == null || wrapperDerecho == null) {
            return;
        }
        if (ocultar == listaServidoresOcultaPorInfo) {
            return;
        }

        listaServidoresOcultaPorInfo = ocultar;
        if (ocultar) {
            if (splitPrincipal.getWidth() > 0) {
                divisorPrincipalAntesInfo = splitPrincipal.getDividerLocation();
            }
            wrapperIzquierdo.setVisible(false);
            wrapperIzquierdo.setMinimumSize(new Dimension(0, 0));
            wrapperIzquierdo.setPreferredSize(new Dimension(0, 0));
            wrapperDerecho.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            splitPrincipal.setDividerSize(0);
            splitPrincipal.setDividerLocation(0);
        } else {
            wrapperIzquierdo.setVisible(true);
            wrapperIzquierdo.setMinimumSize(new Dimension(SERVER_LIST_MIN_WIDTH, 0));
            wrapperIzquierdo.setPreferredSize(new Dimension(SERVER_LIST_INITIAL_WIDTH, 0));
            wrapperDerecho.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 8));
            configurarSplitPane(splitPrincipal, 8);
            int restoredDivider = divisorPrincipalAntesInfo > 0
                    ? divisorPrincipalAntesInfo
                    : Math.max(SERVER_LIST_MIN_WIDTH, Math.min(SERVER_LIST_INITIAL_WIDTH, splitPrincipal.getWidth() / 3));
            SwingUtilities.invokeLater(() -> splitPrincipal.setDividerLocation(restoredDivider));
        }

        splitPrincipal.revalidate();
        splitPrincipal.repaint();
    }

    private void abrirSelectorTema(){
        SwingUtilities.invokeLater(() -> {
            java.util.List<TemaInfo> disponibles = new ArrayList<>();

            // 1) Preferimos la lista completa de temas de FlatLaf
            try{
                for(FlatAllIJThemes.FlatIJLookAndFeelInfo info : FlatAllIJThemes.INFOS){
                    if(info == null) continue;
                    String name = info.getName();
                    String className = info.getClassName();
                    if(name == null || className == null || name.isBlank() || className.isBlank()) continue;
                    disponibles.add(new TemaInfo(name, className));
                }
            } catch (Throwable ignored){
                // fallback abajo
            }

            // 2) Fallback: los LAF instalados en UIManager
            if(disponibles.isEmpty()){
                for(UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()){
                    if(info == null) continue;
                    String cn = info.getClassName();
                    if(cn == null) continue;
                    if(cn.startsWith("com.formdev.flatlaf.intellijthemes.") || cn.startsWith("com.formdev.flatlaf.themes.") || cn.startsWith("com.formdev.flatlaf.")){
                        disponibles.add(new TemaInfo(info.getName(), cn));
                    }
                }
            }

            // 3) Fallback final: un conjunto mínimo conocido de temas FlatLaf empaquetados
            if(disponibles.isEmpty()){
                disponibles.add(new TemaInfo("Solarized Light", "com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme"));
                disponibles.add(new TemaInfo("Solarized Dark", "com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme"));
                disponibles.add(new TemaInfo("Arc", "com.formdev.flatlaf.intellijthemes.FlatArcIJTheme"));
                disponibles.add(new TemaInfo("Arc Dark", "com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme"));
                disponibles.add(new TemaInfo("Material Solarized Light", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTSolarizedLightIJTheme"));
                disponibles.add(new TemaInfo("Material Solarized Dark", "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTSolarizedDarkIJTheme"));
            }

            if(disponibles.isEmpty()){
                JOptionPane.showMessageDialog(this, "No se han encontrado temas de FlatLaf.", "Temas", JOptionPane.WARNING_MESSAGE);
                return;
            }

            disponibles.sort(Comparator.comparing(TemaInfo::name, String.CASE_INSENSITIVE_ORDER));

            String current = UIManager.getLookAndFeel() != null ? UIManager.getLookAndFeel().getName() : null;
            String[] nombres = new String[disponibles.size()];
            for(int i = 0; i < disponibles.size(); i++){
                nombres[i] = disponibles.get(i).name();
            }

            String elegido = (String) JOptionPane.showInputDialog(
                    this,
                    "Selecciona un tema:",
                    "Temas",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    nombres,
                    current
            );
            if(elegido == null) return;

            String className = null;
            for(TemaInfo info : disponibles){
                if(info != null && elegido.equals(info.name())){
                    className = info.className();
                    break;
                }
            }
            if(className == null) return;

            try{
                UIManager.setLookAndFeel(className);
                // Al cambiar de LAF, los defaults de UIManager se reinician; reaplicamos nuestros ajustes
                // para que los bordes/arcos no cambien o desaparezcan.
                Main.aplicarPreferenciasUI();
                FlatLaf.updateUI();
                for(Window w : Window.getWindows()){
                    SwingUtilities.updateComponentTreeUI(w);
                    w.invalidate();
                    w.validate();
                    w.repaint();
                }
                // FlatLaf puede reponer la UI del splitpane; la reconfiguramos
                reconfigurarSplitPanes();
                // Los bordes redondeados creados a mano (FlatLineBorder) no se recalculan solos.
                // Al cambiar de tema, volvemos a generar esos bordes con los nuevos valores de UIManager.
                refreshThemeStyles();
                GestorConfiguracion.guardarTema(className);
            } catch (Exception ex){
                JOptionPane.showMessageDialog(this, "No se pudo aplicar el tema: " + ex.getMessage(), "Temas", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void aplicarBordeRedondoGestionado(JComponent c, Insets insets, Color borderColor, float thickness, int arc){
        if(c == null) return;
        if(!Boolean.TRUE.equals(c.getClientProperty(PROP_MANAGED_ROUNDED_BORDER))) return;

        Object enabledObj = c.getClientProperty(PROP_ROUNDED_BORDER_ENABLED);
        boolean enabled = !(enabledObj instanceof Boolean b) || b;

        Border b = c.getBorder();
        if(!enabled){
            // Si el borde estaba desactivado (p.ej. setBorder(null)), quitamos el borde que el tema pueda reponer
            // al cambiar de Look&Feel, para mantener exactamente el mismo aspecto.
            if(b == null) return;
            if(b instanceof FlatLineBorder || b instanceof UIResource){
                c.setBorder(null);
            }
            return;
        }

        // Tras cambiar de tema, Swing/FlatLaf puede restaurar bordes UIResource o dejarlos a null.
        // Reponemos nuestro borde redondeado para mantener el mismo aspecto.
        if(b == null || b instanceof UIResource){
            c.setBorder(new FlatLineBorder(insets, borderColor, thickness, arc));
            return;
        }
        // Si ya es nuestro tipo de borde, lo regeneramos para asegurar que siga teniendo el arc correcto.
        if(b instanceof FlatLineBorder){
            c.setBorder(new FlatLineBorder(insets, borderColor, thickness, arc));
        }
    }

    private static void setBordeRedondoGestionado(JComponent c, boolean enabled){
        if(c == null) return;
        c.putClientProperty(PROP_MANAGED_ROUNDED_BORDER, Boolean.TRUE);
        c.putClientProperty(PROP_ROUNDED_BORDER_ENABLED, enabled);
    }

    private void refreshThemeStyles() {
        Color bgApp = AppTheme.getBackground();
        Color panelBg = AppTheme.getPanelBackground();

        Container cp = getContentPane();
        if (cp != null) cp.setBackground(bgApp);
        if (ventanaPrincipalPanel != null) ventanaPrincipalPanel.setBackground(bgApp);
        if (panelIzquierdo != null) panelIzquierdo.setBackground(bgApp);
        if (panelDerecho != null) panelDerecho.setBackground(bgApp);
        if (rendimientoPanel != null) rendimientoPanel.setBackground(bgApp);
        if (wrapperIzquierdo != null) wrapperIzquierdo.setBackground(bgApp);
        if (wrapperDerecho != null) wrapperDerecho.setBackground(bgApp);
        if (barraWrapper != null) barraWrapper.setBackground(bgApp);
        if (panelDerechoCards != null) {
            panelDerechoCards.setOpaque(true);
            panelDerechoCards.setBackground(bgApp);
        }

        int arc = AppTheme.getArc();
        Color borderColor = AppTheme.getBorderColor();

        if (servidoresCard != null) {
            servidoresCard.setBackground(panelBg);
            servidoresCard.refreshTheme();
            servidoresCard.setBorder(BorderFactory.createEmptyBorder());
        }
        if (servidoresPanel != null) {
            servidoresPanel.setBackground(panelBg);
        }
        if (botonesServidoresPanel != null) {
            botonesServidoresPanel.setBackground(panelBg);
        }
        if (refrescarListaServidoresButton != null) {
            refrescarListaServidoresButton.setBackground(panelBg);
            refrescarListaServidoresButton.repaint();
        }
        if (nuevoServerButton != null) nuevoServerButton.setBackground(bgApp);
        if (importarServerButton != null) importarServerButton.setBackground(bgApp);
        if (borrarServerButton != null) borrarServerButton.setBackground(bgApp);
        if (abrirCarpetaServerButton != null) abrirCarpetaServerButton.setBackground(bgApp);
        if (panelBarraVertical instanceof CardPanel navigationCard) {
            navigationCard.setBackground(panelBg);
            navigationCard.setBorderColor(borderColor);
            navigationCard.setBorderThickness(1f);
            navigationCard.refreshTheme();
            navigationCard.setBorder(BorderFactory.createEmptyBorder());
        } else if (panelBarraVertical != null) {
            panelBarraVertical.setBackground(panelBg);
            aplicarBordeRedondoGestionado(panelBarraVertical, new Insets(6, 6, 6, 6), borderColor, 1f, arc);
        }
        if (jugadoresCard != null) {
            jugadoresCard.setBackground(panelBg);
            jugadoresCard.refreshTheme();
            jugadoresCard.setBorder(BorderFactory.createEmptyBorder());
        }
        if (consolaCard != null) {
            aplicarBordeRedondoGestionado(consolaCard, new Insets(8, 8, 8, 8), borderColor, 1f, arc);
        }

        // Recalcula colores y bordes de los botones de navegación según el tema actual.
        setPaginaDerecha(paginaDerechaActual);
        // Fuerza a que el listado recalcule bordes/colores (también lo hace en updateUI()).
        if (listaServidoresPanel != null) {
            listaServidoresPanel.updateUI();
            listaServidoresPanel.repaint();
        }

        // El HOME se construye con varios componentes y scrolls personalizados. Si el tema cambia,
        // reconstruimos esa zona igual que cuando el usuario cambia de servidor, que es justo cuando
        // el borde rectangular desaparece.
        if (serverMostrado != null && this.gestorServidores != null && panelDerechoCards != null) {
            PaginaDerecha paginaActual = paginaDerechaActual;
            renderizarPanelDerecho(serverMostrado);
            setPaginaDerecha(paginaActual);
        }
    }

    private void configurarSplitPane(JSplitPane split, int dividerSize){
        if(split == null) return;
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(false);
        split.setBorder(null);
        split.setDividerSize(dividerSize);
        Color bg = AppTheme.getBackground();
        if(bg != null) split.setBackground(bg);

        // Forzar repintado completo cuando se mueve el divisor (evita artefactos de blit)
        if(split.getClientProperty("fullRepaintOnDivider") == null){
            split.putClientProperty("fullRepaintOnDivider", Boolean.TRUE);
            split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
                JRootPane root = getRootPane();
                if(root == null) return;
                RepaintManager.currentManager(root).markCompletelyDirty(root);
                root.repaint();
            });
        }
    }

    private void aplicarDivisorPrincipalInicial(){
        if(divisorPrincipalInicializado || splitPrincipal == null) return;

        int splitWidth = splitPrincipal.getWidth();
        if(splitWidth <= 0) return;

        int dividerSize = Math.max(0, splitPrincipal.getDividerSize());
        int maxLeftWidth = Math.max(SERVER_LIST_MIN_WIDTH, splitWidth - dividerSize - 480);
        int leftWidth = Math.min(SERVER_LIST_INITIAL_WIDTH, maxLeftWidth);
        leftWidth = Math.max(SERVER_LIST_MIN_WIDTH, leftWidth);

        splitPrincipal.setDividerLocation(leftWidth);
        divisorPrincipalInicializado = true;
    }

    private void reconfigurarSplitPanes(){
        if(splitPrincipal != null) configurarSplitPane(splitPrincipal, 8);
        if(splitHome != null) configurarSplitPane(splitHome, 8);
    }

    private void seleccionarServidor(Server server){
        if(server == null) return;
        if(!aplicarCambioServidor(server, false)) return;
        listaServidoresPanel.mostrarSeleccionServidor(server);
    }

    private void volverANoServerDialog(GestorServidores gestorServidores){
        if(cambiandoANoServerDialog) return;
        if(!isDisplayable()) return;
        if(!confirmarSalidaConfiguracion(null, null)) return;
        cambiandoANoServerDialog = true;

        if(serverMostrado != null && consoleListenerActual != null){
            serverMostrado.removeConsoleListener(consoleListenerActual);
        }
        serverMostrado = null;
        consoleListenerActual = null;

        NoServerFrame dialog = new NoServerFrame(gestorServidores);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        this.dispose();
    }

    private void configurarIconoVentana(){
        Image image = cargarLogoAplicacion();
        if(image != null){
            setIconImage(image);
        }
    }

    private Image cargarLogoAplicacion(){
        java.net.URL urlImagen = getClass().getResource("/doraapp/logo.png");
        if(urlImagen == null) return null;
        return new ImageIcon(urlImagen).getImage();
    }

    private void instalarBandejaSistema(){
        try{
            if(!SystemTray.isSupported()) return;

            Image image = cargarLogoAplicacion();
            if(image == null) return;

            trayIcon = new TrayIcon(image, BASE_TITLE);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> restaurarDesdeBandeja());
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    refrescarMenuBandejaSiCorresponde(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    refrescarMenuBandejaSiCorresponde(e);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if(e.getButton() == MouseEvent.BUTTON1){
                        restaurarDesdeBandeja();
                    }
                }
            });

            trayServerStateListener = evt -> EventQueue.invokeLater(this::actualizarMenuBandeja);
            gestorServidores.addPropertyChangeListener("estadoServidor", trayServerStateListener);
            gestorServidores.addPropertyChangeListener("listaServidores", trayServerStateListener);
            actualizarMenuBandeja();
            SystemTray.getSystemTray().add(trayIcon);
            trayDisponible = true;
        } catch (AWTException ex){
            desinstalarBandejaSistema();
        } catch (RuntimeException ex){
            desinstalarBandejaSistema();
        }
    }

    private void refrescarMenuBandejaSiCorresponde(MouseEvent e){
        if(e == null) return;
        if(e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3){
            EventQueue.invokeLater(this::actualizarMenuBandeja);
        }
    }

    private void actualizarMenuBandeja(){
        if(trayIcon == null) return;

        PopupMenu menu = new PopupMenu();
        MenuItem abrir = new MenuItem("Abrir Dora");
        abrir.addActionListener(e -> restaurarDesdeBandeja());
        menu.add(abrir);
        menu.addSeparator();

        List<Server> activos = gestorServidores.getServidoresActivos();
        MenuItem contador = new MenuItem("Servidores activos: " + activos.size());
        contador.setEnabled(false);
        menu.add(contador);

        if(activos.isEmpty()){
            MenuItem ninguno = new MenuItem("Ningun servidor activo");
            ninguno.setEnabled(false);
            menu.add(ninguno);
        } else {
            List<TrayServerMenuRow> serverRows = construirFilasServidoresBandeja(activos);
            int ramColumnWidth = serverRows.stream()
                    .mapToInt(row -> row.ramText().length())
                    .max()
                    .orElse(0);
            for(TrayServerMenuRow row : serverRows){
                MenuItem item = new MenuItem("- " + textoServidorActivoBandeja(row, ramColumnWidth));
                aplicarFuenteMonoespaciadaBandeja(item);
                item.setEnabled(false);
                menu.add(item);
            }
        }

        menu.addSeparator();
        MenuItem salir = new MenuItem("Salir de Dora");
        salir.addActionListener(e -> solicitarSalidaDesdeBandeja());
        menu.add(salir);

        trayIcon.setPopupMenu(menu);
        trayIcon.setToolTip(BASE_TITLE + " - " + activos.size() + " servidor(es) activo(s)");
    }

    private List<TrayServerMenuRow> construirFilasServidoresBandeja(List<Server> activos){
        List<TrayServerMenuRow> rows = new ArrayList<>();
        if(activos == null || activos.isEmpty()) return rows;

        for(Server server : activos){
            rows.add(new TrayServerMenuRow(
                    ajustarNombreServidorBandeja(nombreServidorBandeja(server)),
                    textoRamServidorBandeja(server)
            ));
        }
        return rows;
    }

    private String textoServidorActivoBandeja(TrayServerMenuRow row, int ramColumnWidth){
        String serverName = rellenarDerecha(row.serverName(), TRAY_SERVER_NAME_COLUMN_WIDTH);
        String ramText = rellenarIzquierda(row.ramText(), ramColumnWidth);
        return serverName + "  RAM: " + ramText;
    }

    private String ajustarNombreServidorBandeja(String serverName){
        String safeName = serverName == null || serverName.isBlank() ? "Servidor sin nombre" : serverName.trim();
        if(safeName.length() <= TRAY_SERVER_NAME_COLUMN_WIDTH){
            return safeName;
        }
        int visibleChars = Math.max(0, TRAY_SERVER_NAME_COLUMN_WIDTH - 3);
        return safeName.substring(0, visibleChars) + "...";
    }

    private String rellenarDerecha(String text, int width){
        String safeText = text == null ? "" : text;
        if(safeText.length() >= width) return safeText;
        return safeText + " ".repeat(width - safeText.length());
    }

    private String rellenarIzquierda(String text, int width){
        String safeText = text == null ? "" : text;
        if(safeText.length() >= width) return safeText;
        return " ".repeat(width - safeText.length()) + safeText;
    }

    private void aplicarFuenteMonoespaciadaBandeja(MenuItem item){
        if(item == null) return;
        Font base = item.getFont();
        int style = base != null ? base.getStyle() : Font.PLAIN;
        int size = base != null ? base.getSize() : 12;
        item.setFont(new Font(Font.MONOSPACED, style, size));
    }

    private String textoRamServidorBandeja(Server server){
        ServerResourceSnapshot snapshot = PanelEstadisticas.getLiveResourceSnapshot(server);
        return snapshot.ramPercentRounded() + "% / " + formatearRamBandeja(snapshot.ramMaxMb());
    }

    private String formatearRamBandeja(long ramMb){
        if(ramMb <= 0L) return "-";
        if(ramMb < 1024L) return ramMb + " MB";

        double ramGb = ramMb / 1024d;
        if(Math.abs(ramGb - Math.rint(ramGb)) < 0.05d){
            return String.format(java.util.Locale.ROOT, "%.0f GB", ramGb);
        }
        return String.format(java.util.Locale.ROOT, "%.1f GB", ramGb);
    }

    private String nombreServidorBandeja(Server server){
        if(server == null) return "Servidor desconocido";
        String displayName = server.getDisplayName();
        if(displayName != null && !displayName.isBlank()) return displayName;
        String id = server.getId();
        if(id != null && !id.isBlank()) return id;
        return "Servidor sin nombre";
    }

    private record TrayServerMenuRow(String serverName, String ramText) {}

    private void ocultarEnBandeja(){
        actualizarMenuBandeja();
        setVisible(false);
        if(!avisoBandejaMostrado && trayIcon != null){
            avisoBandejaMostrado = true;
            trayIcon.displayMessage(
                    BASE_TITLE,
                    "Dora sigue ejecutandose en segúndo plano.",
                    TrayIcon.MessageType.INFO
            );
        }
    }

    private void restaurarDesdeBandeja(){
        SwingUtilities.invokeLater(this::restaurarVentanaPrincipal);
    }

    private void restaurarVentanaPrincipal(){
        if(!isDisplayable()) return;
        setVisible(true);
        setExtendedState(getExtendedState() & ~Frame.ICONIFIED);
        toFront();
        requestFocus();
    }

    private void solicitarSalidaDesdeBandeja(){
        SwingUtilities.invokeLater(this::solicitarSalidaAplicacion);
    }

    private void solicitarSalidaAplicacion(){
        if(!isVisible()){
            restaurarVentanaPrincipal();
        }
        if(!confirmarSalidaConfiguracion(null, null)) return;

        List<Server> activos = gestorServidores.getServidoresActivos();
        if(activos.isEmpty()){
            desinstalarBandejaSistema();
            this.dispose();
            System.exit(0);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Hay servidores activos.\n\n");
        for(Server s : activos){
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
        if(res != 0) return;

        SalidaServidoresActivos.detenerYSalir(this, gestorServidores, () -> {
            try {
                VentanaPrincipal.this.dispose();
            } finally {
                System.exit(0);
            }
        });
    }

    private void desinstalarBandejaSistema(){
        if(trayServerStateListener != null){
            gestorServidores.removePropertyChangeListener("estadoServidor", trayServerStateListener);
            gestorServidores.removePropertyChangeListener("listaServidores", trayServerStateListener);
            trayServerStateListener = null;
        }
        if(trayIcon != null){
            try{
                SystemTray.getSystemTray().remove(trayIcon);
            } catch (RuntimeException ignored){
            }
            trayIcon = null;
        }
        trayDisponible = false;
    }

    private PanelServidores getPanelServidores(GestorServidores gestorServidores) {
        PanelServidores listaServidoresPanel = new PanelServidores(gestorServidores);
        listaServidoresPanel.setServidorSeleccionadoListener(server -> aplicarCambioServidor(server, true));
        listaServidoresPanel.setServidorContextMenuListener(new PanelServidores.ServidorContextMenuListener() {
            @Override
            public void abrirConfiguracion(Server server) {
                abrirPaginaServidor(server, PaginaDerecha.CONFIG);
            }

            @Override
            public void abrirMundos(Server server) {
                abrirPaginaServidor(server, PaginaDerecha.MUNDO);
            }
        });
        return listaServidoresPanel;
    }

    private void abrirPaginaServidor(Server server, PaginaDerecha pagina){
        if(server == null || pagina == null) return;

        Server serverSeleccionado = gestorServidores.getServidorSeleccionado();
        boolean mismoServidor = esMismoServidor(serverSeleccionado, server);

        if(!mismoServidor){
            if(!aplicarCambioServidor(server, false)) return;
            listaServidoresPanel.mostrarSeleccionServidor(server);
        } else {
            if(!confirmarSalidaConfiguracion(server, pagina)) return;
            gestorServidores.setServidorSeleccionado(server);
            if(abrirCarpetaServerButton != null) abrirCarpetaServerButton.setEnabled(true);
            if(borrarServerButton != null) borrarServerButton.setEnabled(true);
            boolean panelDesincronizado = !esMismoServidor(serverMostrado, server);
            if(panelDerecho != null && panelDesincronizado) {
                renderizarPanelDerecho(server);
            }
        }

        setPaginaDerecha(pagina);
    }

    private void navegarAPaginaDerecha(PaginaDerecha pagina){
        if(pagina == null) pagina = PaginaDerecha.HOME;
        registrarClickInfoDebug(pagina);
        if(pagina == paginaDerechaActual){
            setPaginaDerecha(pagina);
            return;
        }
        if(!confirmarSalidaConfiguracion(gestorServidores.getServidorSeleccionado(), pagina)) return;
        setPaginaDerecha(pagina);
    }

    private void registrarClickInfoDebug(PaginaDerecha pagina) {
        if (pagina != PaginaDerecha.INFO) {
            infoClicksForDebug = 0;
            return;
        }

        infoClicksForDebug++;
        if (infoClicksForDebug < DEBUG_TOGGLE_INFO_CLICKS) return;

        infoClicksForDebug = 0;
        DebugMode.toggle();
    }

    private void actualizarTituloDebug() {
        setTitle(DebugMode.isEnabled() ? BASE_TITLE + " (debug)" : BASE_TITLE);
    }

    private boolean aplicarCambioServidor(Server server, boolean restaurarSeleccionSiCancela){
        if(server == null) return false;

        Server servidorActual = gestorServidores.getServidorSeleccionado();
        if(!confirmarSalidaConfiguracion(server, paginaDerechaActual)){
            if(restaurarSeleccionSiCancela && servidorActual != null){
                listaServidoresPanel.mostrarSeleccionServidor(servidorActual);
            }
            return false;
        }

        if(esMismoServidor(servidorActual, server)){
            return true;
        }

        gestorServidores.setServidorSeleccionado(server);
        System.out.println("Servidor seleccionado: " + server.getServerDir());
        if(abrirCarpetaServerButton != null) abrirCarpetaServerButton.setEnabled(true);
        if(borrarServerButton != null) borrarServerButton.setEnabled(true);
        if(panelDerecho != null) {
            renderizarPanelDerecho(server);
        }
        return true;
    }

    private boolean confirmarSalidaConfiguracion(Server serverDestino, PaginaDerecha paginaDestino){
        if(paginaDerechaActual != PaginaDerecha.CONFIG || panelConfigServidor == null){
            return true;
        }

        Server servidorActual = gestorServidores.getServidorSeleccionado();
        PaginaDerecha destino = paginaDestino != null ? paginaDestino : paginaDerechaActual;
        boolean mismoServidor = esMismoServidor(servidorActual, serverDestino);
        boolean permaneceEnConfiguracion = destino == PaginaDerecha.CONFIG && (serverDestino == null || mismoServidor);
        if(permaneceEnConfiguracion){
            return true;
        }

        return panelConfigServidor.confirmDiscardOrSave(this);
    }

    private boolean esMismoServidor(Server a, Server b){
        if(a == null || b == null) return false;

        String idA = a.getId();
        String idB = b.getId();
        if(idA != null && idB != null){
            return idA.equals(idB);
        }

        String dirA = a.getServerDir();
        String dirB = b.getServerDir();
        if(dirA == null || dirB == null) return false;
        return dirA.equals(dirB);
    }

    private void estilizarBoton(JButton button){
        Font base = button.getFont();
        button.setFont(base.deriveFont(Font.BOLD, Math.max(14f, base.getSize2D() + 2f)));
        button.setMargin(new Insets(10, 14, 10, 14));
        button.setFocusPainted(false);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setVerticalAlignment(SwingConstants.CENTER);
        button.setIconTextGap(0);
        button.setText(null);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(AppTheme.getBackground());
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if(!button.isEnabled()) return;
                button.setBackground(colorSeleccionPanelServidores());
                button.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(AppTheme.getBackground());
                button.repaint();
            }
        });
    }

    private void aplicarIconoBotonServidores(AbstractButton button, String iconPath){
        if(button == null || iconPath == null || iconPath.isBlank()) return;
        button.setIcon(SvgIconFactory.create(iconPath, 20, 20));
    }

    private void configurarHoverIconoCarpeta(AbstractButton button){
        if(button == null) return;
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if(!button.isEnabled()) return;
                button.setIcon(SvgIconFactory.create("doraicons/folder-open.svg", 20, 20));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setIcon(SvgIconFactory.create("doraicons/folder.svg", 20, 20));
            }
        });
    }
}





