package vista;

import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.extras.components.FlatComboBox;
import com.formdev.flatlaf.extras.components.FlatScrollPane;
import com.formdev.flatlaf.extras.components.FlatSpinner;
import com.formdev.flatlaf.extras.components.FlatTextField;
import controlador.GestorMundos;
import controlador.GestorServidores;
import controlador.MCARenderer;
import controlador.MojangAPI;
import controlador.Utilidades;
import controlador.WorldDataReader;
import controlador.world.PreviewOverlayData;
import controlador.world.PreviewPlayerData;
import controlador.world.PreviewPlayerPoint;
import controlador.world.PreviewRenderPreferences;
import controlador.world.WorldFilesService;
import controlador.world.WorldPlayerDataService;
import controlador.world.WorldPreviewOverlayService;
import controlador.world.WorldStorageAnalyzer;
import controlador.world.WorldStorageStats;
import modelo.MinecraftConstants;
import modelo.Server;
import modelo.World;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.NavigationFilter;
import javax.swing.text.Position;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class PanelMundo extends JPanel {
    private static final String[] REGION_FILE_COMPRESSION_OPTIONS = {"deflate", "lz4", "none"};
    private static final int PREVIEW_PLAYER_HEAD_SIZE = 24;
    private static final Pattern REGION_FILE_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$", Pattern.CASE_INSENSITIVE);
    private static final Map<Path, RegionVisibilityCacheEntry> REGION_VISIBILITY_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ImageIcon> PLAYER_HEAD_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PLAYER_HEADS_LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<PanelMundo> INSTANCIAS_ACTIVAS = java.util.Collections.newSetFromMap(new WeakHashMap<>());

    private final GestorServidores gestorServidores;
    private final Runnable onWorldChanged;

    private final JLabel tiempoRealLabel = new JLabel("-");
    private final JLabel lastPlayedLabel = new JLabel("Última apertura: -");
    private final JLabel versionLabel = new JLabel("Versión: -");
    private final JLabel tipoMundoLabel = new JLabel("-");
    private final PreviewImagePanel previewImageLabel = new PreviewImagePanel();
    private final PreviewSpinner previewSpinner = new PreviewSpinner();
    private final JLabel seedValueLabel = new JLabel("-");
    private final JLabel tiempoRealValueLabel = new JLabel("-");
    private final JLabel lastPlayedValueLabel = new JLabel("-");
    private final JLabel versionValueLabel = new JLabel("-");
    private final JLabel dataVersionValueLabel = new JLabel("-");
    private final JLabel tipoMundoValueLabel = new JLabel("-");
    private final JLabel spawnValueLabel = new JLabel("-");
    private final JLabel gamemodeValueLabel = new JLabel("-");
    private final JLabel difficultyValueLabel = new JLabel("-");
    private final JLabel weatherValueLabel = new JLabel("-");
    private final JLabel dayTimeValueLabel = new JLabel("-");
    private final JLabel hardcoreValueLabel = new JLabel("-");
    private final JLabel allowCommandsValueLabel = new JLabel("-");
    private final JLabel generatorSettingsValueLabel = new JLabel("-");
    private final JLabel estructurasValueLabel = new JLabel("-");
    private final JLabel gameRulesValueLabel = new JLabel("-");
    private final JLabel dataPacksValueLabel = new JLabel("-");
    private final JLabel initialEnabledPacksValueLabel = new JLabel("-");
    private final JLabel initialDisabledPacksValueLabel = new JLabel("-");
    private final JLabel previewRenderStatusLabel = new JLabel();
    private final JPanel metadataReadPanel = new JPanel();
    private final FlatComboBox<World> mundosCombo = new FlatComboBox<>();
    private final JButton refrescarButton = new FlatButton();
    private final JButton usarEsteMundoButton = new FlatButton();
    private final JButton importarButton = new FlatButton();
    private final JButton exportarButton = new FlatButton();
    private final JButton generarButton = new FlatButton();
    private final JButton generarPreviewButton = new FlatButton();
    private final JButton guardarConfiguracionMundoButton = new FlatButton();
    private final JButton previewMenuButton = new FlatButton();
    private final JCheckBox allowNetherCheckBox = new JCheckBox("Permitir Nether");
    private final FlatSpinner spawnProtectionSpinner = new FlatSpinner();
    private final FlatSpinner maxWorldSizeSpinner = new FlatSpinner();
    private final FlatComboBox<String> regionCompressionCombo = new FlatComboBox<>();
    private JDialog previewOptionsDialog;
    private JPanel previewOptionsPanel;
    private transient AWTEventListener previewOptionsOutsideClickListener;
    private final JCheckBox tiempoRealPreviewMenuItem = new JCheckBox("Renderizado en tiempo real", false);
    private final JCheckBox mostrarSpawnMenuItem = new JCheckBox("Mostrar spawn", false);
    private final JCheckBox mostrarJugadoresMenuItem = new JCheckBox("Mostrar jugadores", false);
    private final JCheckBox limitesChunksMenuItem = new JCheckBox("Límites de chunks", false);
    private final JCheckBox usarTodoMenuItem = new JCheckBox("Mapa completo", false);
    private final FlatComboBox<PreviewRenderPreferences.PreviewRenderPreset> perfilRenderCombo = new FlatComboBox<>();
    private final FlatComboBox<PreviewRenderLimitOption> limiteRenderCombo = new FlatComboBox<>();
    private final FlatComboBox<PreviewCenterOption> centroRenderCombo = new FlatComboBox<>();
    private final EnumMap<PreviewRenderPreferences.RenderToggle, JCheckBox> renderToggleCheckBoxes =
            new EnumMap<>(PreviewRenderPreferences.RenderToggle.class);
    private final JLabel pesoMundoLabel = new JLabel("-");
    private final JLabel pesoStatsSavesLabel = new JLabel("Peso stats y saves: -");
    private final JLabel pesoTotalLabel = new JLabel("-");
    private final JLabel pesoMundoValueLabel = new JLabel("-");
    private final JLabel pesoStatsSavesValueLabel = new JLabel("-");
    private final JLabel pesoTotalValueLabel = new JLabel("-");
    private final WorldRecentConnectionsPanel recentConnectionsPanel;

    private World mundoActivoActual;
    private boolean actualizandoComboMundos = false;
    private PreviewRenderPreferences previewPreferences = PreviewRenderPreferences.defaults();
    private static final int EMPTY_SPIRAL_RINGS_TO_STOP = 2;
    private static final int PREVIEW_RENDER_MARGIN_BLOCKS = MCARenderer.CHUNK_BLOCK_SIDE;
    private static boolean previewGenerationInProgress = false;
    private static SwingWorker<PreviewGenerationResult, PreviewGenerationUpdate> previewGenerationWorker;
    private final AtomicLong previewGenerationSequence = new AtomicLong();
    private boolean previewDisponibleAntesDeGenerar = false;
    private static String previewGenerationServerId;
    private static String previewGenerationServerName;
    private static String previewGenerationWorldDir;
    private static String previewGenerationWorldName;
    private String ultimoTiempoRenderPreview;
    private String ultimoDetalleTiempoRenderPreview;
    private String progresoRenderPreviewActual;
    private BufferedImage previewRealtimeCanvas;
    private boolean actualizandoOpcionesPreview = false;
    private boolean updatingWorldSettingsControls = false;
    private boolean persistedAllowNether = true;
    private int persistedSpawnProtection = 16;
    private int persistedMaxWorldSize = 29999984;
    private String persistedRegionCompression = "deflate";

    PanelMundo(GestorServidores gestorServidores, Runnable onWorldChanged) {
        this.gestorServidores = gestorServidores;
        this.onWorldChanged = onWorldChanged;
        this.recentConnectionsPanel = new WorldRecentConnectionsPanel(
                gestorServidores::getServidorSeleccionado,
                this::getMundoSeleccionadoOActivo
        );
        setLayout(new BorderLayout());
        setOpaque(false);
        INSTANCIAS_ACTIVAS.add(this);


        refrescarButton.setToolTipText("Refrescar");
        usarEsteMundoButton.setText("Usar este mundo");
        importarButton.setText("Importar mundo");
        exportarButton.setText("Exportar mundo");
        generarButton.setText("Generar nuevo");
        generarPreviewButton.setText("Generar vista previa");
        guardarConfiguracionMundoButton.setText("Guardar ajustes del mundo");
        previewMenuButton.setText(null);
        AppTheme.applyRefreshIconButtonStyle(refrescarButton);
        AppTheme.applyActionButtonStyle(importarButton);
        AppTheme.applyActionButtonStyle(exportarButton);
        AppTheme.applyActionButtonStyle(generarButton);
        AppTheme.applyActionButtonStyle(generarPreviewButton);
        AppTheme.applyActionButtonStyle(guardarConfiguracionMundoButton);
        stylePreviewOverlayButton(generarPreviewButton);
        stylePreviewMenuButton(previewMenuButton);
        applyDefaultPrimaryButtonStyle();
        spawnProtectionSpinner.setModel(new SpinnerNumberModel(16, 0, Integer.MAX_VALUE, 1));
        maxWorldSizeSpinner.setModel(new SpinnerNumberModel(29999984, 1, Integer.MAX_VALUE, 1));
        spawnProtectionSpinner.setEditor(new JSpinner.NumberEditor(spawnProtectionSpinner, "#"));
        maxWorldSizeSpinner.setEditor(new JSpinner.NumberEditor(maxWorldSizeSpinner, "#"));
        regionCompressionCombo.setModel(new DefaultComboBoxModel<>(REGION_FILE_COMPRESSION_OPTIONS));
        perfilRenderCombo.setModel(new DefaultComboBoxModel<>(PreviewRenderPreferences.PreviewRenderPreset.values()));
        limiteRenderCombo.setModel(new DefaultComboBoxModel<>(new PreviewRenderLimitOption[]{
                new PreviewRenderLimitOption("64x64", 64),
                new PreviewRenderLimitOption("128x128", 128),
                new PreviewRenderLimitOption("256x256", 256),
                new PreviewRenderLimitOption("512x512", 512),
                new PreviewRenderLimitOption("1024x1024", 1024),
                new PreviewRenderLimitOption("2048x2048", 2048),
                new PreviewRenderLimitOption("4096x4096", 4096),
                new PreviewRenderLimitOption("8192x8192", 8192),
                new PreviewRenderLimitOption("16384x16384", 16384),
                new PreviewRenderLimitOption("Ilimitado", 0)
        }));

        previewImageLabel.setPreferredSize(new Dimension(320, 320));
        previewImageLabel.setMinimumSize(new Dimension(260, 260));
        instalarMenuContextualPreview();
        previewSpinner.setVisible(false);

        styleInfoLabel(seedValueLabel);
        styleInfoLabel(tiempoRealValueLabel);
        styleInfoLabel(lastPlayedValueLabel);
        styleInfoLabel(versionValueLabel);
        styleInfoLabel(dataVersionValueLabel);
        styleInfoLabel(tipoMundoValueLabel);
        styleInfoLabel(spawnValueLabel);
        styleInfoLabel(gamemodeValueLabel);
        styleInfoLabel(difficultyValueLabel);
        styleInfoLabel(weatherValueLabel);
        styleInfoLabel(dayTimeValueLabel);
        styleInfoLabel(hardcoreValueLabel);
        styleInfoLabel(allowCommandsValueLabel);
        styleInfoLabel(generatorSettingsValueLabel);
        styleInfoLabel(estructurasValueLabel);
        styleInfoLabel(gameRulesValueLabel);
        styleInfoLabel(dataPacksValueLabel);
        styleInfoLabel(initialEnabledPacksValueLabel);
        styleInfoLabel(initialDisabledPacksValueLabel);
        styleInfoLabel(pesoMundoValueLabel);
        styleInfoLabel(pesoStatsSavesValueLabel);
        styleInfoLabel(pesoTotalValueLabel);
        stylePreviewStatusLabel(previewRenderStatusLabel);
        instalarInteraccionSeed();
        configurarControlesAjustesMundo();

        metadataReadPanel.setOpaque(false);
        metadataReadPanel.setLayout(new BoxLayout(metadataReadPanel, BoxLayout.Y_AXIS));

        mundosCombo.addActionListener(e -> {
            if (actualizandoComboMundos) return;
            actualizarVistaMundos();
        });
        refrescarButton.addActionListener(e -> refrescarDatos());
        usarEsteMundoButton.addActionListener(e -> cambiarMundoSeleccionado());
        importarButton.addActionListener(e -> {
            if (GestorMundos.importarMundo(gestorServidores.getServidorSeleccionado(), this)) {
                refrescarDatos();
                notificarCambioDeMundo();
            }
        });
        exportarButton.addActionListener(e -> {
            World mundo = (World) mundosCombo.getSelectedItem();
            if (GestorMundos.exportarMundo(gestorServidores.getServidorSeleccionado(), mundo, this)) {
                refrescarDatos();
            }
        });
        generarButton.addActionListener(e -> abrirDialogoNuevoMundo());
        generarPreviewButton.addActionListener(e -> generarPreviewMundoSeleccionado());
        guardarConfiguracionMundoButton.addActionListener(e -> guardarConfiguracionMundo());
        instalarMenuOpcionesPreview();

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(crearTarjetaPrincipal());
        body.add(Box.createVerticalStrut(8));
        body.add(crearFilaInferior());

        add(body, BorderLayout.CENTER);

        refrescarDatos();
        sincronizarEstadoRenderCompartido();
    }

    private JPanel crearTarjetaPrincipal() {
        CardPanel card = new CardPanel("Mundo");
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        card.getHeaderActionsPanel().add(refrescarButton);

        JPanel izquierda = new JPanel();
        izquierda.setOpaque(false);
        izquierda.setLayout(new BoxLayout(izquierda, BoxLayout.Y_AXIS));

        JPanel izquierdaWrap = new JPanel(new BorderLayout());
        izquierdaWrap.setOpaque(false);

        JPanel selectorPanel = new JPanel(new BorderLayout(8, 0));
        selectorPanel.setOpaque(false);
        selectorPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mundosCombo.setPreferredSize(new Dimension(250, 36));
        mundosCombo.setFont(mundosCombo.getFont().deriveFont(Font.BOLD, Math.max(13f, mundosCombo.getFont().getSize2D())));
        mundosCombo.putClientProperty("JComponent.roundRect", true);
        selectorPanel.add(mundosCombo, BorderLayout.CENTER);
        selectorPanel.add(usarEsteMundoButton, BorderLayout.EAST);

        JPanel infoPanel = new JPanel();
        infoPanel.setOpaque(false);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(crearInfoRow("Tiempo activo:", tiempoRealValueLabel));
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(crearInfoRow("Última apertura:", lastPlayedValueLabel));
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(crearInfoRow("Versión:", versionValueLabel));
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(crearSeedRow());
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(crearInfoRow("Hora del mundo:", dayTimeValueLabel));
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(crearInfoRow("Clima:", weatherValueLabel));
        infoPanel.add(Box.createVerticalStrut(8));
        infoPanel.add(crearInfoRow("Peso mundo:", pesoMundoValueLabel));
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(crearInfoRow("Peso stats y saves:", pesoStatsSavesValueLabel));
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(crearInfoRow("Peso total:", pesoTotalValueLabel));

        JPanel accionesPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
        accionesPanel.setOpaque(false);
        accionesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        accionesPanel.add(generarButton);
        accionesPanel.add(importarButton);
        accionesPanel.add(exportarButton);

        JPanel contenidoIzquierdo = new JPanel();
        contenidoIzquierdo.setOpaque(false);
        contenidoIzquierdo.setLayout(new BoxLayout(contenidoIzquierdo, BoxLayout.Y_AXIS));
        contenidoIzquierdo.add(selectorPanel);
        contenidoIzquierdo.add(Box.createVerticalStrut(6));
        contenidoIzquierdo.add(infoPanel);

        izquierda.setLayout(new BorderLayout(0, 8));
        izquierda.add(contenidoIzquierdo, BorderLayout.NORTH);
        izquierda.add(accionesPanel, BorderLayout.SOUTH);

        izquierdaWrap.add(izquierda, BorderLayout.CENTER);
        card.getContentPanel().add(izquierdaWrap, BorderLayout.CENTER);

        card.setFullHeightSideComponent(crearPanelPreview());
        return card;
    }

    private JPanel crearSeedRow() {
        return crearInfoRow("Semilla:", seedValueLabel);
    }

    private JPanel crearInfoRow(String titulo, JLabel valorLabel) {
        return BoxCategory.createInfoRow(titulo, valorLabel);
    }


    private JPanel crearFilaInferior() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(0, 0, 0, 0);

        gbc.gridx = 0;
        gbc.weightx = 1.0;
        panel.add(crearTarjetaDatosMundo(), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 8, 0, 0);
        panel.add(crearTarjetaConfiguracionMundo(), gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 8, 0, 0);
        panel.add(crearTarjetaConexiones(), gbc);
        return panel;
    }

    private JPanel crearTarjetaDatosMundo() {
        CardPanel card = new CardPanel("Datos");
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        FlatScrollPane lecturaScroll = new FlatScrollPane();
        lecturaScroll.setViewportView(metadataReadPanel);
        lecturaScroll.setBorder(BorderFactory.createEmptyBorder());
        lecturaScroll.getViewport().setOpaque(false);
        lecturaScroll.setOpaque(false);
        lecturaScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        lecturaScroll.setPreferredSize(new Dimension(0, 250));
        lecturaScroll.getVerticalScrollBar().setUnitIncrement(20);
        lecturaScroll.getVerticalScrollBar().setBlockIncrement(80);
        card.getContentPanel().add(lecturaScroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel crearTarjetaConfiguracionMundo() {
        CardPanel card = new CardPanel("Configuración");
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel ajustes = new JPanel();
        ajustes.setOpaque(false);
        ajustes.setLayout(new BoxLayout(ajustes, BoxLayout.Y_AXIS));
        ajustes.add(crearTarjetaAjusteBooleano("Permitir Nether", allowNetherCheckBox));
        ajustes.add(Box.createVerticalStrut(4));
        ajustes.add(crearTarjetaAjusteCampo("Compresión de regiones", regionCompressionCombo));
        ajustes.add(Box.createVerticalStrut(4));
        ajustes.add(crearTarjetaAjusteCampo("Protección del spawn", spawnProtectionSpinner));
        ajustes.add(Box.createVerticalStrut(4));
        ajustes.add(crearTarjetaAjusteCampo("Tamaño máximo del mundo", maxWorldSizeSpinner));
        ajustes.add(Box.createVerticalGlue());
        ajustes.add(Box.createVerticalStrut(6));
        guardarConfiguracionMundoButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        guardarConfiguracionMundoButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, guardarConfiguracionMundoButton.getPreferredSize().height));
        ajustes.add(guardarConfiguracionMundoButton);

        FlatScrollPane ajustesScroll = new FlatScrollPane();
        ajustesScroll.setViewportView(ajustes);
        ajustesScroll.setBorder(BorderFactory.createEmptyBorder());
        ajustesScroll.getViewport().setOpaque(false);
        ajustesScroll.setOpaque(false);
        ajustesScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        ajustesScroll.getVerticalScrollBar().setUnitIncrement(18);
        ajustesScroll.getVerticalScrollBar().setBlockIncrement(72);
        card.getContentPanel().add(ajustesScroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel crearTarjetaConexiones() {
        return recentConnectionsPanel.crearTarjeta();
    }

    private JPanel crearTarjetaAjusteBooleano(String labelText, JCheckBox checkBox) {
        checkBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        checkBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, checkBox.getPreferredSize().height));
        return BoxCategory.createBooleanCard(labelText, checkBox);
    }

    private JPanel crearTarjetaAjusteCampo(String labelText, JComponent field) {
        prepararCampoConfiguracion(field);
        return BoxCategory.createFieldCard(labelText, field);
    }

    private void prepararCampoConfiguracion(JComponent field) {
        if (field == null) {
            return;
        }
        if (field instanceof JComboBox<?> comboBox) {
            comboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            comboBox.setPreferredSize(new Dimension(180, 30));
        } else if (field instanceof JSpinner spinner) {
            spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            spinner.setPreferredSize(new Dimension(180, 30));
        }
        BoxCategory.makeWidthFlexible(field);
    }

    private JPanel crearPanelPreview() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(320, 320));
        panel.setMinimumSize(new Dimension(260, 260));

        JPanel overlayControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        overlayControls.setOpaque(false);
        overlayControls.add(generarPreviewButton);
        overlayControls.add(previewMenuButton);

        JPanel overlayCorner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        overlayCorner.setOpaque(false);
        overlayCorner.setLayout(new BoxLayout(overlayCorner, BoxLayout.Y_AXIS));
        previewRenderStatusLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        overlayControls.setAlignmentX(Component.RIGHT_ALIGNMENT);
        overlayCorner.add(previewRenderStatusLabel);
        overlayCorner.add(Box.createVerticalStrut(6));
        overlayCorner.add(overlayControls);

        JPanel progressWrap = new JPanel(new BorderLayout());
        progressWrap.setOpaque(false);
        progressWrap.add(previewSpinner, BorderLayout.CENTER);
        progressWrap.setVisible(false);

        JPanel checkerboardPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    int size = 16;
                    Color base = AppTheme.getPanelBackground();
                    Color light = AppTheme.tint(base, Color.WHITE, 0.10f);
                    Color dark = AppTheme.darken(base, 0.05f);

                    for (int y = 0; y < getHeight(); y += size) {
                        for (int x = 0; x < getWidth(); x += size) {
                            boolean even = ((x / size) + (y / size)) % 2 == 0;
                            g2.setColor(even ? light : dark);
                            g2.fillRect(x, y, size, size);
                        }
                    }
                } finally {
                    g2.dispose();
                }
            }
        };
        checkerboardPanel.setOpaque(true);
        checkerboardPanel.add(previewImageLabel, BorderLayout.CENTER);

        JLayeredPane layeredPreview = new JLayeredPane() {
            @Override
            public void doLayout() {
                int w = getWidth();
                int h = getHeight();
                checkerboardPanel.setBounds(0, 0, w, h);

                Dimension pref = overlayCorner.getPreferredSize();
                int x = Math.max(0, w - pref.width - 8);
                int y = Math.max(0, h - pref.height - 8);
                overlayCorner.setBounds(x, y, pref.width, pref.height);

                Dimension progressPref = progressWrap.getPreferredSize();
                int progressWidth = progressPref.width;
                int progressHeight = progressPref.height;
                int progressX = Math.max(0, (w - progressWidth) / 2);
                int progressY = Math.max(0, (h - progressHeight) / 2);
                progressWrap.setBounds(progressX, progressY, progressWidth, progressHeight);
            }
        };
        layeredPreview.setOpaque(false);
        layeredPreview.setPreferredSize(new Dimension(320, 320));
        layeredPreview.setMinimumSize(new Dimension(260, 260));
        layeredPreview.add(checkerboardPanel, JLayeredPane.DEFAULT_LAYER);
        layeredPreview.add(overlayCorner, JLayeredPane.PALETTE_LAYER);
        layeredPreview.add(progressWrap, JLayeredPane.MODAL_LAYER);

        panel.add(layeredPreview, BorderLayout.CENTER);
        panel.putClientProperty("previewProgressWrap", progressWrap);
        return panel;
    }

    private void refrescarDatos() {
        Server server = gestorServidores.getServidorSeleccionado();
        actualizandoComboMundos = true;
        mundosCombo.removeAllItems();
        mundoActivoActual = null;

        if (server == null) {
            limpiarVistaSinServidor();
            actualizandoComboMundos = false;
            return;
        }

        try {
            GestorMundos.sincronizarMundosServidor(server);
            List<World> mundos = GestorMundos.listarMundos(server);
            mundoActivoActual = GestorMundos.getMundoActivo(server);

            for (World mundo : mundos) {
                mundosCombo.addItem(mundo);
            }

            if (!mundos.isEmpty()) {
                seleccionarMundoEnCombo(mundoActivoActual);
                if (mundosCombo.getSelectedItem() == null) {
                    mundosCombo.setSelectedIndex(0);
                }
            }
            setControlesActivos(true);
        } catch (RuntimeException ex) {
            tiempoRealValueLabel.setText("-");
            seedValueLabel.setText("-");
            lastPlayedValueLabel.setText("-");
            versionValueLabel.setText("-");
            dataVersionValueLabel.setText("-");
            tipoMundoValueLabel.setText("-");
            spawnValueLabel.setText("-");
            gamemodeValueLabel.setText("-");
            difficultyValueLabel.setText("-");
            weatherValueLabel.setText("-");
            dayTimeValueLabel.setText("-");
            hardcoreValueLabel.setText("-");
            allowCommandsValueLabel.setText("-");
            gameRulesValueLabel.setText("-");
            dataPacksValueLabel.setText("-");
            pesoMundoValueLabel.setText("-");
            pesoStatsSavesValueLabel.setText("-");
            pesoTotalValueLabel.setText("-");
            mostrarTextoPreview("Error cargando mundos.");
            setControlesActivos(false);
            recentConnectionsPanel.actualizar();
        } finally {
            actualizandoComboMundos = false;
        }
        actualizarVistaMundos();
    }

    private void cargarPreferenciasPreviewServidorActual() {
        Server server = gestorServidores.getServidorSeleccionado();
        World mundo = getMundoSeleccionadoOActivo();
        actualizandoOpcionesPreview = true;
        try {
            PreviewRenderPreferences fallback = PreviewRenderPreferences.fromLegacyServer(server);
            Properties metadata = WorldFilesService.readWorldMetadata(mundo);
            previewPreferences = PreviewRenderPreferences.fromProperties(metadata, fallback);
            aplicarPreferenciasPreviewAControles();
            seleccionarLimiteRenderComboActual();
        } finally {
            actualizandoOpcionesPreview = false;
        }
        aplicarPreferenciasPreviewAOverlay();
        actualizarEstadoControlesRender();
    }

    private void guardarPreferenciasPreviewServidorActual() {
        if (actualizandoOpcionesPreview) {
            return;
        }
        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null) {
            return;
        }
        try {
            Properties metadata = WorldFilesService.readWorldMetadata(mundo);
            previewPreferences.writeTo(metadata);
            WorldFilesService.writeWorldMetadata(mundo, metadata);
        } catch (IOException ex) {
            System.out.println("[PanelMundo] No se ha podido guardar la configuración de preview del mundo: " + ex.getMessage());
        }
    }

    private void aplicarPreferenciasPreviewAControles() {
        tiempoRealPreviewMenuItem.setSelected(previewPreferences.renderRealtime());
        mostrarSpawnMenuItem.setSelected(previewPreferences.showSpawn());
        mostrarJugadoresMenuItem.setSelected(previewPreferences.showPlayers());
        limitesChunksMenuItem.setSelected(previewPreferences.showChunkGrid());
        usarTodoMenuItem.setSelected(previewPreferences.useWholeMap());
        seleccionarPerfilRenderPreview(previewPreferences.preset());
        for (PreviewRenderPreferences.RenderToggle toggle : PreviewRenderPreferences.RenderToggle.values()) {
            JCheckBox checkBox = renderToggleCheckBoxes.get(toggle);
            if (checkBox != null) {
                checkBox.setSelected(previewPreferences.isEnabled(toggle));
            }
        }
    }

    private void aplicarPreferenciasPreviewAOverlay() {
        previewImageLabel.setSpawnVisible(previewPreferences.showSpawn());
        previewImageLabel.setPlayersVisible(previewPreferences.showPlayers());
        previewImageLabel.setChunkGridVisible(previewPreferences.showChunkGrid());
    }

    private void limpiarVistaSinServidor() {
        cargarPreferenciasPreviewServidorActual();
        updatingWorldSettingsControls = true;
        tiempoRealValueLabel.setText("-");
        seedValueLabel.setText("-");
        lastPlayedValueLabel.setText("-");
        versionValueLabel.setText("-");
        dataVersionValueLabel.setText("-");
        tipoMundoValueLabel.setText("-");
        spawnValueLabel.setText("-");
        gamemodeValueLabel.setText("-");
        difficultyValueLabel.setText("-");
        weatherValueLabel.setText("-");
        dayTimeValueLabel.setText("-");
        hardcoreValueLabel.setText("-");
        allowCommandsValueLabel.setText("-");
        generatorSettingsValueLabel.setText("-");
        estructurasValueLabel.setText("-");
        gameRulesValueLabel.setText("-");
        dataPacksValueLabel.setText("-");
        initialEnabledPacksValueLabel.setText("-");
        initialDisabledPacksValueLabel.setText("-");
        allowNetherCheckBox.setSelected(false);
        regionCompressionCombo.setSelectedItem("deflate");
        spawnProtectionSpinner.setValue(16);
        maxWorldSizeSpinner.setValue(29999984);
        updatingWorldSettingsControls = false;
        markCurrentWorldSettingsAsPersisted();
        updateWorldSettingsSaveButtonState();
        pesoMundoValueLabel.setText("-");
        pesoStatsSavesValueLabel.setText("-");
        pesoTotalValueLabel.setText("-");
        mostrarTextoPreview("Selecciona un servidor para gestionar sus mundos.");
        recentConnectionsPanel.actualizar();
        setControlesActivos(false);
        actualizarTextoBotonPreview();
        actualizarIndicadorRenderEnCurso();
    }

    private void actualizarVistaMundos() {
        updateUseWorldButtonState();
        cargarPreferenciasPreviewServidorActual();
        World mundo = getMundoSeleccionadoOActivo();
        WorldDataReader.WorldMetadata metadata = mundo == null ? null : WorldDataReader.readMetadata(mundo);
        actualizarLabelsDatosServidor(metadata);
        actualizarConfiguracionMundo(metadata);
        actualizarPreviewSeleccionada();
        actualizarTextoBotonPreview();
        actualizarIndicadorRenderEnCurso();
        sincronizarEstadoRenderCompartido();
        actualizarDatosAlmacenamiento();
        recentConnectionsPanel.actualizar();
    }

    private void actualizarLabelsDatosServidor() {
        World mundo = getMundoSeleccionadoOActivo();
        actualizarLabelsDatosServidor(mundo == null ? null : WorldDataReader.readMetadata(mundo));
    }

    private void actualizarLabelsDatosServidor(WorldDataReader.WorldMetadata metadataMundo) {
        Server server = gestorServidores.getServidorSeleccionado();
        if (server == null) {
            limpiarVistaSinServidor();
            return;
        }

        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null) {
            tiempoRealValueLabel.setText("-");
            seedValueLabel.setText("-");
            lastPlayedValueLabel.setText("-");
            versionValueLabel.setText("-");
            dataVersionValueLabel.setText("-");
            tipoMundoValueLabel.setText("-");
            dayTimeValueLabel.setText("-");
            spawnValueLabel.setText("-");
            gamemodeValueLabel.setText("-");
            difficultyValueLabel.setText("-");
            weatherValueLabel.setText("-");
            return;
        }

        WorldDataReader.WorldMetadata metadata = metadataMundo == null ? WorldDataReader.readMetadata(mundo) : metadataMundo;
        long ticksArchivo = metadata.activeTicks();
        tiempoRealValueLabel.setText(ticksArchivo >= 0L ? formatearTiempo(ticksArchivo) : "-");
        String lastPlayed = metadata.lastPlayed();
        lastPlayedValueLabel.setText(valorOPlaceholder(lastPlayed));
        versionValueLabel.setText(valorOPlaceholder(metadata.versionName()));
        dataVersionValueLabel.setText(valorOPlaceholder(metadata.dataVersion()));
        tipoMundoValueLabel.setText(valorOPlaceholder(leerTipoMundo(mundo)));
        seedValueLabel.setText(valorOPlaceholder(metadata.seed()));
        dayTimeValueLabel.setText(valorOPlaceholder(metadata.dayTime()));
        gamemodeValueLabel.setText(valorOPlaceholder(metadata.gameMode()));
        difficultyValueLabel.setText(construirResumenDificultad(metadata));
        weatherValueLabel.setText(valorOPlaceholder(metadata.weatherSummary()));
        spawnValueLabel.setText(valorOPlaceholder(formatearSpawn(metadata.spawnPoint())));
    }

    private void actualizarConfiguracionMundo() {
        World mundo = getMundoSeleccionadoOActivo();
        actualizarConfiguracionMundo(mundo == null ? null : WorldDataReader.readMetadata(mundo));
    }

    private void actualizarConfiguracionMundo(WorldDataReader.WorldMetadata metadataMundo) {
        Server server = gestorServidores.getServidorSeleccionado();
        World mundo = getMundoSeleccionadoOActivo();
        Properties metadata = WorldFilesService.readWorldMetadata(mundo);
        Properties serverProps = WorldFilesService.readServerProperties(server);
        WorldDataReader.WorldMetadata levelMetadata = metadataMundo == null && mundo != null
                ? WorldDataReader.readMetadata(mundo)
                : metadataMundo;

        dayTimeValueLabel.setText(valorOPlaceholder(levelMetadata == null ? null : levelMetadata.dayTime()));
        hardcoreValueLabel.setText(valorOPlaceholder(primeroNoVacio(levelMetadata == null ? null : levelMetadata.hardcore(), formatearBoolean(metadata.getProperty("hardcore")))));
        allowCommandsValueLabel.setText(valorOPlaceholder(levelMetadata == null ? null : levelMetadata.allowCommands()));
        generatorSettingsValueLabel.setText(formatearGeneratorSettings(metadata.getProperty("generator-settings")));
        estructurasValueLabel.setText(formatearEstructuras(metadata.getProperty("generate-structures")));
        dataPacksValueLabel.setText(valorOPlaceholder(primeroNoVacio(
                levelMetadata == null ? null : levelMetadata.dataPacksSummary(),
                construirResumenPacksIniciales(metadata)
        )));
        gameRulesValueLabel.setText(valorOPlaceholder(levelMetadata == null ? null : levelMetadata.gameRulesSummary()));
        initialEnabledPacksValueLabel.setText(valorOPlaceholder(metadata.getProperty("initial-enabled-packs")));
        initialDisabledPacksValueLabel.setText(valorOPlaceholder(metadata.getProperty("initial-disabled-packs")));
        reconstruirMetadataMundo(levelMetadata);
        actualizarTooltipResumenLargo(dataPacksValueLabel, construirTooltipPacks(metadata));
        actualizarTooltipResumenLargo(gameRulesValueLabel, "Resumen de gamerules principales leidas desde level.dat.");

        updatingWorldSettingsControls = true;
        allowNetherCheckBox.setSelected(leerBoolean(serverProps, "allow-nether", true));
        regionCompressionCombo.setSelectedItem(normalizarRegionCompression(serverProps.getProperty("region-file-compression")));
        spawnProtectionSpinner.setValue(leerEntero(serverProps, "spawn-protection", 16, 0));
        maxWorldSizeSpinner.setValue(leerEntero(serverProps, "max-world-size", 29999984, 1));
        updatingWorldSettingsControls = false;
        markCurrentWorldSettingsAsPersisted();
        updateWorldSettingsSaveButtonState();
    }

    private void reconstruirMetadataMundo(WorldDataReader.WorldMetadata metadata) {
        metadataReadPanel.removeAll();
        agregarSeccionMetadata("Identidad");
        metadataReadPanel.add(crearInfoRow("Versión de datos:", dataVersionValueLabel));
        metadataReadPanel.add(Box.createVerticalStrut(4));
        metadataReadPanel.add(crearInfoRow("Tipo:", tipoMundoValueLabel));
        metadataReadPanel.add(Box.createVerticalStrut(10));

        agregarSeccionMetadata("Estado del mundo");
        metadataReadPanel.add(crearInfoRow("Modo de juego:", gamemodeValueLabel));
        metadataReadPanel.add(Box.createVerticalStrut(4));
        metadataReadPanel.add(crearInfoRow("Dificultad:", difficultyValueLabel));
        metadataReadPanel.add(Box.createVerticalStrut(4));
        metadataReadPanel.add(crearInfoRow("Spawn:", spawnValueLabel));
        metadataReadPanel.add(Box.createVerticalStrut(4));
        metadataReadPanel.add(crearInfoRow("Hardcore:", hardcoreValueLabel));
        metadataReadPanel.add(Box.createVerticalStrut(4));
        metadataReadPanel.add(crearInfoRow("Comandos:", allowCommandsValueLabel));
        metadataReadPanel.add(Box.createVerticalStrut(10));

        agregarSeccionMetadata("Generación y packs");
        metadataReadPanel.add(crearInfoRow("Ajustes del generador:", generatorSettingsValueLabel));
        metadataReadPanel.add(Box.createVerticalStrut(4));
        metadataReadPanel.add(crearInfoRow("Generar estructuras:", estructurasValueLabel));
        metadataReadPanel.add(Box.createVerticalStrut(4));
        metadataReadPanel.add(crearInfoRow("Data packs:", dataPacksValueLabel));
        metadataReadPanel.add(Box.createVerticalStrut(4));
        metadataReadPanel.add(crearInfoRow("Initial enabled packs:", initialEnabledPacksValueLabel));
        metadataReadPanel.add(Box.createVerticalStrut(4));
        metadataReadPanel.add(crearInfoRow("Initial disabled packs:", initialDisabledPacksValueLabel));
        metadataReadPanel.add(Box.createVerticalStrut(10));

        agregarSeccionMetadata("Gamerules");
        metadataReadPanel.add(crearSeccionGameRules(metadata == null ? Map.of() : metadata.gameRules()));
        metadataReadPanel.add(Box.createVerticalGlue());
        metadataReadPanel.revalidate();
        metadataReadPanel.repaint();
    }

    private void agregarSeccionMetadata(String titulo) {
        if (metadataReadPanel.getComponentCount() > 0) {
            metadataReadPanel.add(Box.createVerticalStrut(2));
        }
        metadataReadPanel.add(crearSeparadorSeccionMetadata(titulo));
        metadataReadPanel.add(Box.createVerticalStrut(8));
    }

    private JPanel crearSeparadorSeccionMetadata(String titulo) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(titulo);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setForeground(AppTheme.getMutedForeground());

        JSeparator separator = new JSeparator();
        separator.setForeground(AppTheme.getSubtleBorderColor());

        panel.add(label, BorderLayout.WEST);
        panel.add(separator, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        return panel;
    }

    private JPanel crearSeccionGameRules(Map<String, String> gameRules) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (gameRules == null || gameRules.isEmpty()) {
            JLabel fallback = new JLabel("No se han encontrado gamerules en level.dat.");
            fallback.setForeground(AppTheme.getMutedForeground());
            fallback.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(fallback);
            return panel;
        }

        for (Map.Entry<String, String> entry : gameRules.entrySet()) {
            panel.add(crearInfoRow(formatearNombreGameRule(entry.getKey()) + ":", crearValorGameRuleLabel(entry.getValue())));
            panel.add(Box.createVerticalStrut(2));
        }

        return panel;
    }

    private JLabel crearValorGameRuleLabel(String value) {
        JLabel label = new JLabel(valorOPlaceholder(formatearValorGameRule(value)));
        styleInfoLabel(label);
        return label;
    }

    private String formatearValorGameRule(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = quitarComillasEnvolventes(value.trim());
        if ("true".equalsIgnoreCase(normalized) || "si".equalsIgnoreCase(normalized) || "\u00E2\u0153\u201C".equals(normalized)) {
            return "\u2713";
        }
        if ("false".equalsIgnoreCase(normalized) || "no".equalsIgnoreCase(normalized) || "\u00E2\u0153\u201D".equals(normalized)) {
            return "\u2717";
        }
        return normalized;
    }

    private String quitarComillasEnvolventes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        boolean comillasDobles = value.startsWith("\"") && value.endsWith("\"");
        boolean comillasSimples = value.startsWith("'") && value.endsWith("'");
        if (comillasDobles || comillasSimples) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private String formatearNombreGameRule(String key) {
        if (key == null || key.isBlank()) {
            return "-";
        }
        String spaced = key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('-', ' ').trim();
        if (spaced.isEmpty()) {
            return key;
        }
        String[] words = spaced.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }
        return result.toString();
    }

    private void guardarConfiguracionMundo() {
        Server server = gestorServidores.getServidorSeleccionado();
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            JOptionPane.showMessageDialog(this, "No hay un servidor seleccionado.", "Configuración del mundo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Properties props = WorldFilesService.readServerProperties(server);
            props.setProperty("allow-nether", String.valueOf(allowNetherCheckBox.isSelected()));
            props.setProperty("region-file-compression", String.valueOf(regionCompressionCombo.getSelectedItem()));
            props.setProperty("spawn-protection", String.valueOf(((Number) spawnProtectionSpinner.getValue()).intValue()));
            props.setProperty("max-world-size", String.valueOf(((Number) maxWorldSizeSpinner.getValue()).intValue()));
            WorldFilesService.writeServerProperties(server, props);
            gestorServidores.guardarServidor(server);
            actualizarConfiguracionMundo();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "No se han podido guardar los ajustes del mundo: " + ex.getMessage(),
                    "Configuración del mundo",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void configurarControlesAjustesMundo() {
        allowNetherCheckBox.setOpaque(false);
        allowNetherCheckBox.setFocusPainted(false);
        allowNetherCheckBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        allowNetherCheckBox.addActionListener(e -> updateWorldSettingsSaveButtonState());
        mundosCombo.setRoundRect(true);
        regionCompressionCombo.setOpaque(false);
        regionCompressionCombo.setFocusable(false);
        regionCompressionCombo.setRoundRect(true);
        regionCompressionCombo.setMaximumSize(new Dimension(180, 30));
        regionCompressionCombo.setPreferredSize(new Dimension(180, 30));
        regionCompressionCombo.addActionListener(e -> updateWorldSettingsSaveButtonState());
        spawnProtectionSpinner.setRoundRect(true);
        maxWorldSizeSpinner.setRoundRect(true);
        spawnProtectionSpinner.setMaximumSize(new Dimension(180, 30));
        maxWorldSizeSpinner.setMaximumSize(new Dimension(180, 30));
        spawnProtectionSpinner.addChangeListener(e -> updateWorldSettingsSaveButtonState());
        maxWorldSizeSpinner.addChangeListener(e -> updateWorldSettingsSaveButtonState());
        applyDefaultWorldSettingsSaveButtonStyle();
        guardarConfiguracionMundoButton.setEnabled(false);
    }

    private String formatearBoolean(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return Boolean.parseBoolean(value) ? "Si" : "No";
    }

    private String formatearEstructuras(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return Boolean.parseBoolean(value) ? "✓" : "✗";
    }

    private String formatearGeneratorSettings(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        return value;
    }

    private String construirResumenDificultad(WorldDataReader.WorldMetadata metadata) {
        String difficulty = metadata == null ? null : metadata.difficulty();
        if (difficulty == null || difficulty.isBlank()) {
            return "-";
        }
        String locked = metadata.difficultyLocked();
        if (locked == null || locked.isBlank()) {
            return difficulty;
        }
        return difficulty + " (bloqueada: " + locked + ")";
    }

    private String formatearSpawn(WorldDataReader.SpawnPoint spawnPoint) {
        if (spawnPoint == null) {
            return null;
        }
        return "X: " + spawnPoint.x() + " Y: " + spawnPoint.y() + " Z: " + spawnPoint.z();
    }

    private String construirResumenPacksIniciales(Properties metadata) {
        if (metadata == null) {
            return null;
        }
        int enabled = contarElementosCsv(metadata.getProperty("initial-enabled-packs"));
        int disabled = contarElementosCsv(metadata.getProperty("initial-disabled-packs"));
        if (enabled == 0 && disabled == 0) {
            return null;
        }
        return enabled + " iniciales activos / " + disabled + " iniciales desactivados";
    }

    private int contarElementosCsv(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return (int) java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .count();
    }

    private String construirTooltipPacks(Properties metadata) {
        if (metadata == null) {
            return null;
        }
        String enabled = metadata.getProperty("initial-enabled-packs");
        String disabled = metadata.getProperty("initial-disabled-packs");
        if ((enabled == null || enabled.isBlank()) && (disabled == null || disabled.isBlank())) {
            return null;
        }
        return "<html>Activos iniciales: " + escaparHtml(valorOPlaceholder(enabled))
                + "<br>Desactivados iniciales: " + escaparHtml(valorOPlaceholder(disabled)) + "</html>";
    }

    private void actualizarTooltipResumenLargo(JLabel label, String tooltip) {
        if (label == null) {
            return;
        }
        label.setToolTipText((tooltip == null || tooltip.isBlank()) ? null : tooltip);
    }

    private String escaparHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String primeroNoVacio(String primary, String fallback) {
        if (primary != null && !primary.isBlank() && !"-".equals(primary)) {
            return primary;
        }
        return fallback;
    }

    private String normalizarRegionCompression(String value) {
        if (value == null || value.isBlank()) {
            return REGION_FILE_COMPRESSION_OPTIONS[0];
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (String option : REGION_FILE_COMPRESSION_OPTIONS) {
            if (option.equals(normalized)) {
                return option;
            }
        }
        return REGION_FILE_COMPRESSION_OPTIONS[0];
    }

    private void markCurrentWorldSettingsAsPersisted() {
        persistedAllowNether = allowNetherCheckBox.isSelected();
        persistedRegionCompression = String.valueOf(regionCompressionCombo.getSelectedItem());
        persistedSpawnProtection = ((Number) spawnProtectionSpinner.getValue()).intValue();
        persistedMaxWorldSize = ((Number) maxWorldSizeSpinner.getValue()).intValue();
    }

    private boolean hasUnsavedWorldSettingsChanges() {
        Server server = gestorServidores.getServidorSeleccionado();
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return false;
        }
        return allowNetherCheckBox.isSelected() != persistedAllowNether
                || !Objects.equals(String.valueOf(regionCompressionCombo.getSelectedItem()), persistedRegionCompression)
                || ((Number) spawnProtectionSpinner.getValue()).intValue() != persistedSpawnProtection
                || ((Number) maxWorldSizeSpinner.getValue()).intValue() != persistedMaxWorldSize;
    }

    private void updateWorldSettingsSaveButtonState() {
        if (updatingWorldSettingsControls) {
            return;
        }
        boolean hasUnsavedChanges = hasUnsavedWorldSettingsChanges();
        applyDefaultWorldSettingsSaveButtonStyle();
        guardarConfiguracionMundoButton.setEnabled(hasUnsavedChanges);
        if (hasUnsavedChanges) {
            AppTheme.applyAccentButtonStyle(guardarConfiguracionMundoButton);
        }
        guardarConfiguracionMundoButton.repaint();
    }

    private void applyDefaultWorldSettingsSaveButtonStyle() {
        AppTheme.applyActionButtonStyle(guardarConfiguracionMundoButton);
    }

    private boolean leerBoolean(Properties props, String key, boolean defaultValue) {
        if (props == null || key == null) {
            return defaultValue;
        }
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private int leerEntero(Properties props, String key, int defaultValue, int minimum) {
        if (props == null || key == null) {
            return defaultValue;
        }
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(minimum, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private void actualizarDatosAlmacenamiento() {
        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null || mundo.getWorldDir() == null || mundo.getWorldDir().isBlank()) {
            pesoMundoValueLabel.setText("-");
            pesoStatsSavesValueLabel.setText("-");
            pesoTotalValueLabel.setText("-");
            return;
        }

        WorldStorageStats storageStats = WorldStorageAnalyzer.analyze(mundo);
        pesoMundoValueLabel.setText(formatearTamano(storageStats.worldBytes()));
        pesoStatsSavesValueLabel.setText(formatearTamano(storageStats.playerAndStatsBytes()));
        pesoTotalValueLabel.setText(formatearTamano(storageStats.totalBytes()));
    }

    private List<PreviewPlayerData> obtenerJugadoresRecientesDesdePlayerdata(Server server, World mundo) {
        return WorldPlayerDataService.findRecentPlayers(server, mundo, 4);
    }

    private List<PreviewPlayerPoint> obtenerJugadoresRecientesParaOverlay(World mundo) {
        return WorldPlayerDataService.findRecentPlayerPoints(gestorServidores.getServidorSeleccionado(), mundo, 4);
    }

    private World getMundoSeleccionadoOActivo() {
        World mundo = (World) mundosCombo.getSelectedItem();
        return mundo != null ? mundo : mundoActivoActual;
    }

    private String formatearTiempo(long ticks) {
        if (ticks <= 0) return "0 s";

        long totalSegundos = ticks / 20L;
        long horas = totalSegundos / 3600L;
        long minutos = (totalSegundos % 3600L) / 60L;
        long segundos = totalSegundos % 60L;

        if (horas > 0) return horas + " h " + minutos + " min";
        if (minutos > 0) return minutos + " min " + segundos + " s";
        return segundos + " s";
    }

    private void cambiarMundoSeleccionado() {
        World mundo = (World) mundosCombo.getSelectedItem();
        if (GestorMundos.cambiarMundo(gestorServidores.getServidorSeleccionado(), mundo, this)) {
            refrescarDatos();
            notificarCambioDeMundo();
        }
    }

    private void abrirDialogoNuevoMundo() {
        Server server = gestorServidores.getServidorSeleccionado();
        if (server == null) {
            JOptionPane.showMessageDialog(this, "No hay un servidor seleccionado.", "Generar mundo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        FlatTextField nombreField = new FlatTextField();
        nombreField.setText(MinecraftConstants.DEFAULT_WORLD_NAME + "-nuevo");
        nombreField.setRoundRect(true);
        FlatTextField semillaField = new FlatTextField();
        semillaField.setRoundRect(true);
        FlatComboBox<String> tipoBox = new FlatComboBox<>();
        tipoBox.setModel(new DefaultComboBoxModel<>(MinecraftConstants.WORLD_TYPES.toArray(String[]::new)));
        tipoBox.setRoundRect(true);
        FlatTextField generatorSettingsField = new FlatTextField();
        generatorSettingsField.setText("{}");
        generatorSettingsField.setRoundRect(true);
        FlatComboBox<String> gamemodeBox = new FlatComboBox<>();
        gamemodeBox.setModel(new DefaultComboBoxModel<>(MinecraftConstants.GAMEMODES.toArray(String[]::new)));
        gamemodeBox.setRoundRect(true);
        FlatComboBox<String> dificultadBox = new FlatComboBox<>();
        dificultadBox.setModel(new DefaultComboBoxModel<>(MinecraftConstants.DIFFICULTIES.toArray(String[]::new)));
        dificultadBox.setRoundRect(true);
        JCheckBox estructurasBox = new JCheckBox("Generar estructuras", true);
        JCheckBox hardcoreBox = new JCheckBox("Hardcore", false);
        JCheckBox netherBox = new JCheckBox("Permitir Nether", true);
        JCheckBox activarBox = new JCheckBox("Seleccionar al crear", true);
        FlatTextField initialEnabledPacksField = new FlatTextField();
        initialEnabledPacksField.setRoundRect(true);
        FlatTextField initialDisabledPacksField = new FlatTextField();
        initialDisabledPacksField.setRoundRect(true);
        FlatComboBox<String> regionFileCompressionBox = new FlatComboBox<>();
        regionFileCompressionBox.setModel(new DefaultComboBoxModel<>(REGION_FILE_COMPRESSION_OPTIONS));
        regionFileCompressionBox.setRoundRect(true);
        regionFileCompressionBox.setSelectedItem("deflate");
        instalarCampoGeneratorSettings(generatorSettingsField);

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 6));
        panel.add(new JLabel("Nombre del mundo"));
        panel.add(nombreField);
        panel.add(new JLabel("Semilla (opcional)"));
        panel.add(semillaField);
        panel.add(new JLabel("Tipo de mundo"));
        panel.add(tipoBox);
        panel.add(new JLabel("Ajustes del generador"));
        panel.add(generatorSettingsField);
        panel.add(new JLabel("Initial enabled packs"));
        panel.add(initialEnabledPacksField);
        panel.add(new JLabel("Initial disabled packs"));
        panel.add(initialDisabledPacksField);
        panel.add(new JLabel("Compresión de regiones"));
        panel.add(regionFileCompressionBox);
        panel.add(new JLabel("Modo de juego por defecto"));
        panel.add(gamemodeBox);
        panel.add(new JLabel("Dificultad"));
        panel.add(dificultadBox);
        panel.add(estructurasBox);
        panel.add(hardcoreBox);
        panel.add(netherBox);
        panel.add(activarBox);

        int result = JOptionPane.showConfirmDialog(this, panel, "Generar nuevo mundo", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        GestorMundos.WorldGenerationSettings settings = new GestorMundos.WorldGenerationSettings(
                nombreField.getText(),
                semillaField.getText(),
                String.valueOf(tipoBox.getSelectedItem()),
                normalizarGeneratorSettings(generatorSettingsField.getText()),
                estructurasBox.isSelected(),
                hardcoreBox.isSelected(),
                String.valueOf(gamemodeBox.getSelectedItem()),
                String.valueOf(dificultadBox.getSelectedItem()),
                netherBox.isSelected(),
                initialEnabledPacksField.getText(),
                initialDisabledPacksField.getText(),
                String.valueOf(regionFileCompressionBox.getSelectedItem()),
                activarBox.isSelected()
        );

        if (GestorMundos.generarNuevoMundo(server, settings, this)) {
            refrescarDatos();
            notificarCambioDeMundo();
        }
    }

    private void instalarCampoGeneratorSettings(JTextField field) {
        field.setColumns(20);
        field.setToolTipText("Solo puedes editar el contenido interno de las llaves.");
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new BracketLockedDocumentFilter());
        field.setNavigationFilter(new BracketLockedNavigationFilter(field));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                posicionarCursorDentroDeCorchetes(field);
            }
        });
        field.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                SwingUtilities.invokeLater(() -> posicionarCursorDentroDeCorchetes(field));
            }
        });
        SwingUtilities.invokeLater(() -> posicionarCursorDentroDeCorchetes(field));
    }

    private void posicionarCursorDentroDeCorchetes(JTextField field) {
        int max = Math.max(1, field.getDocument().getLength() - 1);
        int position = Math.max(1, Math.min(field.getCaretPosition(), max));
        field.setCaretPosition(position);
    }

    private String normalizarGeneratorSettings(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() >= 2 && normalized.startsWith("{") && normalized.endsWith("}")) {
            String inner = normalized.substring(1, normalized.length() - 1).trim();
            if (inner.isEmpty()) {
                return "";
            }
        }
        return normalized;
    }

    private void seleccionarMundoEnCombo(World objetivo) {
        if (objetivo == null) return;
        for (int i = 0; i < mundosCombo.getItemCount(); i++) {
            World candidato = mundosCombo.getItemAt(i);
            if (candidato != null && Objects.equals(candidato.getWorldName(), objetivo.getWorldName())) {
                mundosCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void notificarCambioDeMundo() {
        if (onWorldChanged != null) {
            onWorldChanged.run();
        }
    }

    private void setControlesActivos(boolean activos) {
        Server server = gestorServidores.getServidorSeleccionado();
        boolean hayServidor = activos && server != null;
        boolean hayMundos = hayServidor && mundosCombo.getItemCount() > 0;

        mundosCombo.setEnabled(hayMundos);
        refrescarButton.setEnabled(hayServidor);
        importarButton.setEnabled(hayServidor);
        exportarButton.setEnabled(hayMundos);
        generarButton.setEnabled(hayServidor);
        generarPreviewButton.setEnabled(hayMundos);
        previewMenuButton.setEnabled(hayMundos);
        allowNetherCheckBox.setEnabled(hayServidor);
        regionCompressionCombo.setEnabled(hayServidor);
        spawnProtectionSpinner.setEnabled(hayServidor);
        maxWorldSizeSpinner.setEnabled(hayServidor);
        if (!hayServidor) {
            guardarConfiguracionMundoButton.setEnabled(false);
        } else {
            updateWorldSettingsSaveButtonState();
        }
        updateUseWorldButtonState();
    }

    private void generarPreviewMundoSeleccionado() {
        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null) {
            JOptionPane.showMessageDialog(this, "No hay un mundo seleccionado.", "Generar vista previa", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (previewGenerationInProgress) {
            if (mismoContextoRenderEnCurso(gestorServidores.getServidorSeleccionado(), mundo)) {
                cancelarGeneracionPreview();
                return;
            }
            JOptionPane.showMessageDialog(
                    this,
                    construirMensajeRenderEnCurso(),
                    "Generar vista previa",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        long generationId = previewGenerationSequence.incrementAndGet();
        String worldDir = mundo.getWorldDir();
        Server selectedServer = gestorServidores.getServidorSeleccionado();
        String serverId = selectedServer == null ? null : selectedServer.getId();
        Path outputPath = WorldFilesService.getPreviewPath(mundo);
        boolean habiaPreviewAnterior = Files.isRegularFile(outputPath);
        previewDisponibleAntesDeGenerar = habiaPreviewAnterior;
        previewGenerationInProgress = true;
        previewGenerationServerId = serverId;
        previewGenerationServerName = selectedServer == null
                ? null
                : (selectedServer.getDisplayName() == null || selectedServer.getDisplayName().isBlank()
                ? selectedServer.getId()
                : selectedServer.getDisplayName());
        previewGenerationWorldDir = worldDir;
        previewGenerationWorldName = mundo.getWorldName();
        progresoRenderPreviewActual = null;
        previewRealtimeCanvas = null;
        previewImageLabel.clearImage();
        setPreviewProgressVisible(true);
        actualizarTextoBotonPreview();
        actualizarIndicadorRenderEnCurso();
        notificarCambioEstadoRenderCompartido();
        long previewStartNanos = System.nanoTime();
        MCARenderer renderer = new MCARenderer();
        WorldDataReader.SpawnPoint spawnPoint = WorldDataReader.getSpawnPoint(mundo);
        SwingWorker<PreviewGenerationResult, PreviewGenerationUpdate> worker = new SwingWorker<>() {
            @Override
            protected PreviewGenerationResult doInBackground() throws Exception {
                List<PreviewPlayerData> recentPlayers = obtenerJugadoresRecientesDesdePlayerdata(selectedServer, mundo);
                MCARenderer.WorldPoint centerPoint = previewPreferences.useWholeMap() ? null : resolverCentroRender(mundo, spawnPoint, recentPlayers);
                List<Path> regionesPreview = encontrarRegionesPreview(mundo, previewPreferences.useWholeMap(), spawnPoint, centerPoint);
                if (isCancelled()) {
                    return null;
                }
                if (regionesPreview.isEmpty()) {
                    return new PreviewGenerationResult(outputPath, true, null, null, null);
                }
                RenderWorldBounds targetRenderBounds = previewPreferences.useWholeMap()
                        ? null
                        : resolverLimitesRender(regionesPreview, centerPoint, previewPreferences.renderLimitPixels());
                RenderWorldBounds renderBounds = expandirLimitesRender(regionesPreview, targetRenderBounds, PREVIEW_RENDER_MARGIN_BLOCKS);
                MCARenderer.RenderOptions renderOptions = crearRenderOptionsPreview()
                        .withPreferSquareCrop(!previewPreferences.useWholeMap());
                if (renderBounds != null) {
                    renderOptions = renderOptions.withWorldBounds(
                            renderBounds.minBlockX(),
                            renderBounds.maxBlockX(),
                            renderBounds.minBlockZ(),
                            renderBounds.maxBlockZ()
                    );
                }
                MCARenderer.WorldRenderProgressListener progressListener = null;
                if (previewPreferences.renderRealtime()) {
                    progressListener = new MCARenderer.WorldRenderProgressListener() {
                        @Override
                        public void onCanvasInitialized(int width, int height, int defaultArgb, int totalRegions) {
                            if (!isCancelled()) {
                                String statusText = construirTextoProgresoRender(0, totalRegions, 0.0d);
                                publish(PreviewGenerationUpdate.initialization(width, height, defaultArgb, totalRegions, statusText));
                            }
                        }

                        @Override
                        public void onRegionComposed(int drawX, int drawY, BufferedImage regionImage, int regionsCompleted, int totalRegions, MCARenderer.RenderStats partialStats) {
                            if (!isCancelled()) {
                                double elapsedSeconds = (System.nanoTime() - previewStartNanos) / 1_000_000_000.0d;
                                publish(PreviewGenerationUpdate.region(
                                        drawX,
                                        drawY,
                                        regionImage,
                                        regionsCompleted,
                                        totalRegions,
                                        elapsedSeconds,
                                        partialStats,
                                        construirTextoProgresoRender(regionsCompleted, totalRegions, elapsedSeconds)
                                ));
                            }
                        }
                    };
                }
                MCARenderer.RenderedWorld renderedWorld = renderer.renderWorldWithMetadata(regionesPreview, renderOptions, null, progressListener);
                if (isCancelled()) {
                    return null;
                }
                CroppedPreview croppedPreview = (!previewPreferences.useWholeMap() && targetRenderBounds != null)
                        ? recortarPreviewAObjetivo(renderedWorld, targetRenderBounds)
                        : previewPreferences.useWholeMap()
                        ? new CroppedPreview(renderedWorld.image(), renderedWorld.originBlockX(), renderedWorld.originBlockZ())
                        : recortarPreviewPorLimite(renderedWorld, centerPoint, previewPreferences.renderLimitPixels());
                BufferedImage preview = croppedPreview.image();
                guardarPreview(preview, outputPath);
                PreviewOverlayData overlayData = null;
                try {
                    overlayData = new PreviewOverlayData(
                            croppedPreview.originBlockX(),
                            croppedPreview.originBlockZ(),
                            renderedWorld.pixelsPerBlock(),
                            spawnPoint == null ? null : new MCARenderer.WorldPoint(spawnPoint.x(), spawnPoint.z()),
                            recentPlayers.stream()
                                    .map(player -> new PreviewPlayerPoint(player.username(), player.point()))
                                    .toList()
                    );
                    WorldPreviewOverlayService.saveOverlayData(mundo, overlayData);
                } catch (RuntimeException ex) {
                    System.out.println("[PanelMundo] No se ha podido construir la metadata de overlay de la preview: " + ex.getMessage());
                    ex.printStackTrace(System.out);
                }
                return new PreviewGenerationResult(outputPath, false, preview, overlayData, renderedWorld.stats());
            }

            @Override
            protected void process(List<PreviewGenerationUpdate> updates) {
                boolean isCurrentWorker = previewGenerationWorker == this && previewGenerationSequence.get() == generationId;
                if (!isCurrentWorker || updates == null || updates.isEmpty()) {
                    return;
                }

                for (PreviewGenerationUpdate update : updates) {
                    if (update == null) {
                        continue;
                    }
                    if (update.initialization()) {
                        previewRealtimeCanvas = new BufferedImage(update.canvasWidth(), update.canvasHeight(), BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2 = previewRealtimeCanvas.createGraphics();
                        try {
                            g2.setColor(new Color(update.defaultArgb(), true));
                            g2.fillRect(0, 0, update.canvasWidth(), update.canvasHeight());
                        } finally {
                            g2.dispose();
                        }
                        previewImageLabel.setImage(previewRealtimeCanvas);
                        previewImageLabel.setOverlayData(null);
                        previewImageLabel.setChunkGridVisible(false);
                        previewImageLabel.setSpawnVisible(false);
                        previewImageLabel.setPlayersVisible(false);
                        progresoRenderPreviewActual = update.statusText();
                        continue;
                    }
                    if (previewRealtimeCanvas == null || update.regionImage() == null) {
                        continue;
                    }
                    Graphics2D g2 = previewRealtimeCanvas.createGraphics();
                    try {
                        g2.drawImage(update.regionImage(), update.drawX(), update.drawY(), null);
                    } finally {
                        g2.dispose();
                    }
                    previewImageLabel.setImage(previewRealtimeCanvas);
                    progresoRenderPreviewActual = update.statusText();
                }

                actualizarIndicadorRenderEnCurso();
                previewImageLabel.repaint();
            }

            @Override
            protected void done() {
                boolean isCurrentWorker = previewGenerationWorker == this && previewGenerationSequence.get() == generationId;
                if (isCurrentWorker) {
                    previewGenerationInProgress = false;
                    previewGenerationWorker = null;
                    limpiarContextoRenderEnCurso();
                    updateUseWorldButtonState();
                    setPreviewProgressVisible(false);
                    progresoRenderPreviewActual = null;
                    previewRealtimeCanvas = null;
                    actualizarTextoBotonPreview();
                    actualizarIndicadorRenderEnCurso();
                    notificarCambioEstadoRenderCompartido();
                }
                try {
                    if (isCancelled()) {
                        restaurarPreviewAnteriorSiExiste(habiaPreviewAnterior);
                        return;
                    }
                    PreviewGenerationResult result = get();
                    if (result == null) {
                        return;
                    }
                    if (result.sinRegiones()) {
                        restaurarPreviewAnteriorSiExiste(habiaPreviewAnterior);
                        JOptionPane.showMessageDialog(PanelMundo.this,
                                "El mundo no contiene archivos .mca en la carpeta region.",
                                "Generar vista previa",
                                JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    double elapsedSeconds = (System.nanoTime() - previewStartNanos) / 1_000_000_000.0d;
                    ultimoTiempoRenderPreview = formatearDuracionRender(elapsedSeconds);
                    ultimoDetalleTiempoRenderPreview = construirDetalleTiempoRender(result.stats(), elapsedSeconds);
                    System.out.println(String.format(Locale.ROOT, "[PanelMundo] Preview generated in %.2f seconds", elapsedSeconds));
                    actualizarIndicadorRenderEnCurso();
                    boolean sigueEnMismoMundo = mismoContextoSeleccionado(serverId, worldDir);
                    if (result.preview() != null && sigueEnMismoMundo) {
                        previewImageLabel.setImage(result.preview());
                        previewImageLabel.setOverlayData(result.overlayData());
                        previewImageLabel.setChunkGridVisible(previewPreferences.showChunkGrid());
                        previewImageLabel.setSpawnVisible(previewPreferences.showSpawn());
                        previewImageLabel.setPlayersVisible(previewPreferences.showPlayers());
                    } else if (sigueEnMismoMundo) {
                        actualizarPreviewSeleccionada();
                    }
                } catch (java.util.concurrent.CancellationException ex) {
                    restaurarPreviewAnteriorSiExiste(habiaPreviewAnterior);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    restaurarPreviewAnteriorSiExiste(habiaPreviewAnterior);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause.getMessage();
                    if (message == null || message.isBlank()) {
                        message = cause.getClass().getSimpleName();
                    }
                    restaurarPreviewAnteriorSiExiste(habiaPreviewAnterior);
                    JOptionPane.showMessageDialog(PanelMundo.this,
                            "No se ha podido generar la vista previa: " + message,
                            "Generar vista previa",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        previewGenerationWorker = worker;
        worker.execute();
    }

    private List<Path> encontrarRegionesPreview(World mundo, boolean generarTodo, WorldDataReader.SpawnPoint spawnPoint, MCARenderer.WorldPoint centerPoint) throws Exception {
        Path regionDir = WorldDataReader.getOverworldRegionDirectory(mundo);
        if (regionDir == null || !Files.isDirectory(regionDir)) {
            return List.of();
        }

        MCARenderer renderer = new MCARenderer();
        try (Stream<Path> stream = Files.list(regionDir)) {
            List<Path> regiones = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mca"))
                    .filter(this::esArchivoRegionValido)
                    .filter(path -> {
                        try {
                            return Files.size(path) > 0L;
                        } catch (IOException ex) {
                            return false;
                        }
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();

            if (generarTodo) {
                return seleccionarGrupoPrincipalRegiones(regiones, spawnPoint, centerPoint, previewPreferences.renderLimitPixels());
            }

            List<Path> regionesBase;
            if (centerPoint != null && previewPreferences.renderLimitPixels() > 0) {
                regionesBase = regiones;
            } else {
                List<Path> visibles = IntStream.range(0, regiones.size())
                        .filter(i -> {
                            try {
                                return tieneRegionVisibleCacheada(renderer, regiones.get(i));
                            } catch (IOException ex) {
                                return false;
                            }
                        })
                        .mapToObj(regiones::get)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .toList();
                regionesBase = visibles.isEmpty() ? regiones : visibles;
            }
            return seleccionarGrupoPrincipalRegiones(regionesBase, spawnPoint, centerPoint, previewPreferences.renderLimitPixels());
        }
    }

    private boolean tieneRegionVisibleCacheada(MCARenderer renderer, Path region) throws IOException {
        if (renderer == null || region == null) {
            return false;
        }

        Path normalized = region.toAbsolutePath().normalize();
        long size = Files.size(normalized);
        long lastModified = Files.getLastModifiedTime(normalized).toMillis();
        RegionVisibilityCacheEntry cached = REGION_VISIBILITY_CACHE.get(normalized);
        if (cached != null && cached.size() == size && cached.lastModified() == lastModified) {
            return cached.visible();
        }

        boolean visible = renderer.hasVisibleBlocks(normalized);
        REGION_VISIBILITY_CACHE.put(normalized, new RegionVisibilityCacheEntry(size, lastModified, visible));
        return visible;
    }

    private void cancelarGeneracionPreview() {
        SwingWorker<PreviewGenerationResult, PreviewGenerationUpdate> worker = previewGenerationWorker;
        if (worker != null) {
            worker.cancel(true);
        }
        previewGenerationInProgress = false;
        previewGenerationWorker = null;
        limpiarContextoRenderEnCurso();
        setPreviewProgressVisible(false);
        restaurarPreviewAnteriorSiExiste(previewDisponibleAntesDeGenerar);
        actualizarTextoBotonPreview();
        actualizarIndicadorRenderEnCurso();
        notificarCambioEstadoRenderCompartido();
    }

    private boolean mismoContextoSeleccionado(String expectedServerId, String expectedWorldDir) {
        Server currentServer = gestorServidores.getServidorSeleccionado();
        World currentWorld = getMundoSeleccionadoOActivo();
        String currentServerId = currentServer == null ? null : currentServer.getId();
        String currentWorldDir = currentWorld == null ? null : currentWorld.getWorldDir();
        return Objects.equals(expectedServerId, currentServerId) && Objects.equals(expectedWorldDir, currentWorldDir);
    }

    private boolean mismoContextoRenderEnCurso(Server server, World world) {
        String serverId = server == null ? null : server.getId();
        String worldDir = world == null ? null : world.getWorldDir();
        return Objects.equals(previewGenerationServerId, serverId) && Objects.equals(previewGenerationWorldDir, worldDir);
    }

    private void limpiarContextoRenderEnCurso() {
        previewGenerationServerId = null;
        previewGenerationServerName = null;
        previewGenerationWorldDir = null;
        previewGenerationWorldName = null;
    }

    private CroppedPreview recortarPreviewPorLimite(MCARenderer.RenderedWorld renderedWorld, MCARenderer.WorldPoint centerPoint, int maxBlocksPerSide) {
        if (renderedWorld == null || renderedWorld.image() == null || maxBlocksPerSide <= 0) {
            return new CroppedPreview(
                    renderedWorld == null ? null : renderedWorld.image(),
                    renderedWorld == null ? 0 : renderedWorld.originBlockX(),
                    renderedWorld == null ? 0 : renderedWorld.originBlockZ()
            );
        }

        BufferedImage image = renderedWorld.image();
        int pixelsPerBlock = Math.max(1, renderedWorld.pixelsPerBlock());
        int maxPixelSide = Math.max(1, maxBlocksPerSide * pixelsPerBlock);
        if (image.getWidth() <= maxPixelSide && image.getHeight() <= maxPixelSide) {
            return new CroppedPreview(image, renderedWorld.originBlockX(), renderedWorld.originBlockZ());
        }

        int cropWidth = Math.min(image.getWidth(), maxPixelSide);
        int cropHeight = Math.min(image.getHeight(), maxPixelSide);
        int desiredMinBlockX;
        int desiredMinBlockZ;
        if (centerPoint == null) {
            desiredMinBlockX = renderedWorld.originBlockX() + Math.max(0, ((image.getWidth() / pixelsPerBlock) - maxBlocksPerSide) / 2);
            desiredMinBlockZ = renderedWorld.originBlockZ() + Math.max(0, ((image.getHeight() / pixelsPerBlock) - maxBlocksPerSide) / 2);
        } else {
            int halfSpan = Math.max(1, maxBlocksPerSide / 2);
            desiredMinBlockX = centerPoint.x() - halfSpan;
            desiredMinBlockZ = centerPoint.z() - halfSpan;
        }

        int cropX = Math.max(0, Math.min(image.getWidth() - cropWidth, (desiredMinBlockX - renderedWorld.originBlockX()) * pixelsPerBlock));
        int cropY = Math.max(0, Math.min(image.getHeight() - cropHeight, (desiredMinBlockZ - renderedWorld.originBlockZ()) * pixelsPerBlock));

        BufferedImage cropped = image.getSubimage(cropX, cropY, cropWidth, cropHeight);
        int originBlockX = renderedWorld.originBlockX() + Math.floorDiv(cropX, pixelsPerBlock);
        int originBlockZ = renderedWorld.originBlockZ() + Math.floorDiv(cropY, pixelsPerBlock);
        return new CroppedPreview(cropped, originBlockX, originBlockZ);
    }

    private List<Path> seleccionarGrupoPrincipalRegiones(List<Path> regiones, WorldDataReader.SpawnPoint spawnPoint, MCARenderer.WorldPoint centerPoint, int limiteMaximoRender) {
        if (regiones == null || regiones.isEmpty()) {
            return List.of();
        }

        List<RegionPreviewCandidate> candidatas = new ArrayList<>();
        for (Path region : regiones) {
            RegionPreviewCandidate candidata = parsearRegionPreview(region);
            if (candidata != null) {
                candidatas.add(candidata);
            }
        }

        if (candidatas.isEmpty()) {
            return regiones;
        }

        if (previewPreferences.useWholeMap()) {
            return candidatas.stream()
                    .sorted(Comparator.comparing((RegionPreviewCandidate r) -> r.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .map(RegionPreviewCandidate::path)
                    .toList();
        }

        List<Path> regionesContinuas = seleccionarRegionesCuadradoContinuo(candidatas, centerPoint, limiteMaximoRender);
        if (!regionesContinuas.isEmpty()) {
            return regionesContinuas;
        }

        List<Path> regionesVentana = seleccionarRegionesParaVentana(candidatas, centerPoint, limiteMaximoRender);
        if (!regionesVentana.isEmpty()) {
            return regionesVentana;
        }

        Map<RegionKey, RegionPreviewCandidate> porClave = new HashMap<>();
        for (RegionPreviewCandidate candidata : candidatas) {
            porClave.put(candidata.key(), candidata);
        }

        List<List<RegionPreviewCandidate>> grupos = new ArrayList<>();
        Set<RegionKey> visitadas = new HashSet<>();
        for (RegionPreviewCandidate candidata : candidatas) {
            if (!visitadas.add(candidata.key())) {
                continue;
            }

            List<RegionPreviewCandidate> grupo = new ArrayList<>();
            ArrayDeque<RegionPreviewCandidate> cola = new ArrayDeque<>();
            cola.add(candidata);

            while (!cola.isEmpty()) {
                RegionPreviewCandidate actual = cola.removeFirst();
                grupo.add(actual);

                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        RegionKey vecina = new RegionKey(actual.regionX() + dx, actual.regionZ() + dz);
                        if (!visitadas.add(vecina)) {
                            continue;
                        }
                        RegionPreviewCandidate candidataVecina = porClave.get(vecina);
                        if (candidataVecina != null) {
                            cola.addLast(candidataVecina);
                        } else {
                            visitadas.remove(vecina);
                        }
                    }
                }
            }

            grupos.add(grupo);
        }

        List<RegionPreviewCandidate> mejorGrupo = elegirMejorGrupo(grupos, spawnPoint, centerPoint);
        List<RegionPreviewCandidate> regionesLimitadas = limitarGrupoParaRender(mejorGrupo, centerPoint, limiteMaximoRender);
        return regionesLimitadas.stream()
                .sorted(Comparator.comparing((RegionPreviewCandidate r) -> r.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .map(RegionPreviewCandidate::path)
                .toList();
    }

    private List<Path> seleccionarRegionesParaVentana(List<RegionPreviewCandidate> candidatas, MCARenderer.WorldPoint centerPoint, int limiteMaximoRender) {
        if (candidatas == null || candidatas.isEmpty() || centerPoint == null || limiteMaximoRender <= 0) {
            return List.of();
        }

        int halfSpan = Math.max(1, limiteMaximoRender / 2);
        int minBlockX = centerPoint.x() - halfSpan;
        int maxBlockX = minBlockX + limiteMaximoRender - 1;
        int minBlockZ = centerPoint.z() - halfSpan;
        int maxBlockZ = minBlockZ + limiteMaximoRender - 1;

        return candidatas.stream()
                .filter(candidate -> regionIntersectsWindow(candidate, minBlockX, maxBlockX, minBlockZ, maxBlockZ))
                .sorted(Comparator.comparing((RegionPreviewCandidate r) -> r.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .map(RegionPreviewCandidate::path)
                .toList();
    }

    private List<Path> seleccionarRegionesEnEspiral(List<RegionPreviewCandidate> candidatas, MCARenderer.WorldPoint centerPoint, int limiteMaximoRender) {
        if (candidatas == null || candidatas.isEmpty() || centerPoint == null || limiteMaximoRender <= 0) {
            return List.of();
        }

        int maxRegionsPerSide = Math.max(1, (int) Math.ceil((double) limiteMaximoRender / MCARenderer.REGION_BLOCK_SIDE));
        int maxRadius = Math.max(0, maxRegionsPerSide);
        RegionKey centerRegion = new RegionKey(
                Math.floorDiv(centerPoint.x(), MCARenderer.REGION_BLOCK_SIDE),
                Math.floorDiv(centerPoint.z(), MCARenderer.REGION_BLOCK_SIDE)
        );

        Map<RegionKey, RegionPreviewCandidate> porClave = new HashMap<>();
        for (RegionPreviewCandidate candidata : candidatas) {
            porClave.put(candidata.key(), candidata);
        }

        LinkedHashMap<RegionKey, RegionPreviewCandidate> seleccionadas = new LinkedHashMap<>();
        int emptyRings = 0;
        for (int radius = 0; radius <= maxRadius; radius++) {
            List<RegionKey> ringKeys = construirAnilloEspiral(centerRegion, radius);
            boolean ringHasContent = false;
            for (RegionKey key : ringKeys) {
                RegionPreviewCandidate candidata = porClave.get(key);
                if (candidata != null) {
                    seleccionadas.putIfAbsent(key, candidata);
                    ringHasContent = true;
                }
            }
            if (ringHasContent) {
                emptyRings = 0;
            } else {
                emptyRings++;
                if (emptyRings >= EMPTY_SPIRAL_RINGS_TO_STOP) {
                    break;
                }
            }
        }

        if (seleccionadas.isEmpty()) {
            return List.of();
        }

        return seleccionadas.values().stream()
                .sorted(Comparator.comparing((RegionPreviewCandidate r) -> r.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .map(RegionPreviewCandidate::path)
                .toList();
    }

    private List<Path> seleccionarRegionesCuadradoContinuo(List<RegionPreviewCandidate> candidatas, MCARenderer.WorldPoint centerPoint, int limiteMaximoRender) {
        if (candidatas == null || candidatas.isEmpty() || centerPoint == null || limiteMaximoRender <= 0) {
            return List.of();
        }

        Map<RegionKey, RegionPreviewCandidate> porClave = new HashMap<>();
        for (RegionPreviewCandidate candidata : candidatas) {
            porClave.put(candidata.key(), candidata);
        }

        int centerRegionX = Math.floorDiv(centerPoint.x(), MCARenderer.REGION_BLOCK_SIDE);
        int centerRegionZ = Math.floorDiv(centerPoint.z(), MCARenderer.REGION_BLOCK_SIDE);
        int requestedMinBlockX = centerPoint.x() - (limiteMaximoRender / 2);
        int requestedMaxBlockX = requestedMinBlockX + limiteMaximoRender - 1;
        int requestedMinBlockZ = centerPoint.z() - (limiteMaximoRender / 2);
        int requestedMaxBlockZ = requestedMinBlockZ + limiteMaximoRender - 1;
        int requestedMinRegionX = Math.floorDiv(requestedMinBlockX, MCARenderer.REGION_BLOCK_SIDE);
        int requestedMaxRegionX = Math.floorDiv(requestedMaxBlockX, MCARenderer.REGION_BLOCK_SIDE);
        int requestedMinRegionZ = Math.floorDiv(requestedMinBlockZ, MCARenderer.REGION_BLOCK_SIDE);
        int requestedMaxRegionZ = Math.floorDiv(requestedMaxBlockZ, MCARenderer.REGION_BLOCK_SIDE);
        int maxRadius = Math.max(
                Math.max(Math.abs(requestedMinRegionX - centerRegionX), Math.abs(requestedMaxRegionX - centerRegionX)),
                Math.max(Math.abs(requestedMinRegionZ - centerRegionZ), Math.abs(requestedMaxRegionZ - centerRegionZ))
        );

        LinkedHashMap<RegionKey, RegionPreviewCandidate> seleccionadas = new LinkedHashMap<>();
        for (int radius = 0; radius <= maxRadius; radius++) {
            List<RegionKey> ringKeys = construirAnilloEspiral(new RegionKey(centerRegionX, centerRegionZ), radius);
            boolean ringCompleto = true;
            for (RegionKey key : ringKeys) {
                if (key.regionX() < requestedMinRegionX || key.regionX() > requestedMaxRegionX
                        || key.regionZ() < requestedMinRegionZ || key.regionZ() > requestedMaxRegionZ) {
                    continue;
                }
                RegionPreviewCandidate candidata = porClave.get(key);
                if (candidata == null) {
                    ringCompleto = false;
                    break;
                }
            }
            if (!ringCompleto) {
                break;
            }
            for (RegionKey key : ringKeys) {
                if (key.regionX() < requestedMinRegionX || key.regionX() > requestedMaxRegionX
                        || key.regionZ() < requestedMinRegionZ || key.regionZ() > requestedMaxRegionZ) {
                    continue;
                }
                RegionPreviewCandidate candidata = porClave.get(key);
                if (candidata != null) {
                    seleccionadas.putIfAbsent(key, candidata);
                }
            }
        }

        if (seleccionadas.isEmpty()) {
            return List.of();
        }

        return seleccionadas.values().stream()
                .map(RegionPreviewCandidate::path)
                .toList();
    }

    private List<RegionKey> construirAnilloEspiral(RegionKey centerRegion, int radius) {
        if (centerRegion == null) {
            return List.of();
        }
        if (radius <= 0) {
            return List.of(centerRegion);
        }

        List<RegionKey> ring = new ArrayList<>(radius * 8);
        int minX = centerRegion.regionX() - radius;
        int maxX = centerRegion.regionX() + radius;
        int minZ = centerRegion.regionZ() - radius;
        int maxZ = centerRegion.regionZ() + radius;

        for (int x = minX; x <= maxX; x++) {
            ring.add(new RegionKey(x, minZ));
        }
        for (int z = minZ + 1; z <= maxZ; z++) {
            ring.add(new RegionKey(maxX, z));
        }
        for (int x = maxX - 1; x >= minX; x--) {
            ring.add(new RegionKey(x, maxZ));
        }
        for (int z = maxZ - 1; z > minZ; z--) {
            ring.add(new RegionKey(minX, z));
        }
        return ring;
    }

    private RenderWorldBounds resolverLimitesRender(List<Path> regionesPreview, MCARenderer.WorldPoint centerPoint, int limiteMaximoRender) {
        if (regionesPreview == null || regionesPreview.isEmpty() || centerPoint == null || limiteMaximoRender <= 0) {
            return null;
        }

        int minRegionX = Integer.MAX_VALUE;
        int maxRegionX = Integer.MIN_VALUE;
        int minRegionZ = Integer.MAX_VALUE;
        int maxRegionZ = Integer.MIN_VALUE;
        for (Path region : regionesPreview) {
            RegionPreviewCandidate candidate = parsearRegionPreview(region);
            if (candidate == null) {
                continue;
            }
            minRegionX = Math.min(minRegionX, candidate.regionX());
            maxRegionX = Math.max(maxRegionX, candidate.regionX());
            minRegionZ = Math.min(minRegionZ, candidate.regionZ());
            maxRegionZ = Math.max(maxRegionZ, candidate.regionZ());
        }

        if (minRegionX == Integer.MAX_VALUE) {
            return null;
        }

        int availableMinBlockX = minRegionX * MCARenderer.REGION_BLOCK_SIDE;
        int availableMaxBlockX = ((maxRegionX + 1) * MCARenderer.REGION_BLOCK_SIDE) - 1;
        int availableMinBlockZ = minRegionZ * MCARenderer.REGION_BLOCK_SIDE;
        int availableMaxBlockZ = ((maxRegionZ + 1) * MCARenderer.REGION_BLOCK_SIDE) - 1;

        int span = Math.max(1, limiteMaximoRender);
        int requestedMinBlockX = centerPoint.x() - (span / 2);
        int requestedMaxBlockX = requestedMinBlockX + span - 1;
        int requestedMinBlockZ = centerPoint.z() - (span / 2);
        int requestedMaxBlockZ = requestedMinBlockZ + span - 1;

        int minBlockX = Math.max(availableMinBlockX, requestedMinBlockX);
        int maxBlockX = Math.min(availableMaxBlockX, requestedMaxBlockX);
        int minBlockZ = Math.max(availableMinBlockZ, requestedMinBlockZ);
        int maxBlockZ = Math.min(availableMaxBlockZ, requestedMaxBlockZ);
        if (minBlockX > maxBlockX || minBlockZ > maxBlockZ) {
            return null;
        }

        return new RenderWorldBounds(minBlockX, maxBlockX, minBlockZ, maxBlockZ);
    }

    private RenderWorldBounds expandirLimitesRender(List<Path> regionesPreview, RenderWorldBounds baseBounds, int marginBlocks) {
        if (regionesPreview == null || regionesPreview.isEmpty() || baseBounds == null || marginBlocks <= 0) {
            return baseBounds;
        }

        int minRegionX = Integer.MAX_VALUE;
        int maxRegionX = Integer.MIN_VALUE;
        int minRegionZ = Integer.MAX_VALUE;
        int maxRegionZ = Integer.MIN_VALUE;
        for (Path region : regionesPreview) {
            RegionPreviewCandidate candidate = parsearRegionPreview(region);
            if (candidate == null) {
                continue;
            }
            minRegionX = Math.min(minRegionX, candidate.regionX());
            maxRegionX = Math.max(maxRegionX, candidate.regionX());
            minRegionZ = Math.min(minRegionZ, candidate.regionZ());
            maxRegionZ = Math.max(maxRegionZ, candidate.regionZ());
        }
        if (minRegionX == Integer.MAX_VALUE) {
            return baseBounds;
        }

        int availableMinBlockX = minRegionX * MCARenderer.REGION_BLOCK_SIDE;
        int availableMaxBlockX = ((maxRegionX + 1) * MCARenderer.REGION_BLOCK_SIDE) - 1;
        int availableMinBlockZ = minRegionZ * MCARenderer.REGION_BLOCK_SIDE;
        int availableMaxBlockZ = ((maxRegionZ + 1) * MCARenderer.REGION_BLOCK_SIDE) - 1;

        return new RenderWorldBounds(
                Math.max(availableMinBlockX, baseBounds.minBlockX() - marginBlocks),
                Math.min(availableMaxBlockX, baseBounds.maxBlockX() + marginBlocks),
                Math.max(availableMinBlockZ, baseBounds.minBlockZ() - marginBlocks),
                Math.min(availableMaxBlockZ, baseBounds.maxBlockZ() + marginBlocks)
        );
    }

    private CroppedPreview recortarPreviewAObjetivo(MCARenderer.RenderedWorld renderedWorld, RenderWorldBounds targetBounds) {
        if (renderedWorld == null || renderedWorld.image() == null || targetBounds == null) {
            return new CroppedPreview(
                    renderedWorld == null ? null : renderedWorld.image(),
                    renderedWorld == null ? 0 : renderedWorld.originBlockX(),
                    renderedWorld == null ? 0 : renderedWorld.originBlockZ()
            );
        }

        BufferedImage image = renderedWorld.image();
        int pixelsPerBlock = Math.max(1, renderedWorld.pixelsPerBlock());
        int cropX = Math.max(0, (targetBounds.minBlockX() - renderedWorld.originBlockX()) * pixelsPerBlock);
        int cropY = Math.max(0, (targetBounds.minBlockZ() - renderedWorld.originBlockZ()) * pixelsPerBlock);
        int cropWidth = Math.min(image.getWidth() - cropX, ((targetBounds.maxBlockX() - targetBounds.minBlockX()) + 1) * pixelsPerBlock);
        int cropHeight = Math.min(image.getHeight() - cropY, ((targetBounds.maxBlockZ() - targetBounds.minBlockZ()) + 1) * pixelsPerBlock);
        if (cropWidth <= 0 || cropHeight <= 0) {
            return new CroppedPreview(image, renderedWorld.originBlockX(), renderedWorld.originBlockZ());
        }

        return new CroppedPreview(
                image.getSubimage(cropX, cropY, cropWidth, cropHeight),
                targetBounds.minBlockX(),
                targetBounds.minBlockZ()
        );
    }

    private boolean regionIntersectsWindow(RegionPreviewCandidate candidate, int minBlockX, int maxBlockX, int minBlockZ, int maxBlockZ) {
        if (candidate == null) {
            return false;
        }
        int regionMinX = candidate.regionX() * MCARenderer.REGION_BLOCK_SIDE;
        int regionMaxX = regionMinX + MCARenderer.REGION_BLOCK_SIDE - 1;
        int regionMinZ = candidate.regionZ() * MCARenderer.REGION_BLOCK_SIDE;
        int regionMaxZ = regionMinZ + MCARenderer.REGION_BLOCK_SIDE - 1;
        return regionMaxX >= minBlockX
                && regionMinX <= maxBlockX
                && regionMaxZ >= minBlockZ
                && regionMinZ <= maxBlockZ;
    }

    private List<RegionPreviewCandidate> limitarGrupoParaRender(List<RegionPreviewCandidate> grupo, MCARenderer.WorldPoint centerPoint, int limiteMaximoRender) {
        if (grupo == null || grupo.isEmpty() || limiteMaximoRender <= 0) {
            return grupo == null ? List.of() : grupo;
        }

        int maxRegionsPerSide = Math.max(1, limiteMaximoRender / MCARenderer.REGION_BLOCK_SIDE);
        if (maxRegionsPerSide == Integer.MAX_VALUE) {
            return grupo;
        }

        int minRegionX = grupo.stream().mapToInt(RegionPreviewCandidate::regionX).min().orElse(0);
        int maxRegionX = grupo.stream().mapToInt(RegionPreviewCandidate::regionX).max().orElse(0);
        int minRegionZ = grupo.stream().mapToInt(RegionPreviewCandidate::regionZ).min().orElse(0);
        int maxRegionZ = grupo.stream().mapToInt(RegionPreviewCandidate::regionZ).max().orElse(0);
        if ((maxRegionX - minRegionX + 1) <= maxRegionsPerSide && (maxRegionZ - minRegionZ + 1) <= maxRegionsPerSide) {
            return grupo;
        }

        RegionKey centerRegion = centerPoint == null ? null : new RegionKey(Math.floorDiv(centerPoint.x(), MCARenderer.REGION_BLOCK_SIDE), Math.floorDiv(centerPoint.z(), MCARenderer.REGION_BLOCK_SIDE));
        double anchorX = centerRegion != null ? centerRegion.regionX() : grupo.stream().mapToInt(RegionPreviewCandidate::regionX).average().orElse(0d);
        double anchorZ = centerRegion != null ? centerRegion.regionZ() : grupo.stream().mapToInt(RegionPreviewCandidate::regionZ).average().orElse(0d);

        List<Integer> startXs = construirIniciosVentana(minRegionX, maxRegionX, maxRegionsPerSide, anchorX);
        List<Integer> startZs = construirIniciosVentana(minRegionZ, maxRegionZ, maxRegionsPerSide, anchorZ);

        WindowSelection mejorSeleccion = null;
        for (int startX : startXs) {
            int endX = startX + maxRegionsPerSide - 1;
            for (int startZ : startZs) {
                int endZ = startZ + maxRegionsPerSide - 1;
                List<RegionPreviewCandidate> subset = grupo.stream()
                        .filter(region -> region.regionX() >= startX && region.regionX() <= endX
                                && region.regionZ() >= startZ && region.regionZ() <= endZ)
                        .toList();
                if (subset.isEmpty()) {
                    continue;
                }
                WindowSelection actual = new WindowSelection(startX, startZ, endX, endZ, subset, centerRegion, anchorX, anchorZ);
                if (mejorSeleccion == null || actual.compareTo(mejorSeleccion) < 0) {
                    mejorSeleccion = actual;
                }
            }
        }

        return mejorSeleccion == null ? grupo : mejorSeleccion.regiones();
    }

    private List<Integer> construirIniciosVentana(int minRegion, int maxRegion, int maxRegionsPerSide, double anchor) {
        int span = maxRegion - minRegion + 1;
        if (span <= maxRegionsPerSide) {
            return List.of(minRegion);
        }

        int ultimoInicio = maxRegion - maxRegionsPerSide + 1;
        List<Integer> inicios = new ArrayList<>();
        int sugerido = (int) Math.round(anchor - ((maxRegionsPerSide - 1) / 2.0d));
        sugerido = Math.max(minRegion, Math.min(ultimoInicio, sugerido));
        inicios.add(sugerido);

        for (int current = minRegion; current <= ultimoInicio; current++) {
            if (!inicios.contains(current)) {
                inicios.add(current);
            }
        }
        return inicios;
    }

    private List<RegionPreviewCandidate> elegirMejorGrupo(List<List<RegionPreviewCandidate>> grupos, WorldDataReader.SpawnPoint spawnPoint, MCARenderer.WorldPoint centerPoint) {
        if (grupos == null || grupos.isEmpty()) {
            return List.of();
        }
        if (grupos.size() == 1) {
            return grupos.get(0);
        }

        MCARenderer.WorldPoint effectiveCenter = centerPoint != null
                ? centerPoint
                : (spawnPoint == null ? null : new MCARenderer.WorldPoint(spawnPoint.x(), spawnPoint.z()));
        RegionKey spawnRegion = effectiveCenter == null ? null : new RegionKey(Math.floorDiv(effectiveCenter.x(), 512), Math.floorDiv(effectiveCenter.z(), 512));
        List<RegionPreviewCandidate> mejor = null;
        for (List<RegionPreviewCandidate> grupo : grupos) {
            if (mejor == null || compararGrupos(grupo, mejor, spawnRegion) < 0) {
                mejor = grupo;
            }
        }
        return mejor == null ? grupos.get(0) : mejor;
    }

    private int compararGrupos(List<RegionPreviewCandidate> a, List<RegionPreviewCandidate> b, RegionKey spawnRegion) {
        boolean aTieneSpawn = contieneRegion(a, spawnRegion);
        boolean bTieneSpawn = contieneRegion(b, spawnRegion);
        if (aTieneSpawn != bTieneSpawn) {
            return aTieneSpawn ? -1 : 1;
        }

        int bySize = Integer.compare(b.size(), a.size());
        if (bySize != 0) {
            return bySize;
        }

        int byWeight = Long.compare(pesoGrupo(b), pesoGrupo(a));
        if (byWeight != 0) {
            return byWeight;
        }

        if (spawnRegion != null) {
            int byDistance = Integer.compare(distanciaGrupoAlSpawn(a, spawnRegion), distanciaGrupoAlSpawn(b, spawnRegion));
            if (byDistance != 0) {
                return byDistance;
            }
        }

        RegionKey minA = minRegionKey(a);
        RegionKey minB = minRegionKey(b);
        int byZ = Integer.compare(minA.regionZ(), minB.regionZ());
        if (byZ != 0) {
            return byZ;
        }
        return Integer.compare(minA.regionX(), minB.regionX());
    }

    private boolean contieneRegion(List<RegionPreviewCandidate> grupo, RegionKey objetivo) {
        if (grupo == null || grupo.isEmpty() || objetivo == null) {
            return false;
        }
        for (RegionPreviewCandidate candidata : grupo) {
            if (candidata.key().equals(objetivo)) {
                return true;
            }
        }
        return false;
    }

    private long pesoGrupo(List<RegionPreviewCandidate> grupo) {
        long total = 0L;
        for (RegionPreviewCandidate candidata : grupo) {
            total += candidata.size();
        }
        return total;
    }

    private int distanciaGrupoAlSpawn(List<RegionPreviewCandidate> grupo, RegionKey spawnRegion) {
        int mejor = Integer.MAX_VALUE;
        for (RegionPreviewCandidate candidata : grupo) {
            int distancia = Math.abs(candidata.regionX() - spawnRegion.regionX()) + Math.abs(candidata.regionZ() - spawnRegion.regionZ());
            if (distancia < mejor) {
                mejor = distancia;
            }
        }
        return mejor;
    }

    private RegionKey minRegionKey(List<RegionPreviewCandidate> grupo) {
        RegionPreviewCandidate mejor = grupo.get(0);
        for (RegionPreviewCandidate candidata : grupo) {
            if (candidata.regionZ() < mejor.regionZ()
                    || (candidata.regionZ() == mejor.regionZ() && candidata.regionX() < mejor.regionX())) {
                mejor = candidata;
            }
        }
        return mejor.key();
    }

    private RegionPreviewCandidate parsearRegionPreview(Path region) {
        if (region == null || region.getFileName() == null) {
            return null;
        }
        Matcher matcher = REGION_FILE_PATTERN.matcher(region.getFileName().toString());
        if (!matcher.matches()) {
            return null;
        }

        try {
            int regionX = Integer.parseInt(matcher.group(1));
            int regionZ = Integer.parseInt(matcher.group(2));
            long size = Files.size(region);
            return new RegionPreviewCandidate(region, regionX, regionZ, size);
        } catch (IOException | NumberFormatException ex) {
            return null;
        }
    }

    private boolean esArchivoRegionValido(Path region) {
        if (region == null || region.getFileName() == null) {
            return false;
        }
        Matcher matcher = REGION_FILE_PATTERN.matcher(region.getFileName().toString());
        if (!matcher.matches()) {
            return false;
        }
        try {
            Integer.parseInt(matcher.group(1));
            Integer.parseInt(matcher.group(2));
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private record RegionPreviewCandidate(Path path, int regionX, int regionZ, long size) {
        private RegionKey key() {
            return new RegionKey(regionX, regionZ);
        }
    }

    private record RegionKey(int regionX, int regionZ) {}

    private record PreviewRenderLimitOption(String label, int maxPixels) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record PreviewCenterOption(String id, String label, MCARenderer.WorldPoint point) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record WindowSelection(int startX, int startZ, int endX, int endZ, List<RegionPreviewCandidate> regiones,
                                   RegionKey spawnRegion, double anchorX, double anchorZ) {
        private int compareTo(WindowSelection other) {
            boolean thisHasSpawn = contieneSpawn();
            boolean otherHasSpawn = other.contieneSpawn();
            if (thisHasSpawn != otherHasSpawn) {
                return thisHasSpawn ? -1 : 1;
            }

            int byCount = Integer.compare(other.regiones.size(), regiones.size());
            if (byCount != 0) {
                return byCount;
            }

            int byWeight = Long.compare(other.pesoTotal(), pesoTotal());
            if (byWeight != 0) {
                return byWeight;
            }

            int byAnchorDistance = Double.compare(distanciaAlAncla(), other.distanciaAlAncla());
            if (byAnchorDistance != 0) {
                return byAnchorDistance;
            }

            int byZ = Integer.compare(startZ, other.startZ);
            if (byZ != 0) {
                return byZ;
            }
            return Integer.compare(startX, other.startX);
        }

        private boolean contieneSpawn() {
            return spawnRegion != null
                    && spawnRegion.regionX() >= startX && spawnRegion.regionX() <= endX
                    && spawnRegion.regionZ() >= startZ && spawnRegion.regionZ() <= endZ;
        }

        private long pesoTotal() {
            long total = 0L;
            for (RegionPreviewCandidate region : regiones) {
                total += region.size();
            }
            return total;
        }

        private double distanciaAlAncla() {
            double centerX = startX + ((endX - startX) / 2.0d);
            double centerZ = startZ + ((endZ - startZ) / 2.0d);
            return Math.abs(centerX - anchorX) + Math.abs(centerZ - anchorZ);
        }
    }
    private record RegionVisibilityCacheEntry(long size, long lastModified, boolean visible) {}

    private void guardarPreview(BufferedImage image, Path outputPath) throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ImageIO.write(image, "png", outputPath.toFile());
        actualizarTextoBotonPreview();
    }

    private void actualizarPreviewSeleccionada() {
        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null) {
            mostrarTextoPreview("La vista previa todavía no existe.");
            return;
        }

        if (previewGenerationInProgress && mismoContextoRenderEnCurso(gestorServidores.getServidorSeleccionado(), mundo)) {
            previewImageLabel.clearImage();
            previewImageLabel.setOverlayData(null);
            previewImageLabel.setChunkGridVisible(false);
            previewImageLabel.setSpawnVisible(false);
            previewImageLabel.setPlayersVisible(false);
            return;
        }

        Path previewPath = WorldFilesService.getPreviewPath(mundo);
        if (!Files.isRegularFile(previewPath)) {
            mostrarTextoPreview("La vista previa todavía no existe.");
            return;
        }

        try {
            BufferedImage image = ImageIO.read(previewPath.toFile());
            if (image == null) {
                mostrarTextoPreview("No se ha podido leer la vista previa.");
                return;
            }
            PreviewOverlayData overlayData = WorldPreviewOverlayService.loadOverlayData(mundo);
            previewImageLabel.setImage(image);
            previewImageLabel.setOverlayData(overlayData);
            previewImageLabel.setChunkGridVisible(previewPreferences.showChunkGrid());
            previewImageLabel.setSpawnVisible(previewPreferences.showSpawn());
            previewImageLabel.setPlayersVisible(previewPreferences.showPlayers());
            if ((previewPreferences.showChunkGrid() || previewPreferences.showSpawn() || previewPreferences.showPlayers()) && overlayData == null) {
                System.out.println("[PanelMundo] Overlay metadata missing for preview. Regenerate the preview once to enable local overlays.");
                JOptionPane.showMessageDialog(
                        this,
                        "Esta vista previa no tiene datos de superposición.\nRegenera la vista previa una vez para poder mostrar el spawn o los límites de chunks.",
                        "Superposiciones no disponibles",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        } catch (IOException ex) {
            mostrarTextoPreview("No se ha podido leer la vista previa.");
        }
    }

    private void mostrarTextoPreview(String texto) {
        previewImageLabel.setMessage(texto);
        previewImageLabel.setOverlayData(null);
        previewImageLabel.setChunkGridVisible(false);
        previewImageLabel.setSpawnVisible(false);
        previewImageLabel.setPlayersVisible(false);
    }

    private void restaurarPreviewAnteriorSiExiste(boolean habiaPreviewAnterior) {
        if (habiaPreviewAnterior) {
            actualizarPreviewSeleccionada();
            return;
        }
        mostrarTextoPreview("La vista previa todavía no existe.");
    }

    private void sincronizarEstadoRenderCompartido() {
        World mundo = getMundoSeleccionadoOActivo();
        boolean mostrarProgreso = previewGenerationInProgress && mismoContextoRenderEnCurso(gestorServidores.getServidorSeleccionado(), mundo);
        setPreviewProgressVisible(mostrarProgreso);
        actualizarTextoBotonPreview();
        actualizarIndicadorRenderEnCurso();
    }

    private static void notificarCambioEstadoRenderCompartido() {
        SwingUtilities.invokeLater(() -> {
            java.util.List<PanelMundo> instancias = new ArrayList<>(INSTANCIAS_ACTIVAS);
            for (PanelMundo instancia : instancias) {
                if (instancia != null) {
                    instancia.sincronizarEstadoRenderCompartido();
                }
            }
        });
    }

    private void setPreviewProgressVisible(boolean visible) {
        previewSpinner.setVisible(visible);
        previewSpinner.setRunning(visible);
        Container parent = previewSpinner.getParent();
        if (parent != null) {
            parent.setVisible(visible);
            parent.revalidate();
            parent.repaint();
        }
    }

    private void instalarMenuContextualPreview() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copiarItem = new JMenuItem("Copiar");
        JMenuItem verArchivoItem = new JMenuItem("Ver archivo");
        JMenuItem guardarComoItem = new JMenuItem("Guardar como");
        copiarItem.addActionListener(e -> copiarPreviewAlPortapapeles());
        verArchivoItem.addActionListener(e -> abrirPreviewEnExplorador());
        guardarComoItem.addActionListener(e -> guardarPreviewComo());
        menu.add(copiarItem);
        menu.add(verArchivoItem);
        menu.add(guardarComoItem);

        MouseAdapter popupMouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                mostrarPopupSiCorresponde(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                mostrarPopupSiCorresponde(e);
            }

            private void mostrarPopupSiCorresponde(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                boolean existePreview = existePreviewSeleccionada();
                copiarItem.setEnabled(existePreview);
                verArchivoItem.setEnabled(existePreview);
                guardarComoItem.setEnabled(existePreview);
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        };
        previewImageLabel.addMouseListener(popupMouse);
    }

    private boolean existePreviewSeleccionada() {
        World mundo = getMundoSeleccionadoOActivo();
        return mundo != null && Files.isRegularFile(WorldFilesService.getPreviewPath(mundo));
    }

    private void copiarPreviewAlPortapapeles() {
        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null) {
            JOptionPane.showMessageDialog(this, "No hay un mundo seleccionado.", "Copiar", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path previewPath = WorldFilesService.getPreviewPath(mundo);
        if (!Files.isRegularFile(previewPath)) {
            JOptionPane.showMessageDialog(this, "La vista previa todavía no existe.", "Copiar", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            BufferedImage image = ImageIO.read(previewPath.toFile());
            if (image == null) {
                throw new FileNotFoundException("No se ha podido leer la vista previa.");
            }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageSelection(image), null);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se ha podido copiar la vista previa: " + ex.getMessage(), "Copiar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void abrirPreviewEnExplorador() {
        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null) {
            JOptionPane.showMessageDialog(this, "No hay un mundo seleccionado.", "Ver archivo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path previewPath = WorldFilesService.getPreviewPath(mundo);
        if (!Files.isRegularFile(previewPath)) {
            JOptionPane.showMessageDialog(this, "La vista previa todavía no existe.", "Ver archivo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                new ProcessBuilder("explorer.exe", "/select,", previewPath.toAbsolutePath().toString()).start();
                return;
            }
            Desktop.getDesktop().open(previewPath.toAbsolutePath().getParent().toFile());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se ha podido abrir el Explorador: " + ex.getMessage(), "Ver archivo", JOptionPane.ERROR_MESSAGE);
        } catch (UnsupportedOperationException ex) {
            JOptionPane.showMessageDialog(this, "Este sistema no soporta abrir archivos desde Java.", "Ver archivo", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void guardarPreviewComo() {
        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null) {
            JOptionPane.showMessageDialog(this, "No hay un mundo seleccionado.", "Guardar como", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path previewPath = WorldFilesService.getPreviewPath(mundo);
        if (!Files.isRegularFile(previewPath)) {
            JOptionPane.showMessageDialog(this, "La vista previa todavía no existe.", "Guardar como", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar vista previa como");
        java.io.File imagenesDir = Utilidades.resolveSystemPicturesDirectory();
        if (imagenesDir != null && imagenesDir.isDirectory()) {
            chooser.setCurrentDirectory(imagenesDir);
        }
        chooser.setSelectedFile(new java.io.File(crearNombreSugeridoPreview(mundo)));

        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path destino = chooser.getSelectedFile().toPath();
        if (!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            destino = destino.resolveSibling(destino.getFileName() + ".png");
        }

        if (Files.exists(destino)) {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "El archivo ya existe. ¿Quieres reemplazarlo?",
                    "Guardar como",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            Path parent = destino.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(previewPath, destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se ha podido guardar la vista previa: " + ex.getMessage(), "Guardar como", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String crearNombreSugeridoPreview(World mundo) {
        String nombre = mundo == null || mundo.getWorldName() == null || mundo.getWorldName().isBlank()
                ? "world"
                : mundo.getWorldName();
        String nombreSeguro = nombre.replaceAll("[\\\\/:*?\"<>|]", "_");
        String timestamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        return nombreSeguro + "_preview_" + timestamp + ".png";
    }

    private void avisarSuperposicionesNoDisponiblesSiHaceFalta() {
        if (!previewPreferences.showChunkGrid() && !previewPreferences.showSpawn() && !previewPreferences.showPlayers()) {
            return;
        }

        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null || WorldPreviewOverlayService.loadOverlayData(mundo) != null) {
            return;
        }

        JOptionPane.showMessageDialog(
                this,
                "Esta vista previa no tiene datos de superposición.\nRegenera la vista previa una vez para poder mostrar el spawn o los límites de chunks.",
                "Superposiciones no disponibles",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void actualizarTextoBotonPreview() {
        if (previewGenerationInProgress) {
            World mundo = getMundoSeleccionadoOActivo();
            if (mismoContextoRenderEnCurso(gestorServidores.getServidorSeleccionado(), mundo)) {
                generarPreviewButton.setText("Cancelar");
            } else {
                generarPreviewButton.setText("Generar vista previa");
            }
            ajustarAnchoBotonPreview();
            return;
        }
        World mundo = getMundoSeleccionadoOActivo();
        boolean existePreview = mundo != null && Files.isRegularFile(WorldFilesService.getPreviewPath(mundo));
        generarPreviewButton.setText(existePreview ? "Regenerar vista previa" : "Generar vista previa");
        ajustarAnchoBotonPreview();
    }

    private void actualizarIndicadorRenderEnCurso() {
        if (previewGenerationInProgress) {
            previewRenderStatusLabel.setVisible(true);
            previewRenderStatusLabel.setText(progresoRenderPreviewActual == null || progresoRenderPreviewActual.isBlank()
                    ? "Renderizando..."
                    : progresoRenderPreviewActual);
            previewRenderStatusLabel.setToolTipText("Generando vista previa de " + construirEtiquetaRenderEnCurso());
            return;
        }
        if (ultimoTiempoRenderPreview != null && !ultimoTiempoRenderPreview.isBlank()) {
            previewRenderStatusLabel.setVisible(true);
            previewRenderStatusLabel.setText("Último render: " + ultimoTiempoRenderPreview);
            previewRenderStatusLabel.setToolTipText(ultimoDetalleTiempoRenderPreview == null || ultimoDetalleTiempoRenderPreview.isBlank()
                    ? "Tiempo de la última generación de la vista previa."
                    : ultimoDetalleTiempoRenderPreview);
            return;
        }
        previewRenderStatusLabel.setVisible(false);
        previewRenderStatusLabel.setText("");
        previewRenderStatusLabel.setToolTipText(null);
    }

    private String construirTextoProgresoRender(int regionesCompletadas, int totalRegiones, double elapsedSeconds) {
        String tiempo = formatearDuracionRender(elapsedSeconds);
        if (totalRegiones <= 0) {
            return "Renderizando... " + tiempo;
        }
        return "Renderizando... " + tiempo + " (" + regionesCompletadas + "/" + totalRegiones + ")";
    }

    private void ajustarAnchoBotonPreview() {
        FontMetrics metrics = generarPreviewButton.getFontMetrics(generarPreviewButton.getFont());
        int maxTextWidth = Math.max(
                metrics.stringWidth("Generar vista previa"),
                Math.max(metrics.stringWidth("Regenerar vista previa"), metrics.stringWidth("Cancelar"))
        );
        int width = maxTextWidth + 28;
        int height = Math.max(30, generarPreviewButton.getPreferredSize().height);
        Dimension fixedSize = new Dimension(width, height);
        generarPreviewButton.setPreferredSize(fixedSize);
        generarPreviewButton.setMinimumSize(fixedSize);
    }

    private String construirEtiquetaRenderEnCurso() {
        String serverName = previewGenerationServerName == null || previewGenerationServerName.isBlank()
                ? "Servidor"
                : previewGenerationServerName;
        String worldName = previewGenerationWorldName == null || previewGenerationWorldName.isBlank()
                ? "mundo"
                : previewGenerationWorldName;
        return serverName + " / " + worldName;
    }

    private String construirMensajeRenderEnCurso() {
        return "Ya hay una renderización en curso para " + construirEtiquetaRenderEnCurso()
                + ".\nEspera a que termine o vuelve a ese mundo para cancelarla.";
    }

    private String formatearDuracionRender(double elapsedSeconds) {
        if (Double.isNaN(elapsedSeconds) || Double.isInfinite(elapsedSeconds) || elapsedSeconds < 0d) {
            return "-";
        }
        if (elapsedSeconds >= 60d) {
            int totalSeconds = (int) Math.round(elapsedSeconds);
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return minutes + " min " + seconds + " s";
        }
        return String.format(Locale.ROOT, "%.2f s", elapsedSeconds);
    }

    private String construirDetalleTiempoRender(MCARenderer.RenderStats stats, double elapsedSeconds) {
        if (stats == null) {
            return "Tiempo total de la última generación de la vista previa: " + formatearDuracionRender(elapsedSeconds);
        }
        return "<html>"
                + "Total: " + formatearDuracionRender(elapsedSeconds) + "<br>"
                + "Muestreo: " + formatearDuracionNanos(stats.sampleNanos()) + "<br>"
                + "Pintado: " + formatearDuracionNanos(stats.paintNanos()) + "<br>"
                + "Composicion: " + formatearDuracionNanos(stats.composeNanos()) + "<br>"
                + "Crop: " + formatearDuracionNanos(stats.cropNanos()) + "<br>"
                + "Marcador: " + formatearDuracionNanos(stats.markerNanos()) + "<br>"
                + "Total medido: " + formatearDuracionNanos(stats.totalTrackedNanos())
                + "</html>";
    }

    private String formatearDuracionNanos(long nanos) {
        if (nanos <= 0L) {
            return "0 ms";
        }
        double millis = nanos / 1_000_000.0d;
        if (millis >= 1000.0d) {
            return String.format(Locale.ROOT, "%.2f s", millis / 1000.0d);
        }
        if (millis >= 10.0d) {
            return String.format(Locale.ROOT, "%.0f ms", millis);
        }
        return String.format(Locale.ROOT, "%.1f ms", millis);
    }

    private void updateUseWorldButtonState() {
        World seleccionado = (World) mundosCombo.getSelectedItem();
        boolean hayMundoSeleccionado = mundosCombo.isEnabled() && seleccionado != null;
        boolean cambioPendiente = hayMundoSeleccionado
                && mundoActivoActual != null
                && !Objects.equals(seleccionado.getWorldName(), mundoActivoActual.getWorldName());

        applyDefaultPrimaryButtonStyle();
        usarEsteMundoButton.setEnabled(cambioPendiente);
        if (cambioPendiente) {
            AppTheme.applyAccentButtonStyle(usarEsteMundoButton);
        }
        usarEsteMundoButton.repaint();
    }

    private void applyDefaultPrimaryButtonStyle() {
        AppTheme.applyActionButtonStyle(usarEsteMundoButton);
    }

    private void stylePreviewMenuButton(JButton button) {
        if (button == null) return;
        AppTheme.applyActionButtonStyle(button);
        button.setText(null);
        button.setIcon(SvgIconFactory.create("doraicons/tuning-2.svg", 18, 18));
        button.setMargin(new Insets(4, 8, 4, 8));
        button.setToolTipText("Opciones de vista previa");
        stylePreviewOverlayButton(button);
    }

    private void stylePreviewOverlayButton(JButton button) {
        if (button == null) return;
        AppTheme.applyActionButtonStyle(button);
    }

    private void stylePreviewStatusLabel(JLabel label) {
        if (label == null) return;
        label.setVisible(false);
        label.setOpaque(true);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setForeground(Color.WHITE);
        label.setBackground(new Color(24, 28, 36, 220));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 45)),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
    }

    private void seleccionarPerfilRenderPreview(PreviewRenderPreferences.PreviewRenderPreset preset) {
        if (preset == null) {
            return;
        }
        perfilRenderCombo.setSelectedItem(preset);
    }

    private void aplicarPerfilRenderPreviewSeleccionado() {
        PreviewRenderPreferences.PreviewRenderPreset selected =
                (PreviewRenderPreferences.PreviewRenderPreset) perfilRenderCombo.getSelectedItem();
        if (selected == null) {
            return;
        }
        previewPreferences = selected == PreviewRenderPreferences.PreviewRenderPreset.CUSTOM
                ? previewPreferences.withPreset(PreviewRenderPreferences.PreviewRenderPreset.CUSTOM)
                : previewPreferences.withPreset(selected);
        actualizandoOpcionesPreview = true;
        try {
            aplicarPreferenciasPreviewAControles();
        } finally {
            actualizandoOpcionesPreview = false;
        }
        aplicarPreferenciasPreviewAOverlay();
        actualizarEstadoControlesRender();
        guardarPreferenciasPreviewServidorActual();
    }

    private MCARenderer.RenderOptions crearRenderOptionsPreview() {
        return previewPreferences.toRenderOptions();
    }

    private void instalarMenuOpcionesPreview() {
        configurarPerfilRenderCombo();
        perfilRenderCombo.setToolTipText("<html><b>Preset de calidad:</b> Personalizado conserva el estado actual. Calidad, Equilibrado y Rendimiento aplican un conjunto completo y visible de toggles.</html>");
        perfilRenderCombo.addActionListener(e -> {
            if (actualizandoOpcionesPreview) {
                return;
            }
            aplicarPerfilRenderPreviewSeleccionado();
        });

        stylePreviewOptionCheckBox(usarTodoMenuItem);
        usarTodoMenuItem.setToolTipText("<html><b>Qué hace:</b> renderiza todo el mundo disponible en vez de recortar una ventana alrededor del centro.<br><b>Rendimiento:</b> puede disparar mucho tiempo, memoria y tamaño de salida.<br><b>Visual:</b> ofrece contexto completo.<br><b>Efectos secundarios:</b> desactiva centro y límite porque dejan de tener sentido.</html>");
        usarTodoMenuItem.addActionListener(e -> {
            previewPreferences = previewPreferences.withUseWholeMap(usarTodoMenuItem.isSelected());
            actualizarEstadoControlesRender();
            guardarPreferenciasPreviewServidorActual();
        });

        stylePreviewOptionCheckBox(tiempoRealPreviewMenuItem);
        tiempoRealPreviewMenuItem.setToolTipText("<html><b>Qué hace:</b> actualiza el lienzo conforme se van componiendo regiones durante la generación.<br><b>Rendimiento:</b> añade repintados Swing y copias de imagen; el coste es de CPU/UI, no de GPU del renderer.<br><b>Visual:</b> permite ver progreso real.<br><b>Efectos secundarios:</b> puede hacer la generación algo menos estable en equipos justos.</html>");
        tiempoRealPreviewMenuItem.addActionListener(e -> {
            previewPreferences = previewPreferences.withRenderRealtime(tiempoRealPreviewMenuItem.isSelected());
            guardarPreferenciasPreviewServidorActual();
        });

        stylePreviewOptionCheckBox(mostrarSpawnMenuItem);
        mostrarSpawnMenuItem.setToolTipText("<html><b>Qué hace:</b> superpone la marca del spawn usando metadata de overlay de la vista previa.<br><b>Rendimiento:</b> impacto mínimo; solo dibujo de overlay en la UI.<br><b>Visual:</b> facilita orientarse en el mapa.<br><b>Efectos secundarios:</b> si la vista previa no tiene metadata de overlay hay que regenerarla una vez.</html>");
        mostrarSpawnMenuItem.addActionListener(e -> {
            previewPreferences = previewPreferences.withShowSpawn(mostrarSpawnMenuItem.isSelected());
            aplicarPreferenciasPreviewAOverlay();
            avisarSuperposicionesNoDisponiblesSiHaceFalta();
            guardarPreferenciasPreviewServidorActual();
        });

        stylePreviewOptionCheckBox(mostrarJugadoresMenuItem);
        mostrarJugadoresMenuItem.setToolTipText("<html><b>Qué hace:</b> muestra sobre la vista previa las últimas posiciones de jugadores conocidas.<br><b>Rendimiento:</b> impacto mínimo; solo overlay 2D en la interfaz.<br><b>Visual:</b> ayuda a localizar actividad reciente.<br><b>Efectos secundarios:</b> depende de playerdata y metadata de overlay disponibles.</html>");
        mostrarJugadoresMenuItem.addActionListener(e -> {
            previewPreferences = previewPreferences.withShowPlayers(mostrarJugadoresMenuItem.isSelected());
            aplicarPreferenciasPreviewAOverlay();
            avisarSuperposicionesNoDisponiblesSiHaceFalta();
            guardarPreferenciasPreviewServidorActual();
        });

        stylePreviewOptionCheckBox(limitesChunksMenuItem);
        limitesChunksMenuItem.setToolTipText("<html><b>Qué hace:</b> dibuja la retícula de chunks sobre la vista previa cargada.<br><b>Rendimiento:</b> impacto mínimo; solo overlay local en la UI.<br><b>Visual:</b> facilita depurar alineación, estructuras y medidas.<br><b>Efectos secundarios:</b> puede ensuciar mapas muy densos.</html>");
        limitesChunksMenuItem.addActionListener(e -> {
            previewPreferences = previewPreferences.withShowChunkGrid(limitesChunksMenuItem.isSelected());
            aplicarPreferenciasPreviewAOverlay();
            avisarSuperposicionesNoDisponiblesSiHaceFalta();
            guardarPreferenciasPreviewServidorActual();
        });

        for (PreviewRenderPreferences.RenderToggle toggle : PreviewRenderPreferences.RenderToggle.values()) {
            renderToggleCheckBoxes.put(toggle, createRenderToggleCheckBox(toggle));
        }

        configurarLimiteRenderCombo();
        configurarCentroRenderCombo();
        actualizarEstadoControlesRender();

        JPanel generationSection = createPreviewOptionsSectionPanel("Generación");
        generationSection.add(createPreviewStackedOptionRow("Área máxima", limiteRenderCombo,
                "<html><b>Qué hace:</b> define el tamaño máximo de la ventana de bloques que se intentará renderizar alrededor del centro.<br><b>Rendimiento:</b> cuanto mayor sea, más regiones/chunks se leerán y más tiempo consumirá.<br><b>Visual:</b> más contexto a cambio de vistas previas más pesadas.<br><b>Efectos secundarios:</b> valores altos pueden disparar memoria y tiempo.</html>"));
        generationSection.add(Box.createVerticalStrut(4));
        generationSection.add(createPreviewStackedOptionRow("Centro de generación", centroRenderCombo,
                "<html><b>Qué hace:</b> elige el punto alrededor del que se recorta la vista previa cuando no se genera el mundo completo.<br><b>Rendimiento:</b> no cambia el coste por píxel, pero sí qué zona se procesa.<br><b>Visual:</b> cambia totalmente el encuadre final.<br><b>Efectos secundarios:</b> si el jugador ya no existe o no tiene datos recientes se vuelve al spawn.</html>"));
        generationSection.add(Box.createVerticalStrut(4));
        generationSection.add(createPreviewCheckBoxRow(usarTodoMenuItem, usarTodoMenuItem.getToolTipText()));
        generationSection.add(Box.createVerticalStrut(4));
        generationSection.add(createPreviewCheckBoxRow(tiempoRealPreviewMenuItem, tiempoRealPreviewMenuItem.getToolTipText()));
        generationSection.add(Box.createVerticalGlue());

        JPanel qualitySection = createPreviewOptionsSectionPanel("Calidad del rendering");
        qualitySection.add(createPreviewOptionRow("Preset", perfilRenderCombo, perfilRenderCombo.getToolTipText()));
        qualitySection.add(Box.createVerticalStrut(4));
        for (PreviewRenderPreferences.RenderToggle toggle : renderToggleOrder()) {
            JCheckBox checkBox = renderToggleCheckBoxes.get(toggle);
            qualitySection.add(createPreviewCheckBoxRow(checkBox, toggle.helpText()));
            qualitySection.add(Box.createVerticalStrut(3));
        }
        qualitySection.add(Box.createVerticalGlue());

        JPanel overlaySection = createPreviewOptionsSectionPanel("Superposicion");
        overlaySection.add(createPreviewCheckBoxRow(mostrarSpawnMenuItem, mostrarSpawnMenuItem.getToolTipText()));
        overlaySection.add(Box.createVerticalStrut(4));
        overlaySection.add(createPreviewCheckBoxRow(mostrarJugadoresMenuItem, mostrarJugadoresMenuItem.getToolTipText()));
        overlaySection.add(Box.createVerticalStrut(4));
        overlaySection.add(createPreviewCheckBoxRow(limitesChunksMenuItem, limitesChunksMenuItem.getToolTipText()));
        overlaySection.add(Box.createVerticalGlue());

        JPanel columnsPanel = new JPanel(new GridBagLayout());
        columnsPanel.setOpaque(false);
        columnsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel generationWrapper = wrapPreviewOptionsSection(generationSection);
        JPanel qualityWrapper = wrapPreviewOptionsSection(qualitySection);
        JPanel overlayWrapper = wrapPreviewOptionsSection(overlaySection);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, 16);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.weightx = 0.0d;
        gbc.fill = GridBagConstraints.NONE;
        columnsPanel.add(generationWrapper, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0d;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        columnsPanel.add(qualityWrapper, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.0d;
        gbc.insets = new Insets(0, 0, 0, 0);
        gbc.fill = GridBagConstraints.NONE;
        columnsPanel.add(overlayWrapper, gbc);

        FlatScrollPane optionsScrollPane = new FlatScrollPane();
        optionsScrollPane.setViewportView(columnsPanel);
        optionsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        optionsScrollPane.setOpaque(false);
        optionsScrollPane.getViewport().setOpaque(false);
        optionsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        optionsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        optionsScrollPane.getVerticalScrollBar().setUnitIncrement(18);
        optionsScrollPane.getVerticalScrollBar().setBlockIncrement(72);
        optionsScrollPane.getHorizontalScrollBar().setUnitIncrement(18);
        optionsScrollPane.getHorizontalScrollBar().setBlockIncrement(72);
        optionsScrollPane.setPreferredSize(new Dimension(550, 250));

        previewOptionsPanel = new JPanel(new BorderLayout());
        AppTheme.applySurfacePreviewStyle(previewOptionsPanel, new Insets(6, 6, 6, 6));
        previewOptionsPanel.add(optionsScrollPane, BorderLayout.CENTER);

        previewMenuButton.addActionListener(e -> mostrarMenuOpcionesPreview(previewMenuButton));
    }

    private List<PreviewRenderPreferences.RenderToggle> renderToggleOrder() {
        return List.of(
                PreviewRenderPreferences.RenderToggle.SHADE_BY_HEIGHT,
                PreviewRenderPreferences.RenderToggle.ADVANCED_MATERIAL_SHADING,
                PreviewRenderPreferences.RenderToggle.WATER_SUBSURFACE_SHADING,
                PreviewRenderPreferences.RenderToggle.ADVANCED_WATER_COLORING,
                PreviewRenderPreferences.RenderToggle.BIOME_COLORING,
                PreviewRenderPreferences.RenderToggle.ADVANCED_BIOME_COLORING
        );
    }

    private JCheckBox createRenderToggleCheckBox(PreviewRenderPreferences.RenderToggle toggle) {
        JCheckBox checkBox = new JCheckBox(toggle.label(), previewPreferences.isEnabled(toggle));
        stylePreviewOptionCheckBox(checkBox);
        checkBox.setToolTipText(toggle.helpText());
        checkBox.addActionListener(e -> {
            if (actualizandoOpcionesPreview) {
                return;
            }
            previewPreferences = previewPreferences.withRenderToggle(toggle, checkBox.isSelected());
            seleccionarPerfilRenderPreview(PreviewRenderPreferences.PreviewRenderPreset.CUSTOM);
            guardarPreferenciasPreviewServidorActual();
        });
        return checkBox;
    }

    private void mostrarMenuOpcionesPreview(Component anchor) {
        if (anchor == null || previewOptionsPanel == null) {
            return;
        }
        if (previewOptionsDialog != null && previewOptionsDialog.isVisible()) {
            ocultarMenuOpcionesPreview();
            return;
        }
        cargarPreferenciasPreviewServidorActual();
        actualizarCentroRenderCombo();
        actualizarEstadoControlesRender();
        mostrarPreviewOptionsDialog(anchor);
    }

    private void mostrarPreviewOptionsDialog(Component anchor) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (!(owner instanceof Frame frameOwner)) {
            return;
        }
        if (previewOptionsDialog == null || previewOptionsDialog.getOwner() != frameOwner) {
            previewOptionsDialog = new JDialog(frameOwner, Dialog.ModalityType.MODELESS);
            previewOptionsDialog.setUndecorated(true);
            previewOptionsDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            previewOptionsDialog.setFocusableWindowState(true);
            previewOptionsDialog.setContentPane(previewOptionsPanel);
            previewOptionsDialog.getRootPane().registerKeyboardAction(
                    e -> ocultarMenuOpcionesPreview(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW
            );
        }

        previewOptionsDialog.pack();
        previewOptionsDialog.setLocation(calcularUbicacionPreviewOptionsDialog(anchor, previewOptionsDialog.getSize()));
        previewOptionsDialog.setVisible(true);
        previewOptionsDialog.toFront();
        instalarPreviewOptionsOutsideClickListener();
    }

    private Point calcularUbicacionPreviewOptionsDialog(Component anchor, Dimension dialogSize) {
        Point anchorOnScreen = anchor.getLocationOnScreen();
        Rectangle screenBounds = anchor.getGraphicsConfiguration() != null
                ? anchor.getGraphicsConfiguration().getBounds()
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int x = anchorOnScreen.x + anchor.getWidth() - dialogSize.width;
        int y = anchorOnScreen.y + anchor.getHeight() + 6;
        x = Math.max(screenBounds.x + 8, Math.min(x, screenBounds.x + screenBounds.width - dialogSize.width - 8));
        y = Math.max(screenBounds.y + 8, Math.min(y, screenBounds.y + screenBounds.height - dialogSize.height - 8));
        return new Point(x, y);
    }

    private void ocultarMenuOpcionesPreview() {
        desinstalarPreviewOptionsOutsideClickListener();
        if (previewOptionsDialog != null) {
            previewOptionsDialog.setVisible(false);
        }
    }

    private void instalarPreviewOptionsOutsideClickListener() {
        desinstalarPreviewOptionsOutsideClickListener();
        previewOptionsOutsideClickListener = event -> {
            if (!(event instanceof MouseEvent mouseEvent) || mouseEvent.getID() != MouseEvent.MOUSE_PRESSED) {
                return;
            }
            Object source = mouseEvent.getSource();
            if (!(source instanceof Component component) || isPreviewOptionsInteraction(component)) {
                return;
            }
            SwingUtilities.invokeLater(this::ocultarMenuOpcionesPreview);
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(previewOptionsOutsideClickListener, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void desinstalarPreviewOptionsOutsideClickListener() {
        if (previewOptionsOutsideClickListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(previewOptionsOutsideClickListener);
            previewOptionsOutsideClickListener = null;
        }
    }

    private boolean isPreviewOptionsInteraction(Component component) {
        if (component == null) {
            return false;
        }
        if (previewOptionsDialog != null && SwingUtilities.isDescendingFrom(component, previewOptionsDialog)) {
            return true;
        }
        if (SwingUtilities.isDescendingFrom(component, previewMenuButton)) {
            return true;
        }
        JPopupMenu popupMenu = (JPopupMenu) SwingUtilities.getAncestorOfClass(JPopupMenu.class, component);
        if (popupMenu == null) {
            return false;
        }
        Component invoker = popupMenu.getInvoker();
        return invoker != null
                && ((previewOptionsPanel != null && SwingUtilities.isDescendingFrom(invoker, previewOptionsPanel))
                || SwingUtilities.isDescendingFrom(invoker, previewMenuButton));
    }

    private void stylePreviewOptionCheckBox(JCheckBox checkBox) {
        if (checkBox == null) {
            return;
        }
        checkBox.setOpaque(false);
        checkBox.setFocusPainted(false);
        AppTheme.applyHandCursor(checkBox);
        checkBox.setForeground(AppTheme.getForeground());
    }

    private JLabel createPreviewOptionsSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setForeground(AppTheme.getMutedForeground());
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        return label;
    }

    private JPanel createPreviewOptionsSectionPanel(String title) {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.add(createPreviewOptionsSectionLabel(title));
        section.add(Box.createVerticalStrut(4));
        return section;
    }

    private JPanel wrapPreviewOptionsSection(JPanel content) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder());
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    private void configurarPerfilRenderCombo() {
        perfilRenderCombo.setOpaque(false);
        perfilRenderCombo.setFocusable(true);
        perfilRenderCombo.setRoundRect(true);
        perfilRenderCombo.setMaximumSize(new Dimension(180, 30));
        perfilRenderCombo.setPreferredSize(new Dimension(180, 30));
    }

    private void configurarLimiteRenderCombo() {
        limiteRenderCombo.setOpaque(false);
        limiteRenderCombo.setFocusable(true);
        limiteRenderCombo.setRoundRect(true);
        limiteRenderCombo.setMaximumSize(new Dimension(180, 30));
        limiteRenderCombo.setPreferredSize(new Dimension(180, 30));
        limiteRenderCombo.addActionListener(e -> {
            if (actualizandoOpcionesPreview) {
                return;
            }
            PreviewRenderLimitOption selected = (PreviewRenderLimitOption) limiteRenderCombo.getSelectedItem();
            if (selected != null) {
                previewPreferences = previewPreferences.withRenderLimitPixels(selected.maxPixels());
                guardarPreferenciasPreviewServidorActual();
            }
        });
        seleccionarLimiteRenderComboActual();
    }

    private void seleccionarLimiteRenderComboActual() {
        for (int i = 0; i < limiteRenderCombo.getItemCount(); i++) {
            PreviewRenderLimitOption option = limiteRenderCombo.getItemAt(i);
            if (option.maxPixels() == previewPreferences.renderLimitPixels()) {
                limiteRenderCombo.setSelectedIndex(i);
                return;
            }
        }
        if (limiteRenderCombo.getItemCount() > 0) {
            limiteRenderCombo.setSelectedIndex(0);
        }
    }

    private void configurarCentroRenderCombo() {
        centroRenderCombo.setOpaque(false);
        centroRenderCombo.setFocusable(true);
        centroRenderCombo.setRoundRect(true);
        centroRenderCombo.setMaximumSize(new Dimension(180, 30));
        centroRenderCombo.setPreferredSize(new Dimension(180, 30));
        centroRenderCombo.addActionListener(e -> {
            if (actualizandoOpcionesPreview) {
                return;
            }
            PreviewCenterOption selected = (PreviewCenterOption) centroRenderCombo.getSelectedItem();
            if (selected != null) {
                previewPreferences = previewPreferences.withRenderCenterId(selected.id());
                guardarPreferenciasPreviewServidorActual();
            }
        });
    }

    private void actualizarCentroRenderCombo() {
        World mundo = getMundoSeleccionadoOActivo();
        Server server = gestorServidores.getServidorSeleccionado();
        WorldDataReader.SpawnPoint spawnPoint = mundo == null ? null : WorldDataReader.getSpawnPoint(mundo);
        List<PreviewPlayerData> recentPlayers = obtenerJugadoresRecientesDesdePlayerdata(server, mundo);

        DefaultComboBoxModel<PreviewCenterOption> model = new DefaultComboBoxModel<>();
        if (spawnPoint != null) {
            model.addElement(new PreviewCenterOption("spawn", "Spawn", new MCARenderer.WorldPoint(spawnPoint.x(), spawnPoint.z())));
        }
        for (PreviewPlayerData player : recentPlayers) {
            String label = (player.username() == null || player.username().isBlank()) ? "Jugador" : player.username();
            model.addElement(new PreviewCenterOption("player:" + player.username(), label, player.point()));
        }

        centroRenderCombo.setModel(model);
        seleccionarCentroRenderComboActual();
    }

    private void seleccionarCentroRenderComboActual() {
        for (int i = 0; i < centroRenderCombo.getItemCount(); i++) {
            PreviewCenterOption option = centroRenderCombo.getItemAt(i);
            if (Objects.equals(option.id(), previewPreferences.renderCenterId())) {
                centroRenderCombo.setSelectedIndex(i);
                return;
            }
        }
        if (centroRenderCombo.getItemCount() > 0) {
            centroRenderCombo.setSelectedIndex(0);
            PreviewCenterOption selected = (PreviewCenterOption) centroRenderCombo.getSelectedItem();
            previewPreferences = previewPreferences.withRenderCenterId(selected == null ? "spawn" : selected.id());
        }
    }

    private void actualizarEstadoControlesRender() {
        boolean habilitados = !previewPreferences.useWholeMap();
        limiteRenderCombo.setEnabled(habilitados);
        centroRenderCombo.setEnabled(habilitados);
    }

    private MCARenderer.WorldPoint resolverCentroRender(World mundo, WorldDataReader.SpawnPoint spawnPoint, List<PreviewPlayerData> recentPlayers) {
        if (previewPreferences.renderCenterId() == null || previewPreferences.renderCenterId().isBlank() || "spawn".equals(previewPreferences.renderCenterId())) {
            return spawnPoint == null ? null : new MCARenderer.WorldPoint(spawnPoint.x(), spawnPoint.z());
        }

        if (recentPlayers != null) {
            for (PreviewPlayerData player : recentPlayers) {
                if (player == null || player.username() == null || player.username().isBlank()) {
                    continue;
                }
                if (Objects.equals("player:" + player.username(), previewPreferences.renderCenterId())) {
                    return player.point();
                }
            }
        }

        return spawnPoint == null ? null : new MCARenderer.WorldPoint(spawnPoint.x(), spawnPoint.z());
    }

    private JPanel createPreviewOptionRow(String label, JComponent component, String helpText) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JLabel textLabel = new JLabel(label);
        textLabel.setForeground(AppTheme.getForeground());
        textLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        textLabel.setVerticalAlignment(SwingConstants.CENTER);
        textLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        JPanel fieldWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        fieldWrapper.setOpaque(false);
        Dimension fieldSize = new Dimension(152, 28);
        component.setPreferredSize(fieldSize);
        component.setMinimumSize(fieldSize);
        component.setMaximumSize(fieldSize);
        fieldWrapper.add(component);
        if (helpText != null && !helpText.isBlank()) {
            component.setToolTipText(helpText);
            textLabel.setToolTipText(helpText);
        }
        row.add(textLabel, BorderLayout.WEST);
        row.add(fieldWrapper, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, fieldSize.height));
        return row;
    }

    private JPanel createPreviewStackedOptionRow(String label, JComponent component, String helpText) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));

        JLabel textLabel = new JLabel(label);
        textLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textLabel.setForeground(AppTheme.getForeground());
        textLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));

        Dimension fieldSize = new Dimension(152, 28);
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setPreferredSize(fieldSize);
        component.setMinimumSize(fieldSize);
        component.setMaximumSize(fieldSize);

        if (helpText != null && !helpText.isBlank()) {
            component.setToolTipText(helpText);
            textLabel.setToolTipText(helpText);
        }

        row.add(textLabel);
        row.add(component);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, textLabel.getPreferredSize().height + fieldSize.height + 2));
        return row;
    }

    private JPanel createPreviewCheckBoxRow(JCheckBox checkBox, String helpText) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (helpText != null && !helpText.isBlank()) {
            checkBox.setToolTipText(helpText);
        }
        row.add(checkBox, BorderLayout.CENTER);
        return row;
    }

    private void instalarInteraccionSeed() {
        Font fuenteBase = seedValueLabel.getFont();
        seedValueLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        seedValueLabel.setToolTipText("Haz click para copiar la seed.");

        seedValueLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                String texto = obtenerValorSeedActual();
                if (texto == null) return;
                java.util.Map<java.awt.font.TextAttribute, Object> attrs = new java.util.HashMap<>(fuenteBase.getAttributes());
                attrs.put(java.awt.font.TextAttribute.UNDERLINE, java.awt.font.TextAttribute.UNDERLINE_ON);
                seedValueLabel.setFont(fuenteBase.deriveFont(attrs));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                seedValueLabel.setFont(fuenteBase);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                String texto = obtenerValorSeedActual();
                if (texto == null) return;
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(texto), null);
                JOptionPane.showMessageDialog(
                        PanelMundo.this,
                        "Semilla copiada al portapapeles.",
                        "Copiado",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        });
    }

    private String obtenerValorSeedActual() {
        String texto = seedValueLabel.getText();
        if (texto == null) return null;
        String valor = texto.trim();
        if (valor.isEmpty() || "-".equals(valor)) return null;
        return valor;
    }

    private void styleInfoLabel(JLabel label) {
        if (label == null) return;
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 14f));
    }

    private String valorOPlaceholder(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String leerTipoMundo(World mundo) {
        return WorldFilesService.readWorldMetadata(mundo).getProperty("level-type");
    }

    private String formatearTamano(long bytes) {
        if (bytes <= 0L) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int unitIndex = 0;
        while (value >= 1024d && unitIndex < units.length - 1) {
            value /= 1024d;
            unitIndex++;
        }

        DecimalFormat format = value >= 100d || unitIndex == 0 ? new DecimalFormat("0") : new DecimalFormat("0.#");
        return format.format(value) + " " + units[unitIndex];
    }

    private static String obtenerInicial(String username) {
        if (username == null || username.isBlank()) return "?";
        return username.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static void precargarCabezasJugadoresPreview(List<PreviewPlayerPoint> playerPoints, Runnable onLoaded) {
        if (playerPoints == null || playerPoints.isEmpty()) {
            return;
        }
        for (PreviewPlayerPoint playerPoint : playerPoints) {
            if (playerPoint == null || playerPoint.username() == null || playerPoint.username().isBlank()) {
                continue;
            }
            cargarCabezaJugadorPreviewAsync(playerPoint.username(), onLoaded);
        }
    }

    private static void cargarCabezaJugadorPreviewAsync(String username, Runnable onLoaded) {
        if (username == null || username.isBlank()) {
            return;
        }

        String key = username.strip().toLowerCase(Locale.ROOT);
        if (key.isBlank() || PLAYER_HEAD_CACHE.containsKey(key) || !PLAYER_HEADS_LOADING.add(key)) {
            return;
        }

        MojangAPI.runBackgroundRequest(() -> {
            try {
                ImageIcon head = new MojangAPI().obtenerCabezaJugador(username, PREVIEW_PLAYER_HEAD_SIZE);
                if (head != null) {
                    PLAYER_HEAD_CACHE.put(key, head);
                }
            } finally {
                PLAYER_HEADS_LOADING.remove(key);
                if (onLoaded != null) {
                    SwingUtilities.invokeLater(onLoaded);
                }
            }
        });
    }

    private static final class PreviewImagePanel extends JComponent {
        private BufferedImage image;
        private PreviewOverlayData overlayData;
        private boolean chunkGridVisible = false;
        private boolean spawnVisible = false;
        private boolean playersVisible = false;
        private String message = "La vista previa todavía no existe.";
        private double zoom = 1.0d;
        private double panX = 0d;
        private double panY = 0d;
        private Point dragAnchor;
        private double dragStartPanX;
        private double dragStartPanY;

        private PreviewImagePanel() {
            setOpaque(false);
            setCursor(Cursor.getDefaultCursor());

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    updateCursor();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e) || image == null) {
                        return;
                    }
                    dragAnchor = e.getPoint();
                    dragStartPanX = panX;
                    dragStartPanY = panY;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragAnchor == null || image == null) {
                        return;
                    }
                    panX = dragStartPanX + (e.getX() - dragAnchor.x);
                    panY = dragStartPanY + (e.getY() - dragAnchor.y);
                    clampPan();
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragAnchor = null;
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    if (image == null) {
                        return;
                    }
                    double oldZoom = zoom;
                    double factor = Math.pow(1.12d, -e.getPreciseWheelRotation());
                    zoom = Math.max(1.0d, Math.min(32.0d, zoom * factor));
                    adjustPanForZoom(oldZoom, zoom, e.getPoint());
                    clampPan();
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (image != null && e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                        resetView();
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        private void setImage(BufferedImage image) {
            this.image = image;
            this.message = "";
            resetView();
            updateCursor();
        }

        private void setOverlayData(PreviewOverlayData overlayData) {
            this.overlayData = overlayData;
            precargarCabezasJugadoresPreview(overlayData == null ? null : overlayData.playerPoints(), this::repaint);
            repaint();
        }

        private void setChunkGridVisible(boolean chunkGridVisible) {
            this.chunkGridVisible = chunkGridVisible;
            repaint();
        }

        private void setSpawnVisible(boolean spawnVisible) {
            this.spawnVisible = spawnVisible;
            repaint();
        }

        private void setPlayersVisible(boolean playersVisible) {
            this.playersVisible = playersVisible;
            if (playersVisible && overlayData != null) {
                precargarCabezasJugadoresPreview(overlayData.playerPoints(), this::repaint);
            }
            repaint();
        }

        private void clearImage() {
            this.image = null;
            this.overlayData = null;
            this.message = "";
            resetView();
            updateCursor();
        }

        private void setMessage(String message) {
            this.image = null;
            this.overlayData = null;
            this.message = message == null ? "" : message;
            resetView();
            updateCursor();
        }

        private void resetView() {
            zoom = 1.0d;
            panX = 0d;
            panY = 0d;
            repaint();
        }

        private void updateCursor() {
            setCursor(image == null
                    ? Cursor.getDefaultCursor()
                    : Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }

        private void adjustPanForZoom(double oldZoom, double newZoom, Point anchor) {
            RenderState oldState = computeState(oldZoom, panX, panY);
            if (oldState == null || anchor == null) {
                return;
            }

            double imageX = (anchor.x - oldState.drawX()) / oldState.scale();
            double imageY = (anchor.y - oldState.drawY()) / oldState.scale();
            RenderState newState = computeState(newZoom, panX, panY);
            if (newState == null) {
                return;
            }

            panX = anchor.x - (newState.baseX() + imageX * newState.scale());
            panY = anchor.y - (newState.baseY() + imageY * newState.scale());
        }

        private void clampPan() {
            RenderState state = computeState(zoom, panX, panY);
            if (state == null) {
                panX = 0d;
                panY = 0d;
                return;
            }
            panX = Math.max(-state.maxPanX(), Math.min(state.maxPanX(), panX));
            panY = Math.max(-state.maxPanY(), Math.min(state.maxPanY(), panY));
        }

        private RenderState computeState(double zoomValue, double panXValue, double panYValue) {
            if (image == null || getWidth() <= 0 || getHeight() <= 0 || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return null;
            }

            double baseScale = Math.min((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
            if (baseScale <= 0d) {
                return null;
            }

            double scale = baseScale * Math.max(1.0d, zoomValue);
            int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
            double baseX = (getWidth() - drawWidth) / 2.0d;
            double baseY = (getHeight() - drawHeight) / 2.0d;
            double maxPanX = Math.max(0d, (drawWidth - getWidth()) / 2.0d);
            double maxPanY = Math.max(0d, (drawHeight - getHeight()) / 2.0d);
            int drawX = (int) Math.round(baseX + Math.max(-maxPanX, Math.min(maxPanX, panXValue)));
            int drawY = (int) Math.round(baseY + Math.max(-maxPanY, Math.min(maxPanY, panYValue)));
            return new RenderState(scale, drawX, drawY, drawWidth, drawHeight, baseX, baseY, maxPanX, maxPanY);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                if (image == null) {
                    g2.setColor(AppTheme.getMutedForeground());
                    FontMetrics metrics = g2.getFontMetrics();
                    int x = Math.max(8, (getWidth() - metrics.stringWidth(message)) / 2);
                    int y = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
                    g2.drawString(message, x, y);
                    return;
                }

                clampPan();
                RenderState state = computeState(zoom, panX, panY);
                if (state == null) {
                    return;
                }

                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g2.drawImage(image, state.drawX(), state.drawY(), state.drawWidth(), state.drawHeight(), null);
                paintChunkGrid(g2, state);
                paintSpawn(g2, state);
                paintPlayers(g2, state);
            } finally {
                g2.dispose();
            }
        }

        private void paintChunkGrid(Graphics2D g2, RenderState state) {
            if (!chunkGridVisible || overlayData == null || state == null) {
                return;
            }

            double pixelsPerChunk = overlayData.pixelsPerBlock() * 16d * state.scale();
            if (pixelsPerChunk < 6d) {
                return;
            }

            double firstLineX = state.drawX() - (floorMod(overlayData.originBlockX(), 16) * overlayData.pixelsPerBlock() * state.scale());
            double firstLineY = state.drawY() - (floorMod(overlayData.originBlockZ(), 16) * overlayData.pixelsPerBlock() * state.scale());

            Graphics2D grid = (Graphics2D) g2.create();
            try {
                grid.setClip(state.drawX(), state.drawY(), state.drawWidth(), state.drawHeight());
                grid.setColor(new Color(255, 255, 255, 72));

                for (double x = firstLineX; x <= state.drawX() + state.drawWidth(); x += pixelsPerChunk) {
                    int xi = (int) Math.round(x);
                    grid.drawLine(xi, state.drawY(), xi, state.drawY() + state.drawHeight());
                }
                for (double y = firstLineY; y <= state.drawY() + state.drawHeight(); y += pixelsPerChunk) {
                    int yi = (int) Math.round(y);
                    grid.drawLine(state.drawX(), yi, state.drawX() + state.drawWidth(), yi);
                }
            } finally {
                grid.dispose();
            }
        }

        private int floorMod(int value, int mod) {
            int result = value % mod;
            return result < 0 ? result + mod : result;
        }

        private void paintSpawn(Graphics2D g2, RenderState state) {
            if (!spawnVisible || overlayData == null || overlayData.spawnPoint() == null || state == null) {
                return;
            }

            double relativeBlockX = overlayData.spawnPoint().x() - overlayData.originBlockX();
            double relativeBlockZ = overlayData.spawnPoint().z() - overlayData.originBlockZ();
            double imagePixelX = relativeBlockX * overlayData.pixelsPerBlock();
            double imagePixelY = relativeBlockZ * overlayData.pixelsPerBlock();
            int centerX = (int) Math.round(state.drawX() + imagePixelX * state.scale());
            int centerY = (int) Math.round(state.drawY() + imagePixelY * state.scale());

            if (centerX < state.drawX() || centerY < state.drawY()
                    || centerX >= state.drawX() + state.drawWidth()
                    || centerY >= state.drawY() + state.drawHeight()) {
                return;
            }

            Graphics2D marker = (Graphics2D) g2.create();
            try {
                marker.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arm = 10;
                int shadowThickness = 4;
                int mainThickness = 2;

                marker.setColor(new Color(0, 0, 0, 120));
                marker.setStroke(new BasicStroke(shadowThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                marker.drawLine(centerX - arm, centerY - arm, centerX + arm, centerY + arm);
                marker.drawLine(centerX - arm, centerY + arm, centerX + arm, centerY - arm);

                marker.setColor(new Color(225, 60, 60, 245));
                marker.setStroke(new BasicStroke(mainThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                marker.drawLine(centerX - arm, centerY - arm, centerX + arm, centerY + arm);
                marker.drawLine(centerX - arm, centerY + arm, centerX + arm, centerY - arm);
            } finally {
                marker.dispose();
            }
        }

        private void paintPlayers(Graphics2D g2, RenderState state) {
            if (!playersVisible || overlayData == null || overlayData.playerPoints() == null || overlayData.playerPoints().isEmpty() || state == null) {
                return;
            }

            Graphics2D marker = (Graphics2D) g2.create();
            try {
                marker.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                marker.setFont(marker.getFont().deriveFont(Font.BOLD, 11f));

                for (PreviewPlayerPoint player : overlayData.playerPoints()) {
                    if (player == null || player.point() == null || player.username() == null || player.username().isBlank()) {
                        continue;
                    }

                    double relativeBlockX = player.point().x() - overlayData.originBlockX();
                    double relativeBlockZ = player.point().z() - overlayData.originBlockZ();
                    double imagePixelX = relativeBlockX * overlayData.pixelsPerBlock();
                    double imagePixelY = relativeBlockZ * overlayData.pixelsPerBlock();
                    int centerX = (int) Math.round(state.drawX() + imagePixelX * state.scale());
                    int centerY = (int) Math.round(state.drawY() + imagePixelY * state.scale());

                    if (centerX < state.drawX() || centerY < state.drawY()
                            || centerX >= state.drawX() + state.drawWidth()
                            || centerY >= state.drawY() + state.drawHeight()) {
                        continue;
                    }

                    String cacheKey = player.username().strip().toLowerCase(Locale.ROOT);
                    ImageIcon headIcon = PLAYER_HEAD_CACHE.get(cacheKey);
                    if (headIcon != null && headIcon.getImage() != null) {
                        int headSize = PREVIEW_PLAYER_HEAD_SIZE;
                        int headX = centerX - (headSize / 2);
                        int headY = centerY - (headSize / 2);
                        marker.setColor(new Color(0, 0, 0, 130));
                        marker.fillRoundRect(headX - 2, headY - 2, headSize + 4, headSize + 4, 6, 6);
                        marker.drawImage(headIcon.getImage(), headX, headY, null);
                    } else {
                        marker.setColor(new Color(0, 0, 0, 120));
                        marker.fillOval(centerX - 6, centerY - 6, 14, 14);
                        marker.setColor(new Color(255, 212, 74, 240));
                        marker.fillOval(centerX - 5, centerY - 5, 12, 12);
                        marker.setColor(new Color(50, 35, 12, 220));
                        marker.drawString(obtenerInicial(player.username()), centerX - 3, centerY + 4);
                    }

                    String label = player.username().length() > 10 ? player.username().substring(0, 10) : player.username();
                    FontMetrics metrics = marker.getFontMetrics();
                    int textX = centerX + 9;
                    int textY = centerY - 8;
                    int textWidth = metrics.stringWidth(label);
                    marker.setColor(new Color(0, 0, 0, 150));
                    marker.fillRoundRect(textX - 3, textY - metrics.getAscent(), textWidth + 6, metrics.getHeight(), 8, 8);
                    marker.setColor(new Color(255, 244, 204));
                    marker.drawString(label, textX, textY);
                }
            } finally {
                marker.dispose();
            }
        }

        private record RenderState(double scale, int drawX, int drawY, int drawWidth, int drawHeight,
                                   double baseX, double baseY, double maxPanX, double maxPanY) {}
    }

    private static final class PreviewSpinner extends JComponent {
        private final Timer timer;
        private int frame = 0;

        private PreviewSpinner() {
            setOpaque(false);
            setPreferredSize(new Dimension(64, 64));
            setMinimumSize(new Dimension(64, 64));
            timer = new Timer(90, e -> {
                frame = (frame + 1) % 12;
                repaint();
            });
        }

        private void setRunning(boolean running) {
            if (running) {
                if (!timer.isRunning()) {
                    timer.start();
                }
            } else {
                timer.stop();
                frame = 0;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int radius = Math.max(14, Math.min(getWidth(), getHeight()) / 2 - 10);

                for (int i = 0; i < 12; i++) {
                    double angle = Math.toRadians((i * 30) - 90);
                    int dotSize = (i == frame) ? 12 : 8;
                    int x = (int) Math.round(cx + Math.cos(angle) * radius) - (dotSize / 2);
                    int y = (int) Math.round(cy + Math.sin(angle) * radius) - (dotSize / 2);
                    int alpha = (i == frame) ? 235 : 90;
                    g2.setColor(AppTheme.withAlpha(AppTheme.getForeground(), alpha));
                    g2.fillOval(x, y, dotSize, dotSize);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    private record PreviewGenerationUpdate(boolean initialization,
                                           int canvasWidth,
                                           int canvasHeight,
                                           int defaultArgb,
                                           int drawX,
                                           int drawY,
                                           BufferedImage regionImage,
                                           int regionsCompleted,
                                           int totalRegions,
                                           double elapsedSeconds,
                                           MCARenderer.RenderStats partialStats,
                                           String statusText) {
        private static PreviewGenerationUpdate initialization(int canvasWidth, int canvasHeight, int defaultArgb, int totalRegions, String statusText) {
            return new PreviewGenerationUpdate(true, canvasWidth, canvasHeight, defaultArgb, 0, 0, null, 0, totalRegions, 0.0d, null, statusText);
        }

        private static PreviewGenerationUpdate region(int drawX,
                                                      int drawY,
                                                      BufferedImage regionImage,
                                                      int regionsCompleted,
                                                      int totalRegions,
                                                      double elapsedSeconds,
                                                      MCARenderer.RenderStats partialStats,
                                                      String statusText) {
            return new PreviewGenerationUpdate(false, 0, 0, 0, drawX, drawY, regionImage, regionsCompleted, totalRegions, elapsedSeconds, partialStats, statusText);
        }

    }

    private record PreviewGenerationResult(Path outputPath, boolean sinRegiones, BufferedImage preview, PreviewOverlayData overlayData, MCARenderer.RenderStats stats) {}
    private record CroppedPreview(BufferedImage image, int originBlockX, int originBlockZ) {}
    private record RenderWorldBounds(int minBlockX, int maxBlockX, int minBlockZ, int maxBlockZ) {}

    private static final class BracketLockedNavigationFilter extends NavigationFilter {
        private final JTextField field;

        private BracketLockedNavigationFilter(JTextField field) {
            this.field = field;
        }

        @Override
        public void setDot(FilterBypass fb, int dot, Position.Bias bias) {
            fb.setDot(clamp(dot), bias);
        }

        @Override
        public void moveDot(FilterBypass fb, int dot, Position.Bias bias) {
            fb.moveDot(clamp(dot), bias);
        }

        private int clamp(int dot) {
            int length = field.getDocument().getLength();
            int max = Math.max(1, length - 1);
            return Math.max(1, Math.min(dot, max));
        }
    }

    private static final class BracketLockedDocumentFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            replace(fb, offset, length, "", null);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            String current = asegurarCorchetes(fb.getDocument().getText(0, fb.getDocument().getLength()));
            String replacement = construirReemplazo(current, offset, length, text);
            fb.replace(0, fb.getDocument().getLength(), replacement, attrs);
        }

        private String construirReemplazo(String current, int offset, int length, String text) {
            int contentStart = 1;
            int contentEnd = current.length() - 1;
            int start = Math.max(contentStart, Math.min(offset, contentEnd));
            int end = Math.max(start, Math.min(offset + Math.max(0, length), contentEnd));
            String incoming = text == null ? "" : text.replace("[", "").replace("]", "");
            return current.substring(0, start) + incoming + current.substring(end);
        }

        private String asegurarCorchetes(String value) {
            String sanitized = value == null ? "" : value;
            if (sanitized.length() < 2) {
                return "{}";
            }
            String inner = sanitized.substring(1, sanitized.length() - 1)
                    .replace("{", "")
                    .replace("}", "");
            return "{" + inner + "}";
        }
    }

    private static final class ImageSelection implements Transferable {
        private final Image image;

        private ImageSelection(Image image) {
            this.image = image;
        }

        @Override
        public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
            return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
            return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}


