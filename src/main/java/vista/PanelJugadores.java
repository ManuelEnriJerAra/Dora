package vista;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.extras.components.FlatScrollPane;

import controlador.GestorConfiguracion;
import controlador.GestorServidores;
import controlador.GestorUsuariosConocidos;
import controlador.MojangAPI;
import controlador.AppErrorReporter;
import controlador.Utilidades;
import modelo.Server;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SuppressWarnings("unused")
public class PanelJugadores extends JPanel {
    private static final Pattern JOIN = Pattern.compile("([^\\s]+) joined the game");
    private static final Pattern LEFT = Pattern.compile("([^\\s]+) left the game");
    private static final String CARD_JUGADORES = "jugadores";
    private static final String CARD_VACIO = "vacio";
    private static final DateTimeFormatter LIST_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
    private static final int MAX_FAKE_PLAYERS = 200;

    private final GestorServidores gestorServidores;
    private final ObjectMapper mapper = new ObjectMapper();
    private final MojangAPI mojangAPI = new MojangAPI();

    private final JPanel contenedorJugadores = new PlayerWrapPanel(new WrapLayout(FlowLayout.LEFT, 10, 8, 180));
    private final Map<String, PlayerPanel> panelsPorJugador = new LinkedHashMap<>();
    private final LinkedHashSet<String> jugadoresReales = new LinkedHashSet<>();
    private final LinkedHashSet<String> jugadoresDebug = new LinkedHashSet<>();
    private final JScrollPane scrollJugadores;
    private final JPanel panelCentro = new JPanel(new CardLayout());
    private final JPanel panelSinJugadores = new JPanel(new GridBagLayout());

    private final JButton btnWhitelist = new FlatButton();
    private final JButton btnBaneados = new FlatButton();
    private final JButton btnOps = new FlatButton();
    private final JButton btnDebugAddPlayer = new FlatButton();
    private final JButton btnDebugRemovePlayer = new FlatButton();
    private final PropertyChangeListener debugModeListener;
    private final Map<TipoLista, DefaultListModel<String>> modelosListasAbiertas = new EnumMap<>(TipoLista.class);
    private final Map<String, String> sugerenciasExactasRemotas = new ConcurrentHashMap<>();
    private final Set<String> sugerenciasExactasRemotasNoEncontradas = ConcurrentHashMap.newKeySet();
    private final Set<String> operadores = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Set<JLabel> whitelistOpsCountLabels = ConcurrentHashMap.newKeySet();

    private Server server;
    private Consumer<String> consoleListener;
    private final PropertyChangeListener listenerEstadoServidor;
    private int maxPlayers;
    private int fakePlayerSequence;
    private long contadorRefreshSequence;
    private WatchService listasWatchService;
    private Thread listasWatchThread;
    private JPanel headerActionsPanel;

    public PanelJugadores(GestorServidores gestorServidores) {
        this(gestorServidores, true);
    }

    public PanelJugadores(GestorServidores gestorServidores, boolean showTitle) {
        this.gestorServidores = gestorServidores;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(AppTheme.getPanelBackground());

        if (showTitle) {
            JLabel titulo = new CardTitleLabel("Jugadores");
            AppTheme.applyCardTitleStyle(titulo);
            add(titulo, BorderLayout.NORTH);
        }

        contenedorJugadores.setOpaque(true);
        contenedorJugadores.setBackground(AppTheme.getPanelBackground());

        scrollJugadores = new FlatScrollPane();
        scrollJugadores.setViewportView(contenedorJugadores);
        scrollJugadores.setBorder(null);
        scrollJugadores.setOpaque(true);
        scrollJugadores.setBackground(AppTheme.getPanelBackground());
        scrollJugadores.getViewport().setOpaque(true);
        scrollJugadores.getViewport().setBackground(AppTheme.getPanelBackground());
        scrollJugadores.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollJugadores.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        AppTheme.applyStandardScrollSpeed(scrollJugadores);

        JLabel labelSinJugadores = new JLabel("No hay ningún jugador conectado.");
        labelSinJugadores.setFont(labelSinJugadores.getFont().deriveFont(Font.PLAIN, 15f));
        labelSinJugadores.setForeground(AppTheme.getMutedForeground());
        panelSinJugadores.setOpaque(true);
        panelSinJugadores.setBackground(AppTheme.getPanelBackground());
        panelSinJugadores.add(labelSinJugadores);

        panelCentro.setOpaque(true);
        panelCentro.setBackground(AppTheme.getPanelBackground());
        panelCentro.add(scrollJugadores, CARD_JUGADORES);
        panelCentro.add(panelSinJugadores, CARD_VACIO);

        add(panelCentro, BorderLayout.CENTER);
        aplicarEstiloScrollJugadores();

        configurarBotonCabecera(btnWhitelist, "Whitelist", "doraicons/whitelist.svg", () -> abrirDialogoLista("Whitelist", TipoLista.WHITELIST));
        configurarBotonCabecera(btnBaneados, "Baneados", "doraicons/user-block.svg", this::abrirDialogoBaneados);
        configurarBotonCabecera(btnOps, "OPs", "doraicons/shield-user.svg", () -> abrirDialogoLista("OPs", TipoLista.OPS));
        configurarBotonDebug(btnDebugAddPlayer, "Añadir jugador falso", "doraicons/plus.svg", this::addFakePlayer);
        configurarBotonDebug(btnDebugRemovePlayer, "Eliminar jugador falso", "doraicons/minus.svg", this::removeFakePlayer);
        debugModeListener = DebugModeUiBinder.createUiListener(this::actualizarModoDebug);
        DebugMode.addPropertyChangeListener(debugModeListener);

        server = gestorServidores.getServidorSeleccionado();
        maxPlayers = leerMaxPlayers(server);
        if (server != null) {
            recomputarDesdeLogs(server.getRawLogLines());
            consoleListener = this::procesarLinea;
            server.addConsoleListener(consoleListener);
            iniciarWatcherListas(server);
        }

        listenerEstadoServidor = evt -> {
            if (!"estadoServidor".equals(evt.getPropertyName())) return;
            Object nuevoValor = evt.getNewValue();
            if (!(nuevoValor instanceof Server actualizado)) return;
            if (server == null || actualizado.getId() == null || server.getId() == null) return;
            if (!actualizado.getId().equals(server.getId())) return;

            boolean vivo = actualizado.getServerProcess() != null && actualizado.getServerProcess().isAlive();
            if (!vivo) {
                SwingUtilities.invokeLater(() -> setJugadores(Set.of()));
            }
        };
        gestorServidores.addPropertyChangeListener("estadoServidor", listenerEstadoServidor);

        recargarContadoresAsync();

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
            if (!isDisplayable()) {
                if (server != null && consoleListener != null) {
                    server.removeConsoleListener(consoleListener);
                }
                gestorServidores.removePropertyChangeListener("estadoServidor", listenerEstadoServidor);
                DebugMode.removePropertyChangeListener(debugModeListener);
                detenerWatcherListas();
            }
        });
    }

    public void configureHeaderActions(JPanel headerActionsPanel) {
        if (headerActionsPanel == null) return;
        this.headerActionsPanel = headerActionsPanel;
        actualizarAccionesCabecera();
    }

    public void refrescarPanel() {
        actualizarListasAutomaticamente();
        if (server != null) {
            recomputarDesdeLogs(server.getRawLogLines());
            recargarContadoresAsync();
        } else {
            setJugadores(Set.of());
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        aplicarEstiloScrollJugadores();
    }

    private void aplicarEstiloScrollJugadores() {
        if (scrollJugadores == null) return;
        Color panelBg = AppTheme.getPanelBackground();
        setBackground(panelBg);
        contenedorJugadores.setOpaque(true);
        contenedorJugadores.setBackground(panelBg);
        panelCentro.setOpaque(true);
        panelCentro.setBackground(panelBg);
        panelSinJugadores.setOpaque(true);
        panelSinJugadores.setBackground(panelBg);
        scrollJugadores.setBorder(null);
        scrollJugadores.setViewportBorder(null);
        scrollJugadores.setOpaque(true);
        scrollJugadores.setBackground(panelBg);
        if (scrollJugadores.getViewport() != null) {
            scrollJugadores.getViewport().setOpaque(true);
            scrollJugadores.getViewport().setBackground(panelBg);
        }
    }

    private enum TipoLista {
        WHITELIST("whitelist.json"),
        OPS("ops.json"),
        BANNED_PLAYERS("banned-players.json"),
        BANNED_IPS("banned-ips.json");

        final String filename;

        TipoLista(String filename) {
            this.filename = filename;
        }
    }

    private record CounterSnapshot(
            List<String> whitelist,
            List<String> bannedPlayers,
            List<String> bannedIps,
            List<String> ops
    ) {
        private CounterSnapshot {
            whitelist = whitelist == null ? List.of() : List.copyOf(whitelist);
            bannedPlayers = bannedPlayers == null ? List.of() : List.copyOf(bannedPlayers);
            bannedIps = bannedIps == null ? List.of() : List.copyOf(bannedIps);
            ops = ops == null ? List.of() : List.copyOf(ops);
        }

        static CounterSnapshot empty() {
            return new CounterSnapshot(List.of(), List.of(), List.of(), List.of());
        }
    }

    private void configurarBotonCabecera(JButton boton, String titulo, String iconPath, Runnable onClick) {
        AppTheme.configureHeaderIconButton(boton, "Abrir " + titulo, iconPath, 18, AppTheme::getForeground);
        boton.addActionListener(e -> onClick.run());
    }

    private void configurarBotonDebug(JButton boton, String tooltip, String iconPath, Runnable onClick) {
        AppTheme.configureDebugIconButton(boton, tooltip, iconPath, onClick);
    }

    private void actualizarAccionesCabecera() {
        DebugModeUiBinder.rebuildHeaderActions(
                headerActionsPanel,
                List.of(btnDebugAddPlayer, btnDebugRemovePlayer),
                List.of(btnWhitelist, btnBaneados, btnOps)
        );
    }

    private void actualizarModoDebug() {
        if (!DebugMode.isEnabled() && !jugadoresDebug.isEmpty()) {
            jugadoresDebug.clear();
            renderJugadores();
        }
        actualizarAccionesCabecera();
    }

    private void procesarLinea(String raw) {
        if (raw == null || raw.isBlank()) return;

        Matcher mJoin = JOIN.matcher(raw);
        if (mJoin.find()) {
            String name = mJoin.group(1);
            if (name != null && !name.isBlank()) {
                runOnEdtSafely(() -> addJugador(name.strip()), "No se ha podido registrar el jugador conectado.");
            }
            return;
        }

        Matcher mLeft = LEFT.matcher(raw);
        if (mLeft.find()) {
            String name = mLeft.group(1);
            if (name != null && !name.isBlank()) {
                runOnEdtSafely(() -> removeJugador(name.strip()), "No se ha podido quitar el jugador desconectado.");
            }
        }
    }

    private void runOnEdtSafely(Runnable task, String errorMessage) {
        if (task == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                AppErrorReporter.report(errorMessage, ex);
            }
        });
    }

    private void recomputarDesdeLogs(List<String> rawLogLines) {
        if (rawLogLines == null || rawLogLines.isEmpty()) {
            setJugadores(Set.of());
            return;
        }

        LinkedHashSet<String> actuales = new LinkedHashSet<>();
        for (String raw : rawLogLines) {
            if (raw == null) continue;

            Matcher mJoin = JOIN.matcher(raw);
            if (mJoin.find()) {
                String name = mJoin.group(1);
                if (name != null && !name.isBlank()) {
                    addToSetWithLimit(actuales, name.strip());
                }
                continue;
            }

            Matcher mLeft = LEFT.matcher(raw);
            if (mLeft.find()) {
                String name = mLeft.group(1);
                if (name != null && !name.isBlank()) {
                    removeIgnoreCase(actuales, name.strip());
                }
            }
        }

        setJugadores(actuales);
        GestorUsuariosConocidos.recordarUsuarios(actuales, "server-logs");
    }

    private void setJugadores(Set<String> nombres) {
        jugadoresReales.clear();
        if (nombres != null) {
            for (String nombre : nombres) {
                if (nombre == null || nombre.isBlank()) continue;
                addToSetWithLimit(jugadoresReales, nombre);
            }
        }
        renderJugadores();
    }

    private void renderJugadores() {
        LinkedHashSet<String> target = new LinkedHashSet<>(jugadoresReales);
        for (String nombre : jugadoresDebug) {
            if (nombre == null || nombre.isBlank()) continue;
            if (!containsIgnoreCase(target, nombre)) {
                target.add(nombre);
            }
        }

        Iterator<Map.Entry<String, PlayerPanel>> iterator = panelsPorJugador.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PlayerPanel> entry = iterator.next();
            if (containsIgnoreCase(target, entry.getKey())) continue;
            contenedorJugadores.remove(entry.getValue());
            iterator.remove();
        }

        for (String name : target) {
            if (name == null || name.isBlank()) continue;
            if (getPlayerPanelKeyIgnoreCase(name) != null) continue;
            try {
                PlayerPanel panel = new PlayerPanel(name);
                panelsPorJugador.put(name, panel);
                contenedorJugadores.add(panel);
            } catch (RuntimeException ex) {
                AppErrorReporter.report("No se ha podido crear la tarjeta del jugador '" + name + "'.", ex);
            }
        }

        actualizarIndicadoresOperador();
        refrescarUI();
    }

    private void addJugador(String nombre) {
        if (nombre == null || nombre.isBlank()) return;
        if (jugadoresReales.size() >= maxPlayers) return;
        if (containsIgnoreCase(jugadoresReales, nombre)) return;
        addToSetWithLimit(jugadoresReales, nombre);
        renderJugadores();
    }

    private void removeJugador(String nombre) {
        if (nombre == null || nombre.isBlank()) return;
        removeIgnoreCase(jugadoresReales, nombre);
        renderJugadores();
    }

    private void addFakePlayer() {
        if (jugadoresDebug.size() >= MAX_FAKE_PLAYERS) return;
        String name;
        do {
            fakePlayerSequence++;
            name = String.format(Locale.ROOT, "DebugPlayer%03d", fakePlayerSequence);
        } while (containsIgnoreCase(jugadoresReales, name) || containsIgnoreCase(jugadoresDebug, name));

        jugadoresDebug.add(name);
        renderJugadores();
    }

    private void removeFakePlayer() {
        if (jugadoresDebug.isEmpty()) return;
        String last = null;
        for (String name : jugadoresDebug) {
            last = name;
        }
        if (last != null) {
            jugadoresDebug.remove(last);
            renderJugadores();
        }
    }

    private void enviarComando(String comando) {
        Server servidorActual = server;
        if (servidorActual == null) return;
        gestorServidores.mandarComando(servidorActual, comando, false);
    }

    private void refrescarUI() {
        CardLayout cardLayout = (CardLayout) panelCentro.getLayout();
        cardLayout.show(panelCentro, panelsPorJugador.isEmpty() ? CARD_VACIO : CARD_JUGADORES);
        panelCentro.revalidate();
        panelCentro.repaint();
    }

    private void recargarContadores() {
        CounterSnapshot snapshot = cargarContadores(server);
        aplicarContadores(snapshot);
    }

    private void recargarContadoresAsync() {
        Server servidorSnapshot = server;
        Set<String> onlineSnapshot = new LinkedHashSet<>(panelsPorJugador.keySet());
        long requestId = ++contadorRefreshSequence;
        new SwingWorker<CounterSnapshot, Void>() {
            @Override
            protected CounterSnapshot doInBackground() {
                CounterSnapshot snapshot = cargarContadores(servidorSnapshot);
                recordarUsuariosConocidos(snapshot, onlineSnapshot);
                return snapshot;
            }

            @Override
            protected void done() {
                if (requestId != contadorRefreshSequence || servidorSnapshot != server) {
                    return;
                }
                try {
                    aplicarContadores(get());
                } catch (Exception ignored) {
                    aplicarContadores(CounterSnapshot.empty());
                }
            }
        }.execute();
    }

    private CounterSnapshot cargarContadores(Server servidorActual) {
        List<String> whitelist = cargarListaDesdeArchivo(servidorActual, TipoLista.WHITELIST);
        List<String> bannedPlayers = cargarListaDesdeArchivo(servidorActual, TipoLista.BANNED_PLAYERS);
        List<String> bannedIps = cargarListaDesdeArchivo(servidorActual, TipoLista.BANNED_IPS);
        List<String> ops = cargarListaDesdeArchivo(servidorActual, TipoLista.OPS);
        return new CounterSnapshot(whitelist, bannedPlayers, bannedIps, ops);
    }

    private void aplicarContadores(CounterSnapshot snapshot) {
        if (snapshot == null) {
            snapshot = CounterSnapshot.empty();
        }
        int whitelistCount = snapshot.whitelist().size();
        int bannedPlayersCount = snapshot.bannedPlayers().size();
        int bannedIpsCount = snapshot.bannedIps().size();
        List<String> ops = snapshot.ops();
        int opsCount = ops.size();
        operadores.clear();
        operadores.addAll(ops);

        btnWhitelist.setToolTipText("Abrir Whitelist (" + whitelistCount + ")");
        btnBaneados.setToolTipText("Abrir Baneados: IDs baneadas (" + bannedPlayersCount + "), IPs baneadas (" + bannedIpsCount + ")");
        btnOps.setToolTipText("Abrir OPs (" + opsCount + ")");
        actualizarLabelsOpsWhitelist(opsCount);
        actualizarIndicadoresOperador();
        revalidate();
        repaint();
    }

    private List<String> cargarListaDesdeArchivo(TipoLista tipo) {
        return cargarListaDesdeArchivo(server, tipo);
    }

    private List<String> cargarListaDesdeArchivo(Server servidorActual, TipoLista tipo) {
        if (servidorActual == null) return List.of();
        String dir = servidorActual.getServerDir();
        if (dir == null || dir.isBlank()) return List.of();

        Path file = Path.of(dir).resolve(tipo.filename);
        if (!Files.exists(file)) return List.of();

        try (InputStream in = Files.newInputStream(file)) {
            JsonNode root = mapper.readTree(in);
            if (root == null || !root.isArray()) return List.of();
            List<String> out = new ArrayList<>();
            for (JsonNode node : root) {
                if (node == null) continue;
                String value = tipo == TipoLista.BANNED_IPS ? node.path("ip").asString() : node.path("name").asString();
                if (value == null) continue;
                value = value.strip();
                if (value.isBlank()) continue;
                out.add(value);
            }
            out.sort(String.CASE_INSENSITIVE_ORDER);
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private void abrirDialogoLista(String titulo, TipoLista tipo) {
        DefaultListModel<String> model = crearModelo(tipo);
        JDialog dialog = crearDialogoLista(titulo, crearPanelLista(tipo, model));
        dialog.setSize(420, 420);
        dialog.setLocationRelativeTo(this);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                modelosListasAbiertas.remove(tipo, model);
            }
        });
        modelosListasAbiertas.put(tipo, model);
        dialog.setVisible(true);
        recargarContadores();
    }

    private void abrirDialogoBaneados() {
        DefaultListModel<String> modelPlayers = crearModelo(TipoLista.BANNED_PLAYERS);
        DefaultListModel<String> modelIps = crearModelo(TipoLista.BANNED_IPS);

        JPanel splitPanel = new JPanel(new GridLayout(1, 2, 24, 0));
        splitPanel.setOpaque(false);
        JPanel leftPanel = crearSeccionListaBaneados("IDs baneadas", crearPanelLista(TipoLista.BANNED_PLAYERS, modelPlayers));
        leftPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, AppTheme.getSubtleBorderColor()),
                BorderFactory.createEmptyBorder(0, 0, 0, 12)
        ));

        JPanel rightPanel = crearSeccionListaBaneados("IPs baneadas", crearPanelLista(TipoLista.BANNED_IPS, modelIps));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));

        splitPanel.add(leftPanel);
        splitPanel.add(rightPanel);

        JDialog dialog = crearDialogoLista("Baneados", splitPanel);
        dialog.setSize(560, 460);
        dialog.setLocationRelativeTo(this);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                modelosListasAbiertas.remove(TipoLista.BANNED_PLAYERS, modelPlayers);
                modelosListasAbiertas.remove(TipoLista.BANNED_IPS, modelIps);
            }
        });
        modelosListasAbiertas.put(TipoLista.BANNED_PLAYERS, modelPlayers);
        modelosListasAbiertas.put(TipoLista.BANNED_IPS, modelIps);
        dialog.setVisible(true);
        recargarContadores();
    }

    private JPanel crearSeccionListaBaneados(String titulo, JComponent contenido) {
        JLabel titleLabel = new CardTitleLabel(titulo);
        AppTheme.applyCardTitleStyle(titleLabel);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(contenido, BorderLayout.CENTER);
        return panel;
    }

    private DefaultListModel<String> crearModelo(TipoLista tipo) {
        DefaultListModel<String> model = new DefaultListModel<>();
        recargarModel(tipo, model);
        return model;
    }

    private JPanel crearPanelLista(TipoLista tipo, DefaultListModel<String> model) {
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setOpaque(false);
        list.setBackground(AppTheme.getTransparentColor());
        boolean admiteModoCompacto = tipo != TipoLista.BANNED_IPS;
        PlayerIdentityView.SizePreset initialPreset = admiteModoCompacto
                ? (GestorConfiguracion.isJugadoresListaCompacta()
                        ? PlayerIdentityView.SizePreset.COMPACT
                        : PlayerIdentityView.SizePreset.REGULAR)
                : null;
        AtomicReference<String> selectedPlayerRef = new AtomicReference<>();
        final PlayerRowsController[] playerRowsControllerRef = {null};
        if (initialPreset != null) {
            playerRowsControllerRef[0] = crearControladorFilasJugadores(tipo, model, initialPreset, selectedPlayerRef);
        }

        FlatScrollPane scroll = new FlatScrollPane();
        scroll.setViewportView(playerRowsControllerRef[0] != null ? playerRowsControllerRef[0].content : list);
        scroll.setBorder(null);
        scroll.setViewportBorder(null);
        scroll.setOpaque(false);
        if (scroll.getViewport() != null) {
            scroll.getViewport().setOpaque(true);
            scroll.getViewport().setBackground(AppTheme.getPanelBackground());
        }
        AppTheme.applyStandardScrollSpeed(scroll);

        JButton btnAdd = new FlatButton();
        btnAdd.setText("Añadir");
        JButton btnRemove = new FlatButton();
        btnRemove.setText("Quitar");

        btnAdd.addActionListener(e -> onAdd(tipo, model));
        btnRemove.addActionListener(e -> {
            if (playerRowsControllerRef[0] != null) {
                onRemove(tipo, selectedPlayerRef.get(), model);
            } else {
                onRemove(tipo, list, model);
            }
        });

        boolean listaEditable = tieneServidorConDirectorio();
        btnAdd.setEnabled(listaEditable);
        btnRemove.setEnabled(listaEditable);

        JButton compactToggleButton = null;
        if (admiteModoCompacto) {
            compactToggleButton = new FlatButton();
            configureListIconButton(compactToggleButton, "doraicons/minimize.svg", "Vista normal");
            JButton finalCompactToggleButton = compactToggleButton;
            compactToggleButton.addActionListener(e -> {
                PlayerIdentityView.SizePreset currentPreset = playerRowsControllerRef[0] == null
                        ? PlayerIdentityView.SizePreset.REGULAR
                        : playerRowsControllerRef[0].sizePreset;
                PlayerIdentityView.SizePreset nextPreset = currentPreset == PlayerIdentityView.SizePreset.COMPACT
                        ? PlayerIdentityView.SizePreset.REGULAR
                        : PlayerIdentityView.SizePreset.COMPACT;
                if (playerRowsControllerRef[0] != null) {
                    playerRowsControllerRef[0].setSizePreset(nextPreset);
                }
                GestorConfiguracion.guardarJugadoresListaCompacta(nextPreset == PlayerIdentityView.SizePreset.COMPACT);
                actualizarIconoModoCompacto(finalCompactToggleButton, nextPreset);
            });
            actualizarIconoModoCompacto(compactToggleButton, initialPreset);
        }

        configureListIconButton(btnAdd, "doraicons/plus.svg", "Añadir");
        configureListIconButton(btnRemove, "doraicons/minus.svg", "Quitar");

        JLabel contadorLabel = new JLabel(formatearContadorLista(tipo, model.getSize()));
        contadorLabel.setForeground(AppTheme.getMutedForeground());
        model.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                contadorLabel.setText(formatearContadorLista(tipo, model.getSize()));
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                contadorLabel.setText(formatearContadorLista(tipo, model.getSize()));
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                contadorLabel.setText(formatearContadorLista(tipo, model.getSize()));
            }
        });

        JPanel contadorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        contadorPanel.setOpaque(false);
        contadorPanel.add(contadorLabel);
        if (tipo == TipoLista.WHITELIST) {
            JLabel opsLabel = new JLabel(formatearContadorOpsWhitelist(cargarListaDesdeArchivo(TipoLista.OPS).size()));
            opsLabel.setForeground(AppTheme.getMutedForeground());
            whitelistOpsCountLabels.add(opsLabel);
            opsLabel.addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && !opsLabel.isDisplayable()) {
                    whitelistOpsCountLabels.remove(opsLabel);
                }
            });
            contadorPanel.add(opsLabel);
        }

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.setOpaque(false);
        botones.add(btnAdd);
        botones.add(btnRemove);
        if (admiteModoCompacto) {
            botones.add(compactToggleButton);
        }

        JPanel accionesCabecera = new JPanel(new BorderLayout(8, 0));
        accionesCabecera.setOpaque(false);
        accionesCabecera.add(contadorPanel, BorderLayout.WEST);
        accionesCabecera.add(botones, BorderLayout.EAST);

        JPanel cabecera = new JPanel(new BorderLayout(0, 4));
        cabecera.setOpaque(false);
        cabecera.add(accionesCabecera, BorderLayout.NORTH);
        JSeparator separador = new JSeparator();
        separador.setForeground(AppTheme.getSubtleBorderColor());
        separador.setBackground(AppTheme.getSubtleBorderColor());
        cabecera.add(separador, BorderLayout.SOUTH);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.add(cabecera, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        if (tipo == TipoLista.WHITELIST) {
            panel.add(crearFooterWhitelist(), BorderLayout.SOUTH);
        }

        return panel;
    }

    private JComponent crearFooterWhitelist() {
        JCheckBox whitelistCheckBox = new JCheckBox("Activar whitelist");
        whitelistCheckBox.setOpaque(false);
        whitelistCheckBox.setSelected(isWhitelistHabilitada());

        JLabel estadoLabel = new JLabel();
        actualizarEstadoWhitelistLabel(estadoLabel, whitelistCheckBox.isSelected());

        whitelistCheckBox.addActionListener(e -> {
            boolean enabled = whitelistCheckBox.isSelected();
            if (!guardarWhitelistHabilitada(enabled)) {
                whitelistCheckBox.setSelected(!enabled);
                actualizarEstadoWhitelistLabel(estadoLabel, whitelistCheckBox.isSelected());
                return;
            }
            actualizarEstadoWhitelistLabel(estadoLabel, enabled);
        });

        JPanel footer = new JPanel(new BorderLayout(8, 6));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        footer.add(estadoLabel, BorderLayout.CENTER);
        footer.add(whitelistCheckBox, BorderLayout.EAST);
        return footer;
    }

    private void actualizarEstadoWhitelistLabel(JLabel label, boolean enabled) {
        if (label == null) return;
        if (enabled) {
            label.setText("La whitelist está activada.");
            label.setFont(label.getFont().deriveFont(Font.PLAIN));
            label.setForeground(AppTheme.getMutedForeground());
            return;
        }

        label.setText("<html><b>La whitelist está desactivada.</b></html>");
        label.setFont(label.getFont().deriveFont(Font.PLAIN));
        label.setForeground(AppTheme.getForeground());
    }

    private boolean isWhitelistHabilitada() {
        try {
            Path propertiesPath = resolverServerPropertiesPath();
            if (propertiesPath == null || !Files.exists(propertiesPath)) return false;
            Properties props = Utilidades.cargarPropertiesUtf8(propertiesPath);
            return Boolean.parseBoolean(String.valueOf(props.getProperty("white-list", "false")));
        } catch (IOException e) {
            return false;
        }
    }

    private boolean guardarWhitelistHabilitada(boolean enabled) {
        try {
            Path propertiesPath = resolverServerPropertiesPath();
            if (propertiesPath == null) {
                mostrarErrorLista("No se ha podido localizar server.properties.");
                return false;
            }

            Properties props = Files.exists(propertiesPath)
                    ? Utilidades.cargarPropertiesUtf8(propertiesPath)
                    : new Properties();
            props.setProperty("white-list", Boolean.toString(enabled));
            Utilidades.guardarPropertiesUtf8(propertiesPath, props, "Edited by Dora");

            if (isServerActivo()) {
                enviarComando(enabled ? "whitelist on" : "whitelist off");
            }
            return true;
        } catch (IOException e) {
            mostrarErrorLista("No se ha podido guardar el estado de la whitelist: " + e.getMessage());
            return false;
        }
    }

    private Path resolverServerPropertiesPath() {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return null;
        }
        return Path.of(server.getServerDir()).resolve("server.properties");
    }

    private void configurarVistaListaJugadores(JList<String> list, PlayerIdentityView.SizePreset sizePreset) {
        if (list == null) return;
        PlayerIdentityView.SizePreset resolvedPreset = sizePreset == null ? PlayerIdentityView.SizePreset.REGULAR : sizePreset;
        list.putClientProperty("playerListSizePreset", resolvedPreset);
        list.setCellRenderer(new PlayerListCellRenderer(resolvedPreset));
        instalarHoverListaJugadores(list);
        int rowSpacing = resolvedPreset == PlayerIdentityView.SizePreset.COMPACT ? 0 : 8;
        int fixedHeight = switch (resolvedPreset) {
            case COMPACT -> resolvedPreset.avatarSize;
            case LARGE, REGULAR -> resolvedPreset.avatarSize + rowSpacing;
        };
        list.setFixedCellHeight(fixedHeight);
        precargarCabezasLista(list, resolvedPreset);
        list.revalidate();
        list.repaint();
    }

    private PlayerRowsController crearControladorFilasJugadores(
            TipoLista tipo,
            DefaultListModel<String> model,
            PlayerIdentityView.SizePreset sizePreset,
            AtomicReference<String> selectedValueRef
    ) {
        PlayerRowsController controller = new PlayerRowsController(tipo, model, sizePreset, selectedValueRef);
        model.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                controller.rebuild();
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                controller.rebuild();
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                controller.rebuild();
            }
        });
        controller.rebuild();
        return controller;
    }

    private void instalarHoverListaJugadores(JList<String> list) {
        if (list == null) return;
        Object installed = list.getClientProperty("playerListHoverInstalled");
        if (Boolean.TRUE.equals(installed)) {
            return;
        }

        MouseAdapter hoverAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                actualizarHoverLista(list, e.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                actualizarHoverLista(list, e.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                actualizarIndiceHoverLista(list, -1);
                list.setCursor(Cursor.getDefaultCursor());
            }
        };
        list.addMouseMotionListener(hoverAdapter);
        list.addMouseListener(hoverAdapter);
        list.putClientProperty("playerListHoverInstalled", true);
    }

    private void actualizarHoverLista(JList<String> list, java.awt.Point point) {
        if (list == null || point == null) return;
        int index = list.locationToIndex(point);
        if (index < 0) {
            actualizarIndiceHoverLista(list, -1);
            return;
        }
        java.awt.Rectangle bounds = list.getCellBounds(index, index);
        if (bounds == null || !bounds.contains(point)) {
            actualizarIndiceHoverLista(list, -1);
            return;
        }
        actualizarIndiceHoverLista(list, index);
    }

    private void actualizarIndiceHoverLista(JList<String> list, int hoverIndex) {
        if (list == null) return;
        Object previous = list.getClientProperty("playerListHoverIndex");
        int previousIndex = previous instanceof Number number ? number.intValue() : -1;
        if (previousIndex == hoverIndex) {
            return;
        }
        list.putClientProperty("playerListHoverIndex", hoverIndex);
        list.repaint();
    }

    private void precargarCabezasLista(JList<String> list, PlayerIdentityView.SizePreset sizePreset) {
        if (list == null || sizePreset == null) return;
        Object sequenceValue = list.getClientProperty("playerHeadReloadSequence");
        long nextSequence = sequenceValue instanceof Number number ? number.longValue() + 1L : 1L;
        list.putClientProperty("playerHeadReloadSequence", nextSequence);

        ListModel<String> model = list.getModel();
        List<String> usernames = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            String value = model.getElementAt(i);
            if (value != null && !value.isBlank()) {
                usernames.add(value);
            }
        }

        PlayerIdentityView.preloadHeads(usernames, sizePreset.avatarSize, () -> {
            Object currentSequence = list.getClientProperty("playerHeadReloadSequence");
            if (!(currentSequence instanceof Number number) || number.longValue() != nextSequence) {
                return;
            }
            list.revalidate();
            list.repaint();
        });
    }

    private void configureListIconButton(JButton button, String iconPath, String tooltip) {
        if (button == null) return;
        AppTheme.configureHeaderIconButton(button, tooltip, iconPath, 18, AppTheme::getForeground);
    }

    private String formatearContadorLista(TipoLista tipo, int count) {
        if (count <= 0) {
            return switch (tipo) {
                case WHITELIST -> "No hay ningún jugador en la whitelist.";
                case OPS -> "No hay ningún operador.";
                case BANNED_PLAYERS -> "No hay ningún jugador baneado.";
                case BANNED_IPS -> "No hay ninguna IP baneada.";
            };
        }

        return switch (tipo) {
            case WHITELIST -> count == 1 ? "1 jugador en la whitelist" : count + " jugadores en la whitelist";
            case OPS -> count == 1 ? "1 operador" : count + " operadores";
            case BANNED_PLAYERS -> count == 1 ? "1 jugador baneado" : count + " jugadores baneados";
            case BANNED_IPS -> count == 1 ? "1 IP baneada" : count + " IPs baneadas";
        };
    }

    private String formatearContadorOpsWhitelist(int count) {
        return "OPs: " + Math.max(0, count);
    }

    private void actualizarLabelsOpsWhitelist(int opsCount) {
        String text = formatearContadorOpsWhitelist(opsCount);
        whitelistOpsCountLabels.removeIf(label -> label == null || !label.isDisplayable());
        for (JLabel label : whitelistOpsCountLabels) {
            label.setText(text);
        }
    }

    private void actualizarIconoModoCompacto(JButton button, PlayerIdentityView.SizePreset sizePreset) {
        if (button == null) return;
        boolean compactActive = sizePreset == PlayerIdentityView.SizePreset.COMPACT;
        SvgIconFactory.apply(
                button,
                compactActive ? "doraicons/maximize.svg" : "doraicons/minimize.svg",
                18,
                18,
                AppTheme::getForeground
        );
        button.setToolTipText(compactActive ? "Vista compacta activa" : "Vista normal activa");
    }

    private JDialog crearDialogoLista(String titulo, JComponent contenido) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(contenido, BorderLayout.CENTER);

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), titulo, true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(panel);
        return dialog;
    }

    private void recargarModel(TipoLista tipo, DefaultListModel<String> model) {
        List<String> data = cargarListaDesdeArchivo(tipo);
        model.clear();
        for (String value : data) {
            model.addElement(value);
        }
        recargarContadores();
    }

    private void onAdd(TipoLista tipo, DefaultListModel<String> model) {
        String input = tipo == TipoLista.BANNED_IPS
                ? JOptionPane.showInputDialog(
                        SwingUtilities.getWindowAncestor(this),
                        "IP:",
                        "Añadir a " + tipo.name(),
                        JOptionPane.QUESTION_MESSAGE
                )
                : mostrarDialogoAnadirJugador(tipo);
        if (input == null) return;
        input = input.strip();
        if (input.isBlank()) return;

        String cmd = comandoAdd(tipo, input);
        if (cmd == null) return;
        if (isServerActivo()) {
            enviarComando(cmd);
            recordarUsuarioConocidoSiProcede(tipo, input, "manual-add");

            new Timer(500, (ActionEvent e) -> {
                ((Timer) e.getSource()).stop();
                actualizarListasAutomaticamente();
            }).start();
            return;
        }

        if (!modificarListaOffline(tipo, input, true)) return;
        recordarUsuarioConocidoSiProcede(tipo, input, "manual-add");
        recargarModel(tipo, model);
    }

    private String mostrarDialogoAnadirJugador(TipoLista tipo) {
        sincronizarUsuariosConocidosServidorActual();

        JTextField inputField = new JTextField(24);
        JLabel checkingLabel = new JLabel();
        SvgIconFactory.RotatingIcon checkingIcon = SvgIconFactory.createRotating(
                "doraicons/refresh.svg",
                16,
                16,
                AppTheme::getMutedForeground
        );
        checkingLabel.setIcon(checkingIcon);
        checkingLabel.setVisible(false);

        PlayerIdentityView knownSuggestionView = new PlayerIdentityView("", PlayerIdentityView.SizePreset.REGULAR);
        knownSuggestionView.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        knownSuggestionView.setToolTipText("Añadir jugador conocido");
        knownSuggestionView.setHighlighted(false);

        PlayerIdentityView mojangSuggestionView = new PlayerIdentityView("", PlayerIdentityView.SizePreset.REGULAR);
        mojangSuggestionView.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        mojangSuggestionView.setToolTipText("Añadir nombre exacto de Mojang");
        mojangSuggestionView.setHighlighted(false);

        JLabel knownEmptyLabel = new JLabel("Escribe para ver jugadores conocidos.");
        knownEmptyLabel.setForeground(AppTheme.getMutedForeground());

        JLabel mojangEmptyLabel = new JLabel("Escribe un nombre para comprobar Mojang.");
        mojangEmptyLabel.setForeground(AppTheme.getMutedForeground());

        JPanel knownCards = new JPanel(new CardLayout());
        knownCards.setOpaque(false);
        knownCards.add(knownEmptyLabel, "empty");
        knownCards.add(knownSuggestionView, "suggestion");

        JPanel mojangCards = new JPanel(new CardLayout());
        mojangCards.setOpaque(false);
        mojangCards.add(mojangEmptyLabel, "empty");
        mojangCards.add(mojangSuggestionView, "suggestion");

        JPanel suggestionPanel = new JPanel(new GridLayout(2, 1, 0, 8));
        suggestionPanel.setOpaque(false);
        suggestionPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        suggestionPanel.add(crearSeccionSugerencia("Conocidos", knownCards));
        suggestionPanel.add(crearSeccionSugerencia("Nombre exacto en Mojang", mojangCards));

        final String[] selectedKnownSuggestion = {null};
        final String[] selectedMojangSuggestion = {null};
        final String[] result = {null};
        final int[] suggestionRequestVersion = {0};
        final JDialog[] dialogRef = {null};
        Runnable applyKnownSuggestion = () -> {
            if (selectedKnownSuggestion[0] == null || selectedKnownSuggestion[0].isBlank()) return;
            result[0] = selectedKnownSuggestion[0];
            JDialog dialog = dialogRef[0];
            if (dialog != null) {
                dialog.dispose();
            }
        };
        Runnable applyMojangSuggestion = () -> {
            if (selectedMojangSuggestion[0] == null || selectedMojangSuggestion[0].isBlank()) return;
            result[0] = selectedMojangSuggestion[0];
            JDialog dialog = dialogRef[0];
            if (dialog != null) {
                dialog.dispose();
            }
        };
        knownSuggestionView.addPrimaryMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                applyKnownSuggestion.run();
            }
        });
        mojangSuggestionView.addPrimaryMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                applyMojangSuggestion.run();
            }
        });

        Timer checkingSpinTimer = new Timer(16, e -> {
            double currentAngle = ((Number) checkingLabel.getClientProperty("suggestionCheckAngle")).doubleValue();
            currentAngle -= Math.toRadians(8);
            checkingLabel.putClientProperty("suggestionCheckAngle", currentAngle);
            checkingIcon.setAngleRadians(currentAngle);
            checkingLabel.repaint();
        });
        checkingLabel.putClientProperty("suggestionCheckAngle", 0d);

        Timer debounceTimer = new Timer(500, e -> {
            String query = inputField.getText() == null ? "" : inputField.getText().strip();
            int requestVersion = ++suggestionRequestVersion[0];
            CardLayout knownCardsLayout = (CardLayout) knownCards.getLayout();
            CardLayout mojangCardsLayout = (CardLayout) mojangCards.getLayout();

            if (query.isBlank()) {
                selectedKnownSuggestion[0] = null;
                selectedMojangSuggestion[0] = null;
                knownEmptyLabel.setText("Escribe para ver jugadores conocidos.");
                mojangEmptyLabel.setText("Escribe un nombre para comprobar Mojang.");
                knownCardsLayout.show(knownCards, "empty");
                mojangCardsLayout.show(mojangCards, "empty");
                checkingSpinTimer.stop();
                checkingLabel.setVisible(false);
                suggestionPanel.revalidate();
                suggestionPanel.repaint();
                return;
            }

            String localSuggestion = buscarSugerenciaConocidaJugador(query, tipo);
            selectedKnownSuggestion[0] = localSuggestion;
            if (localSuggestion == null) {
                knownEmptyLabel.setText("No hay jugadores conocidos parecidos.");
                knownCardsLayout.show(knownCards, "empty");
            } else {
                knownSuggestionView.setPlayerName(localSuggestion);
                knownSuggestionView.setHighlighted(false);
                knownCardsLayout.show(knownCards, "suggestion");
            }

            if (query.length() < 3) {
                selectedMojangSuggestion[0] = null;
                mojangEmptyLabel.setText("Escribe al menos 3 caracteres.");
                mojangCardsLayout.show(mojangCards, "empty");
                checkingSpinTimer.stop();
                checkingLabel.setVisible(false);
                suggestionPanel.revalidate();
                suggestionPanel.repaint();
                return;
            }

            mojangEmptyLabel.setText("Comprobando nombre exacto en Mojang...");
            mojangCardsLayout.show(mojangCards, "empty");
            suggestionPanel.revalidate();
            suggestionPanel.repaint();

            MojangAPI.runBackgroundRequest(() -> {
                MojangAPI.PlayerProfile remoteProfile = buscarSugerenciaExactaRemotaJugador(query, tipo);
                SwingUtilities.invokeLater(() -> {
                    if (requestVersion != suggestionRequestVersion[0]) return;
                    JDialog dialog = dialogRef[0];
                    if (dialog != null && !dialog.isDisplayable()) return;

                    String currentQuery = inputField.getText() == null ? "" : inputField.getText().strip();
                    if (!normalizeComparableText(currentQuery).equals(normalizeComparableText(query))) {
                        return;
                    }

                    if (remoteProfile == null) {
                        selectedMojangSuggestion[0] = null;
                        mojangEmptyLabel.setText("No existe un nombre exacto en Mojang.");
                        mojangCardsLayout.show(mojangCards, "empty");
                    } else {
                        selectedMojangSuggestion[0] = remoteProfile.getName();
                        mojangSuggestionView.setPlayerName(remoteProfile.getName());
                        mojangSuggestionView.setHighlighted(false);
                        mojangCardsLayout.show(mojangCards, "suggestion");
                    }
                    checkingSpinTimer.stop();
                    checkingLabel.setVisible(false);
                    suggestionPanel.revalidate();
                    suggestionPanel.repaint();
                });
            });
        });
        debounceTimer.setRepeats(false);

        inputField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                schedule();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                schedule();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                schedule();
            }

            private void schedule() {
                suggestionRequestVersion[0]++;
                selectedKnownSuggestion[0] = null;
                selectedMojangSuggestion[0] = null;
                checkingLabel.putClientProperty("suggestionCheckAngle", 0d);
                checkingIcon.setAngleRadians(0d);
                checkingLabel.setVisible(true);
                knownEmptyLabel.setText("Buscando jugadores conocidos...");
                mojangEmptyLabel.setText("Preparando comprobación exacta...");
                ((CardLayout) knownCards.getLayout()).show(knownCards, "empty");
                ((CardLayout) mojangCards.getLayout()).show(mojangCards, "empty");
                if (!checkingSpinTimer.isRunning()) {
                    checkingSpinTimer.start();
                }
                debounceTimer.restart();
            }
        });

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(checkingLabel, BorderLayout.EAST);
        panel.add(inputRow, BorderLayout.NORTH);
        panel.add(suggestionPanel, BorderLayout.CENTER);

        JButton okButton = new FlatButton();
        okButton.setText("Añadir");
        JButton cancelButton = new FlatButton();
        cancelButton.setText("Cancelar");

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Añadir a " + tipo.name(), true);
        dialogRef[0] = dialog;
        okButton.addActionListener(e -> {
            String text = inputField.getText() == null ? "" : inputField.getText().strip();
            if (text.isBlank() && selectedMojangSuggestion[0] != null) {
                text = selectedMojangSuggestion[0];
            } else if (text.isBlank() && selectedKnownSuggestion[0] != null) {
                text = selectedKnownSuggestion[0];
            } else if (selectedMojangSuggestion[0] != null
                    && normalizeComparableText(text).equals(normalizeComparableText(selectedMojangSuggestion[0]))) {
                text = selectedMojangSuggestion[0];
            } else if (selectedKnownSuggestion[0] != null
                    && !normalizeComparableText(text).equals(normalizeComparableText(selectedKnownSuggestion[0]))
                    && normalizeComparableText(selectedKnownSuggestion[0]).startsWith(normalizeComparableText(text))) {
                text = selectedKnownSuggestion[0];
            }
            if (text.isBlank()) return;
            result[0] = text;
            dialog.dispose();
        });
        cancelButton.addActionListener(e -> dialog.dispose());
        inputField.addActionListener(e -> okButton.doClick());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancelButton);
        buttons.add(okButton);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(panel, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(okButton);
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 360), dialog.getHeight());
        dialog.setLocationRelativeTo(this);
        SwingUtilities.invokeLater(inputField::requestFocusInWindow);
        dialog.setVisible(true);
        suggestionRequestVersion[0]++;
        debounceTimer.stop();
        checkingSpinTimer.stop();
        return result[0];
    }

    private JPanel crearSeccionSugerencia(String title, JPanel content) {
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(AppTheme.getMutedForeground());

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(320, 54));
        panel.setMinimumSize(new Dimension(320, 54));
        return panel;
    }

    private String buscarSugerenciaConocidaJugador(String query, TipoLista tipo) {
        if (query == null || query.isBlank() || tipo == TipoLista.BANNED_IPS) {
            return null;
        }
        TreeSet<String> candidates = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        candidates.addAll(GestorUsuariosConocidos.buscarSugerencias(query, 8));
        candidates.addAll(PlayerIdentityView.getCachedUsernames());
        candidates.addAll(cargarNombresDesdeUsercache());
        candidates.addAll(cargarListaDesdeArchivo(tipo));

        String normalizedQuery = normalizeComparableText(query);
        String prefixMatch = null;
        String containsMatch = null;
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            String normalizedCandidate = normalizeComparableText(candidate);
            if (normalizedCandidate.equals(normalizedQuery)) {
                return candidate;
            }
            if (prefixMatch == null && normalizedCandidate.startsWith(normalizedQuery)) {
                prefixMatch = candidate;
            } else if (containsMatch == null && normalizedCandidate.contains(normalizedQuery)) {
                containsMatch = candidate;
            }
        }
        return prefixMatch != null ? prefixMatch : containsMatch;
    }

    private MojangAPI.PlayerProfile buscarSugerenciaExactaRemotaJugador(String query, TipoLista tipo) {
        if (query == null || query.isBlank() || tipo == TipoLista.BANNED_IPS) {
            return null;
        }

        String normalizedQuery = normalizeComparableText(query);
        String cachedSuggestion = sugerenciasExactasRemotas.get(normalizedQuery);
        if (cachedSuggestion != null && !cachedSuggestion.isBlank()) {
            return new MojangAPI.PlayerProfile(cachedSuggestion, null, null);
        }
        if (sugerenciasExactasRemotasNoEncontradas.contains(normalizedQuery)) {
            return null;
        }

        MojangAPI.PlayerProfile exactProfile = mojangAPI.obtenerPerfilJugadorExacto(query);
        if (exactProfile == null || exactProfile.getName() == null || exactProfile.getName().isBlank()) {
            sugerenciasExactasRemotasNoEncontradas.add(normalizedQuery);
            return null;
        }

        String normalizedExactName = exactProfile.getName().strip();
        sugerenciasExactasRemotas.put(normalizedQuery, normalizedExactName);
        return exactProfile;
    }

    private List<String> cargarNombresDesdeUsercache() {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return List.of();
        }
        Path usercachePath = Path.of(server.getServerDir()).resolve("usercache.json");
        if (!Files.isRegularFile(usercachePath)) {
            return List.of();
        }
        try {
            JsonNode root = mapper.readTree(usercachePath.toFile());
            if (root == null || !root.isArray()) {
                return List.of();
            }
            TreeSet<String> usernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (JsonNode node : root) {
                if (node == null) continue;
                String name = node.path("name").asString(null);
                if (name == null || name.isBlank()) continue;
                usernames.add(name.strip());
            }
            return new ArrayList<>(usernames);
        } catch (Exception e) {
            return List.of();
        }
    }

    private void sincronizarUsuariosConocidosServidorActual() {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return;
        }
        CounterSnapshot snapshot = cargarContadores(server);
        recordarUsuariosConocidos(snapshot, new LinkedHashSet<>(panelsPorJugador.keySet()));
    }

    private void recordarUsuariosConocidos(CounterSnapshot snapshot, Set<String> onlinePlayers) {
        if (snapshot == null) {
            return;
        }
        GestorUsuariosConocidos.recordarUsuarios(snapshot.whitelist(), "whitelist");
        GestorUsuariosConocidos.recordarUsuarios(snapshot.ops(), "ops");
        GestorUsuariosConocidos.recordarUsuarios(snapshot.bannedPlayers(), "banned-players");
        GestorUsuariosConocidos.recordarUsuarios(onlinePlayers == null ? Set.of() : onlinePlayers, "online-players");
    }

    private void recordarUsuarioConocidoSiProcede(TipoLista tipo, String username, String source) {
        if (tipo == null || username == null || username.isBlank()) return;
        if (tipo == TipoLista.BANNED_IPS) return;
        GestorUsuariosConocidos.recordarUsuario(username, null, null, source);
    }

    private void onRemove(TipoLista tipo, JList<String> list, DefaultListModel<String> model) {
        String selected = list.getSelectedValue();
        onRemove(tipo, selected, model);
    }

    private void onRemove(TipoLista tipo, String selected, DefaultListModel<String> model) {
        if (selected == null || selected.isBlank()) return;

        String cmd = comandoRemove(tipo, selected);
        if (cmd == null) return;
        if (isServerActivo()) {
            enviarComando(cmd);

            new Timer(500, (ActionEvent e) -> {
                ((Timer) e.getSource()).stop();
                actualizarListasAutomaticamente();
            }).start();
            return;
        }

        if (!modificarListaOffline(tipo, selected, false)) return;
        recargarModel(tipo, model);
    }

    private void actualizarListasAutomaticamente() {
        recargarContadores();
        sincronizarUsuariosConocidosServidorActual();
        for (Map.Entry<TipoLista, DefaultListModel<String>> entry : modelosListasAbiertas.entrySet()) {
            recargarModel(entry.getKey(), entry.getValue());
        }
    }

    private void actualizarIndicadoresOperador() {
        for (Map.Entry<String, PlayerPanel> entry : panelsPorJugador.entrySet()) {
            PlayerPanel panel = entry.getValue();
            if (panel == null) continue;
            panel.setOperator(containsIgnoreCase(operadores, entry.getKey()));
        }
    }

    private boolean modificarListaOffline(TipoLista tipo, String value, boolean add) {
        Path path = resolverRutaLista(tipo);
        if (path == null) {
            mostrarErrorLista("No se ha podido localizar el archivo de la lista.");
            return false;
        }

        try {
            ArrayNode items = leerArrayLista(path);
            boolean changed = add ? anadirEntradaLista(items, tipo, value) : quitarEntradaLista(items, tipo, value);
            if (!changed) {
                return false;
            }
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), items);
            actualizarListasAutomaticamente();
            return true;
        } catch (IOException e) {
            mostrarErrorLista("No se ha podido guardar la lista: " + e.getMessage());
            return false;
        }
    }

    private ArrayNode leerArrayLista(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return mapper.createArrayNode();
        }
        JsonNode root = mapper.readTree(path.toFile());
        if (root instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        return mapper.createArrayNode();
    }

    private boolean anadirEntradaLista(ArrayNode items, TipoLista tipo, String value) {
        if (items == null || tipo == null || value == null || value.isBlank()) return false;
        String normalizedValue = value.strip();
        if (contieneEntrada(items, tipo, normalizedValue)) return false;

        ObjectNode entry = construirEntradaLista(tipo, normalizedValue);
        if (entry == null) return false;
        items.add(entry);
        return true;
    }

    private boolean quitarEntradaLista(ArrayNode items, TipoLista tipo, String value) {
        if (items == null || tipo == null || value == null || value.isBlank()) return false;
        String normalizedValue = value.strip();
        for (int i = 0; i < items.size(); i++) {
            JsonNode node = items.get(i);
            String currentValue = tipo == TipoLista.BANNED_IPS ? node.path("ip").asString() : node.path("name").asString();
            if (currentValue != null && currentValue.strip().equalsIgnoreCase(normalizedValue)) {
                items.remove(i);
                return true;
            }
        }
        return false;
    }

    private boolean contieneEntrada(ArrayNode items, TipoLista tipo, String value) {
        for (JsonNode node : items) {
            String currentValue = tipo == TipoLista.BANNED_IPS ? node.path("ip").asString() : node.path("name").asString();
            if (currentValue != null && currentValue.strip().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode construirEntradaLista(TipoLista tipo, String value) {
        ObjectNode entry = mapper.createObjectNode();
        String timestamp = LIST_TIMESTAMP_FORMAT.format(ZonedDateTime.now());
        switch (tipo) {
            case WHITELIST -> {
                String uuid = resolverUuidJugador(value);
                if (uuid == null) return null;
                entry.put("uuid", uuid);
                entry.put("name", value);
            }
            case OPS -> {
                String uuid = resolverUuidJugador(value);
                if (uuid == null) return null;
                entry.put("uuid", uuid);
                entry.put("name", value);
                entry.put("level", 4);
                entry.put("bypassesPlayerLimit", false);
            }
            case BANNED_PLAYERS -> {
                String uuid = resolverUuidJugador(value);
                if (uuid == null) return null;
                entry.put("uuid", uuid);
                entry.put("name", value);
                entry.put("created", timestamp);
                entry.put("source", "Dora");
                entry.put("expires", "forever");
                entry.put("reason", "Banned by an operator.");
            }
            case BANNED_IPS -> {
                entry.put("ip", value);
                entry.put("created", timestamp);
                entry.put("source", "Dora");
                entry.put("expires", "forever");
                entry.put("reason", "Banned by an operator.");
            }
        }
        return entry;
    }

    private String resolverUuidJugador(String username) {
        String uuid = mojangAPI.obtenerUuidJugador(username);
        if (uuid != null && !uuid.isBlank()) {
            return uuid;
        }
        mostrarErrorLista("No se ha podido resolver el UUID de '" + username + "'.");
        return null;
    }

    private Path resolverRutaLista(TipoLista tipo) {
        if (tipo == null || server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return null;
        }
        return Path.of(server.getServerDir()).resolve(tipo.filename);
    }

    private boolean isServerActivo() {
        return server != null && server.getServerProcess() != null && server.getServerProcess().isAlive();
    }

    private boolean tieneServidorConDirectorio() {
        return server != null && server.getServerDir() != null && !server.getServerDir().isBlank();
    }

    private void mostrarErrorLista(String message) {
        JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                message,
                "Listas de jugadores",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private void iniciarWatcherListas(Server servidorActual) {
        detenerWatcherListas();
        if (servidorActual == null || servidorActual.getServerDir() == null || servidorActual.getServerDir().isBlank()) return;

        Path serverDir = Path.of(servidorActual.getServerDir());
        if (!Files.isDirectory(serverDir)) return;

        try {
            listasWatchService = serverDir.getFileSystem().newWatchService();
            serverDir.register(
                    listasWatchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY
            );
        } catch (IOException e) {
            detenerWatcherListas();
            return;
        }

        listasWatchThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted() && listasWatchService != null) {
                    WatchKey key = listasWatchService.take();
                    boolean relevantChange = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Object context = event.context();
                        if (!(context instanceof Path changed)) continue;
                        if (esListaObservada(changed.getFileName().toString())) {
                            relevantChange = true;
                        }
                    }
                    if (!key.reset()) {
                        break;
                    }
                    if (relevantChange) {
                        SwingUtilities.invokeLater(this::actualizarListasAutomaticamente);
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
            }
        }, "jugadores-listas-watch");
        listasWatchThread.setDaemon(true);
        listasWatchThread.start();
    }

    private void detenerWatcherListas() {
        if (listasWatchThread != null) {
            listasWatchThread.interrupt();
            listasWatchThread = null;
        }
        if (listasWatchService != null) {
            try {
                listasWatchService.close();
            } catch (IOException ignored) {
            }
            listasWatchService = null;
        }
    }

    private boolean esListaObservada(String filename) {
        if (filename == null || filename.isBlank()) return false;
        for (TipoLista tipo : TipoLista.values()) {
            if (tipo.filename.equalsIgnoreCase(filename)) return true;
        }
        return false;
    }

    private String comandoAdd(TipoLista tipo, String value) {
        return switch (tipo) {
            case WHITELIST -> "whitelist add " + value;
            case OPS -> "op " + value;
            case BANNED_PLAYERS -> "ban " + value;
            case BANNED_IPS -> "ban-ip " + value;
        };
    }

    private String comandoRemove(TipoLista tipo, String value) {
        return switch (tipo) {
            case WHITELIST -> "whitelist remove " + value;
            case OPS -> "deop " + value;
            case BANNED_PLAYERS -> "pardon " + value;
            case BANNED_IPS -> "pardon-ip " + value;
        };
    }

    private void addToSetWithLimit(LinkedHashSet<String> set, String nombre) {
        if (nombre == null || nombre.isBlank()) return;
        if (containsIgnoreCase(set, nombre)) return;
        if (set.size() >= maxPlayers) return;
        set.add(nombre);
    }

    private String normalizeComparableText(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private boolean containsIgnoreCase(Collection<String> values, String candidate) {
        if (values == null || candidate == null) return false;
        String normalizedCandidate = normalizeComparableText(candidate);
        for (String value : values) {
            if (normalizeComparableText(value).equals(normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }

    private void removeIgnoreCase(Collection<String> values, String candidate) {
        if (values == null || candidate == null) return;
        String normalizedCandidate = normalizeComparableText(candidate);
        values.removeIf(value -> normalizeComparableText(value).equals(normalizedCandidate));
    }

    private String getPlayerPanelKeyIgnoreCase(String playerName) {
        if (playerName == null || playerName.isBlank()) return null;
        String normalizedPlayerName = normalizeComparableText(playerName);
        for (String key : panelsPorJugador.keySet()) {
            if (normalizeComparableText(key).equals(normalizedPlayerName)) {
                return key;
            }
        }
        return null;
    }

    private int leerMaxPlayers(Server server) {
        try {
            if (server == null) return 20;
            String dir = server.getServerDir();
            if (dir == null || dir.isBlank()) return 20;

            Path path = Path.of(dir).resolve("server.properties");
            if (!Files.exists(path)) return 20;

            Properties props = Utilidades.cargarPropertiesUtf8(path);
            String value = props.getProperty("max-players");
            if (value == null || value.isBlank()) return 20;
            int parsed = Integer.parseInt(value.trim());
            return parsed <= 0 ? 20 : parsed;
        } catch (IOException | NumberFormatException e) {
            return 20;
        }
    }

    private class PlayerPanel extends JPanel {
        private final PlayerIdentityView identityView;
        private boolean operator;
        private boolean hover;

        PlayerPanel(String username) {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder());
            setToolTipText("Click derecho para acciones");
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            identityView = new PlayerIdentityView(username, PlayerIdentityView.SizePreset.REGULAR);
            add(identityView, BorderLayout.CENTER);
            instalarMenuContextual(username);
        }

        private void instalarMenuContextual(String username) {
            JPopupMenu menu = crearMenuJugador(username);

            MouseAdapter adapter = new MouseAdapter() {
                private void maybeShow(MouseEvent e) {
                    if (!e.isPopupTrigger()) return;
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    maybeShow(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    maybeShow(e);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    setHover(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setHover(false);
                }
            };

            addMouseListener(adapter);
            identityView.addMouseListener(adapter);
            identityView.getNameLabel().addMouseListener(adapter);
        }

        private void setHover(boolean hover) {
            this.hover = hover;
            applyOperatorStyle();
        }

        private void setOperator(boolean operator) {
            if (this.operator == operator) return;
            this.operator = operator;
            applyOperatorStyle();
        }

        private void applyOperatorStyle() {
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder());
            identityView.setOperatorHighlighted(operator);
            identityView.setHighlighted(hover);
            revalidate();
            repaint();
        }

        private JPopupMenu crearMenuJugador(String username) {
            JPopupMenu menu = new JPopupMenu();

            JMenuItem copiar = new JMenuItem("Copiar nombre");
            copiar.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(username), null));
            menu.add(copiar);
            menu.addSeparator();

            JMenuItem op = new JMenuItem("OP");
            op.addActionListener(e -> enviarComando("op " + username));
            menu.add(op);

            JMenuItem deop = new JMenuItem("De-OP");
            deop.addActionListener(e -> enviarComando("deop " + username));
            menu.add(deop);

            menu.addSeparator();

            JMenuItem kick = new JMenuItem("Kick...");
            kick.addActionListener(e -> {
                String razon = JOptionPane.showInputDialog(
                        SwingUtilities.getWindowAncestor(PanelJugadores.this),
                        "Razon (opcional):",
                        "Kick a " + username,
                        JOptionPane.QUESTION_MESSAGE
                );
                if (razon == null) return;
                razon = razon.strip();
                enviarComando(razon.isBlank() ? ("kick " + username) : ("kick " + username + " " + razon));
            });
            menu.add(kick);

            JMenuItem ban = new JMenuItem("Ban...");
            ban.addActionListener(e -> {
                String razon = JOptionPane.showInputDialog(
                        SwingUtilities.getWindowAncestor(PanelJugadores.this),
                        "Razon (opcional):",
                        "Ban a " + username,
                        JOptionPane.QUESTION_MESSAGE
                );
                if (razon == null) return;
                razon = razon.strip();
                enviarComando(razon.isBlank() ? ("ban " + username) : ("ban " + username + " " + razon));
            });
            menu.add(ban);

            JMenuItem pardon = new JMenuItem("Pardon (unban)");
            pardon.addActionListener(e -> enviarComando("pardon " + username));
            menu.add(pardon);

            menu.addSeparator();

            JMenuItem wlAdd = new JMenuItem("Whitelist add");
            wlAdd.addActionListener(e -> enviarComando("whitelist add " + username));
            menu.add(wlAdd);

            JMenuItem wlRemove = new JMenuItem("Whitelist remove");
            wlRemove.addActionListener(e -> enviarComando("whitelist remove " + username));
            menu.add(wlRemove);

            menu.addSeparator();

            JMenuItem tell = new JMenuItem("Enviar mensaje (/tell)...");
            tell.addActionListener(e -> {
                String msg = JOptionPane.showInputDialog(
                        SwingUtilities.getWindowAncestor(PanelJugadores.this),
                        "Mensaje:",
                        "Mensaje a " + username,
                        JOptionPane.QUESTION_MESSAGE
                );
                if (msg == null) return;
                msg = msg.strip();
                if (msg.isBlank()) return;
                enviarComando("tell " + username + " " + msg);
            });
            menu.add(tell);

            return menu;
        }
    }

    private static final class PlayerWrapPanel extends JPanel implements Scrollable {
        private PlayerWrapPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? 32 : 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            if (visibleRect == null) return 96;
            return orientation == SwingConstants.VERTICAL
                    ? Math.max(32, visibleRect.height - 32)
                    : Math.max(24, visibleRect.width - 24);
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

    private static final class PlayerListCellRenderer implements javax.swing.ListCellRenderer<String> {
        private final PlayerIdentityView.SizePreset sizePreset;
        private final int rowSpacing;

        private PlayerListCellRenderer(PlayerIdentityView.SizePreset sizePreset) {
            this.sizePreset = sizePreset == null ? PlayerIdentityView.SizePreset.REGULAR : sizePreset;
            this.rowSpacing = this.sizePreset == PlayerIdentityView.SizePreset.COMPACT ? 0 : 8;
        }

        @Override
        public java.awt.Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            PlayerIdentityView view = new PlayerIdentityView(value, sizePreset);
            view.setHighlighted(isSelected || cellHasFocus);
            view.setBackground(isSelected ? AppTheme.getSoftSelectionBackground() : list.getBackground());
            if (rowSpacing <= 0) {
                return view;
            }

            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setOpaque(false);
            wrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, rowSpacing, 0));
            wrapper.add(view, BorderLayout.NORTH);
            return wrapper;
        }
    }

    private final class PlayerRowsController {
        private final TipoLista tipo;
        private final DefaultListModel<String> model;
        private final AtomicReference<String> selectedValueRef;
        private final JPanel rowsPanel = new JPanel(new java.awt.GridBagLayout());
        private final JPanel content = new JPanel(new BorderLayout());
        private PlayerIdentityView.SizePreset sizePreset;

        private PlayerRowsController(
                TipoLista tipo,
                DefaultListModel<String> model,
                PlayerIdentityView.SizePreset sizePreset,
                AtomicReference<String> selectedValueRef
        ) {
            this.tipo = tipo;
            this.model = model;
            this.sizePreset = sizePreset == null ? PlayerIdentityView.SizePreset.REGULAR : sizePreset;
            this.selectedValueRef = selectedValueRef;

            rowsPanel.setOpaque(false);

            content.setOpaque(false);
            content.add(rowsPanel, BorderLayout.NORTH);
            content.setBorder(null);
        }

        private void setSizePreset(PlayerIdentityView.SizePreset nextPreset) {
            sizePreset = nextPreset == null ? PlayerIdentityView.SizePreset.REGULAR : nextPreset;
            rebuild();
        }

        private void rebuild() {
            String selectedValue = selectedValueRef.get();
            boolean selectionStillExists = false;

            rowsPanel.removeAll();
            int rowSpacing = sizePreset == PlayerIdentityView.SizePreset.COMPACT ? 0 : 8;
            java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
            gbc.gridx = 0;
            gbc.weightx = 1;
            gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gbc.anchor = java.awt.GridBagConstraints.NORTHWEST;
            for (int i = 0; i < model.getSize(); i++) {
                String value = model.getElementAt(i);
                if (value == null || value.isBlank()) continue;
                if (value.equalsIgnoreCase(selectedValue)) {
                    selectionStillExists = true;
                }

                PlayerIdentityView view = new PlayerIdentityView(value, sizePreset);
                view.addActionButton("doraicons/trash-unselected.svg", "Eliminar", () -> onRemove(tipo, value, model));
                view.setActionsVisibleOnHover(true);
                view.setHighlighted(value.equalsIgnoreCase(selectedValue));

                MouseAdapter selectionAdapter = new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (!SwingUtilities.isLeftMouseButton(e)) return;
                        selectedValueRef.set(value);
                        rebuild();
                    }
                };
                instalarSeleccionFila(view, selectionAdapter);

                gbc.gridy = i;
                gbc.weighty = 0;
                gbc.insets = new Insets(0, 0, rowSpacing, 0);
                view.setAlignmentX(Component.LEFT_ALIGNMENT);
                java.awt.Dimension preferredSize = view.getPreferredSize();
                view.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, preferredSize.height));
                rowsPanel.add(view, gbc);
            }

            gbc.gridy = model.getSize();
            gbc.weighty = 1;
            gbc.insets = new Insets(0, 0, 0, 0);
            JPanel filler = new JPanel();
            filler.setOpaque(false);
            rowsPanel.add(filler, gbc);

            if (!selectionStillExists) {
                selectedValueRef.set(null);
            }
            rowsPanel.revalidate();
            rowsPanel.repaint();
            content.revalidate();
            content.repaint();
        }

        private void instalarSeleccionFila(PlayerIdentityView view, MouseAdapter adapter) {
            view.addPrimaryMouseListener(adapter);
        }
    }
}
