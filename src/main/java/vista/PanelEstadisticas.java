package vista;

import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.extras.components.FlatComboBox;
import com.formdev.flatlaf.extras.components.FlatScrollPane;
import com.formdev.flatlaf.extras.components.FlatSlider;
import controlador.AppPaths;
import controlador.GestorServidores;
import controlador.Utilidades;
import modelo.Server;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.ObjectMapper;

@SuppressWarnings("unused")
public class PanelEstadisticas extends JPanel {
    private static final int SAMPLE_INTERVAL_MS = 1000;
    private static final int CHART_LEFT_PADDING = 52;
    private static final int CHART_RIGHT_PADDING = 92;
    private static final int CHART_TOP_PADDING = 28;
    private static final int CHART_BOTTOM_PADDING = 28;
    private static final Pattern PLAYER_JOIN = Pattern.compile("([^\\s]+) joined the game");
    private static final Pattern PLAYER_LEFT = Pattern.compile("([^\\s]+) left the game");
    static final int[] RECENT_WINDOW_OPTIONS = {
            60 * 60,
            3 * 60 * 60,
            6 * 60 * 60,
            12 * 60 * 60,
            24 * 60 * 60,
            48 * 60 * 60,
            Server.VENTANA_RECIENTE_MAXIMA_SEGUNDOS
    };
    // Cada cuánto se escribe el historial a disco (se reescribe entero).
    static final int HISTORY_FLUSH_INTERVAL_SECONDS = 60;
    private static final int[] HISTORICAL_RESOLUTION_OPTIONS = {
            60,
            30 * 60,
            60 * 60,
            6 * 60 * 60,
            12 * 60 * 60,
            24 * 60 * 60
    };
    private static final DateTimeFormatter SAMPLE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Map<String, StatsHistory> HISTORY_BY_SERVER = new ConcurrentHashMap<>();
    private static final Map<String, CpuSampleState> CPU_SAMPLE_STATE_BY_SERVER = new ConcurrentHashMap<>();
    // RAM y disco se leen lanzando procesos externos (jstat, PowerShell, ps). Hacerlo cada segundo por
    // servidor cuesta más CPU que el propio servidor en reposo, así que se reutiliza la última lectura.
    static final long EXPENSIVE_SAMPLE_INTERVAL_MS = 5_000L;
    private static final Map<Long, TimedValue<Long>> RAM_SAMPLE_CACHE_BY_PID = new ConcurrentHashMap<>();
    private static final Map<Long, TimedValue<ProcessMetricsSample>> METRICS_SAMPLE_CACHE_BY_PID = new ConcurrentHashMap<>();
    private static final Map<Long, Integer> JSTAT_FAILURES_BY_PID = new ConcurrentHashMap<>();
    private static final int JSTAT_MAX_FAILURES = 3;
    private static final Set<GestorServidores> REGISTERED_GESTORES = ConcurrentHashMap.newKeySet();
    private static final ObjectMapper HISTORY_MAPPER = new ObjectMapper();
    private static final ScheduledExecutorService BACKGROUND_SAMPLER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "stats-background-sampler");
        thread.setDaemon(true);
        return thread;
    });
    private static final ScheduledExecutorService HISTORY_SAVER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "stats-history-saver");
        thread.setDaemon(true);
        return thread;
    });

    static {
        BACKGROUND_SAMPLER.scheduleAtFixedRate(PanelEstadisticas::sampleActiveServers, 0, SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        HISTORY_SAVER.scheduleAtFixedRate(PanelEstadisticas::flushDirtyHistories, HISTORY_FLUSH_INTERVAL_SECONDS, HISTORY_FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private final GestorServidores gestorServidores;
    private final Server server;
    private final StatsHistory statsHistory;
    private final JLabel cpuActualValueLabel = new JLabel("-");
    private final JLabel ramActualValueLabel = new JLabel("-");
    private final JLabel discoActivoValueLabel = new JLabel("-");
    private final JLabel estadoValueLabel = new JLabel("Sin datos");
    private final FlatComboBox<TimeRangeOption> rangoCombo = new FlatComboBox<>();
    private final JButton exportarGraficasButton = new FlatButton();
    private final JButton ajustesHistoricoButton = new FlatButton();
    private final JLabel posicionHistoricoLabel = new JLabel("Ventana actual");
    private final JCheckBox persistenciaCheckBox = new JCheckBox("Persistencia activa");
    private final FlatSlider ventanaRecienteSlider = new FlatSlider();
    private final FlatSlider resolucionHistoricaSlider = new FlatSlider();
    private final JCheckBox cpuActivaCheckBox = new JCheckBox();
    private final JCheckBox cpuPersistenciaCheckBox = new JCheckBox();
    private final JCheckBox ramActivaCheckBox = new JCheckBox();
    private final JCheckBox ramPersistenciaCheckBox = new JCheckBox();
    private final JCheckBox discoActivaCheckBox = new JCheckBox();
    private final JCheckBox discoPersistenciaCheckBox = new JCheckBox();
    private final JCheckBox jugadoresActivaCheckBox = new JCheckBox();
    private final JCheckBox jugadoresPersistenciaCheckBox = new JCheckBox();
    private final JButton reiniciarHistoricoButton = new FlatButton();
    private final JButton guardarHistoricoButton = new FlatButton();
    private final JLabel ventanaRecienteValueLabel = new JLabel();
    private final JLabel resolucionHistoricaValueLabel = new JLabel();
    private final UsageChart cpuChart = new UsageChart("CPU", UsageMode.PERCENT, "Esperando muestras de CPU...");
    private final UsageChart ramChart = new UsageChart("RAM", UsageMode.MEGABYTES, "Esperando muestras de RAM...");
    private final UsageChart diskChart = new UsageChart("Disco", UsageMode.PERCENT, "Esperando actividad de disco...");
    private final UsageChart playersChart = new UsageChart("Jugadores", UsageMode.COUNT, "Esperando actividad de jugadores...");
    private final JPanel chartsPanel = new JPanel();
    private final JScrollPane chartsScrollPane = new FlatScrollPane();
    private final JScrollBar historialScrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
    private Timer refreshTimer;
    private final PropertyChangeListener estadoServidorListener;
    private boolean estadoListenerRegistrado = false;
    private boolean updatingSettingsControls = false;
    private boolean updatingTimelineControls = false;
    private int timelineMaxOffsetSeconds = 0;
    private boolean persistedStatsPersistenceEnabled = true;
    private int persistedStatsRecentWindowSeconds = Server.VENTANA_RECIENTE_POR_DEFECTO_SEGUNDOS;
    private int persistedStatsHistoricalResolutionSeconds = 60;
    private final EnumMap<StatsChartOption, Boolean> pendingChartEnabled = new EnumMap<>(StatsChartOption.class);
    private final EnumMap<StatsChartOption, Boolean> pendingChartPersistenceEnabled = new EnumMap<>(StatsChartOption.class);
    private final EnumMap<StatsChartOption, Boolean> persistedChartEnabled = new EnumMap<>(StatsChartOption.class);
    private final EnumMap<StatsChartOption, Boolean> persistedChartPersistenceEnabled = new EnumMap<>(StatsChartOption.class);

    PanelEstadisticas(GestorServidores gestorServidores, Server server) {
        this.gestorServidores = gestorServidores;
        this.server = server;
        this.statsHistory = getOrCreateHistory(server);
        setLayout(new BorderLayout());
        setOpaque(false);

        CardPanel card = new CardPanel("Estadísticas");
        card.setBorder(BorderFactory.createEmptyBorder());
        card.getContentPanel().add(crearContenido(), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        exportarGraficasButton.setText("Exportar gráficas");
        ajustesHistoricoButton.setText("Configuración");
        reiniciarHistoricoButton.setText("Reiniciar");
        guardarHistoricoButton.setText("Guardar");
        rangoCombo.setModel(new DefaultComboBoxModel<>(TimeRangeOption.values()));
        rangoCombo.setRoundRect(true);
        rangoCombo.setSelectedItem(TimeRangeOption.fromSeconds(getStatsRangeSeconds()));
        rangoCombo.addActionListener(e -> {
            TimeRangeOption selectedRange = getSelectedRange();
            if (server != null) {
                server.setEstadisticasRangoSegundos(selectedRange.seconds());
                guardarConfiguracionServidor();
            }
            refrescarGraficasDesdeHistorial();
        });
        ajustesHistoricoButton.setFocusable(false);
        exportarGraficasButton.setFocusable(false);
        exportarGraficasButton.addActionListener(e -> abrirDialogoExportacion());
        styleActionButton(exportarGraficasButton);
        ajustesHistoricoButton.addActionListener(e -> abrirDialogoHistorico());
        styleActionButton(ajustesHistoricoButton);
        styleActionButton(reiniciarHistoricoButton);
        styleActionButton(guardarHistoricoButton);
        historialScrollBar.setFocusable(false);
        historialScrollBar.setUnitIncrement(60);
        historialScrollBar.addAdjustmentListener(e -> {
            if (updatingTimelineControls) {
                return;
            }
            refrescarGraficasDesdeHistorial();
        });
        posicionHistoricoLabel.setForeground(AppTheme.getMutedForeground());
        instalarNavegacionConRueda(cpuChart);
        instalarNavegacionConRueda(ramChart);
        instalarNavegacionConRueda(diskChart);
        instalarNavegacionConRueda(playersChart);
        instalarNavegacionConRueda(chartsPanel);
        instalarNavegacionConRueda(chartsScrollPane);
        instalarNavegacionConRueda(chartsScrollPane.getViewport());
        instalarNavegacionConRueda(historialScrollBar);
        instalarNavegacionConRueda(posicionHistoricoLabel);
        chartsScrollPane.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                updateChartHeights();
            }
        });
        configurarControlesHistorico();

        estadoServidorListener = evt -> {
            if (!"estadoServidor".equals(evt.getPropertyName())) {
                return;
            }
            Object value = evt.getNewValue();
            if (!(value instanceof Server updatedServer) || !esMismoServidor(updatedServer)) {
                return;
            }
            SwingUtilities.invokeLater(this::solicitarMuestras);
        };
        iniciarRecursosSiHaceFalta();
        refrescarGraficasDesdeHistorial();
        SwingUtilities.invokeLater(this::solicitarMuestras);
    }

    private JPanel crearContenido() {
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);

        JPanel header = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        header.setOpaque(false);

        JPanel rangoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rangoPanel.setOpaque(false);
        JLabel rangoLabel = new JLabel("Rango: ");
        rangoLabel.setForeground(AppTheme.getMutedForeground());
        rangoPanel.add(rangoLabel);
        rangoCombo.setFocusable(false);
        rangoPanel.add(rangoCombo);
        rangoPanel.add(Box.createHorizontalStrut(8));
        rangoPanel.add(exportarGraficasButton);
        rangoPanel.add(Box.createHorizontalStrut(8));
        rangoPanel.add(crearSeparadorVertical(24));
        rangoPanel.add(Box.createHorizontalStrut(8));
        rangoPanel.add(ajustesHistoricoButton);
        header.add(rangoPanel);
        content.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(crearResumen(), BorderLayout.NORTH);

        chartsPanel.setOpaque(false);
        chartsPanel.setLayout(new BoxLayout(chartsPanel, BoxLayout.Y_AXIS));
        rebuildChartsPanel();

        chartsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        chartsScrollPane.setOpaque(false);
        chartsScrollPane.getViewport().setOpaque(false);
        chartsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        chartsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        chartsScrollPane.getVerticalScrollBar().setUnitIncrement(24);
        chartsScrollPane.setViewportView(chartsPanel);
        configurarScrollEstadisticas();

        center.add(chartsScrollPane, BorderLayout.CENTER);
        center.add(crearNavegacionHistorico(), BorderLayout.SOUTH);
        content.add(center, BorderLayout.CENTER);
        return content;
    }

    private JPanel crearNavegacionHistorico() {
        JPanel navigation = new JPanel(new BorderLayout(8, 0));
        navigation.setOpaque(false);
        navigation.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        posicionHistoricoLabel.setPreferredSize(new Dimension(240, 24));
        navigation.add(posicionHistoricoLabel, BorderLayout.WEST);
        navigation.add(historialScrollBar, BorderLayout.CENTER);
        return navigation;
    }

    private void rebuildChartsPanel() {
        chartsPanel.removeAll();
        List<UsageChart> visibleCharts = new ArrayList<>();
        if (isChartEnabledForUi(StatsChartOption.CPU)) {
            visibleCharts.add(cpuChart);
        }
        if (isChartEnabledForUi(StatsChartOption.RAM)) {
            visibleCharts.add(ramChart);
        }
        if (isChartEnabledForUi(StatsChartOption.DISK)) {
            visibleCharts.add(diskChart);
        }
        if (isChartEnabledForUi(StatsChartOption.PLAYERS)) {
            visibleCharts.add(playersChart);
        }
        for (int i = 0; i < visibleCharts.size(); i++) {
            UsageChart chart = visibleCharts.get(i);
            chart.setAlignmentX(Component.LEFT_ALIGNMENT);
            chartsPanel.add(chart);
            if (i < visibleCharts.size() - 1) {
                chartsPanel.add(Box.createVerticalStrut(8));
            }
        }
        updateChartHeights();
        chartsPanel.revalidate();
        chartsPanel.repaint();
    }

    private void updateChartHeights() {
        List<UsageChart> visibleCharts = new ArrayList<>();
        if (isChartEnabledForUi(StatsChartOption.CPU)) visibleCharts.add(cpuChart);
        if (isChartEnabledForUi(StatsChartOption.RAM)) visibleCharts.add(ramChart);
        if (isChartEnabledForUi(StatsChartOption.DISK)) visibleCharts.add(diskChart);
        if (isChartEnabledForUi(StatsChartOption.PLAYERS)) visibleCharts.add(playersChart);
        if (visibleCharts.isEmpty()) {
            return;
        }
        int spacing = 8;
        int viewportHeight = Math.max(0, chartsScrollPane.getViewport().getHeight());
        int availableHeight = viewportHeight > 0 ? viewportHeight : (visibleCharts.size() * 180);
        int calculatedHeight = (availableHeight - (Math.max(0, visibleCharts.size() - 1) * spacing)) / visibleCharts.size();
        int chartHeight = Math.max(128, Math.min(210, calculatedHeight));
        for (UsageChart chart : visibleCharts) {
            chart.setPreferredChartHeight(chartHeight);
        }
    }

    private boolean isChartEnabledForUi(StatsChartOption option) {
        if (pendingChartEnabled.containsKey(option)) {
            return Boolean.TRUE.equals(pendingChartEnabled.get(option));
        }
        return isChartEnabled(option);
    }

    private JComponent crearSeparadorVertical(int height) {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setForeground(AppTheme.getSubtleBorderColor());
        separator.setPreferredSize(new Dimension(1, height));
        separator.setMaximumSize(new Dimension(1, height));
        return separator;
    }

    private void instalarNavegacionConRueda(Component component) {
        if (component == null) {
            return;
        }
        component.addMouseWheelListener(this::manejarRuedaHistorico);
    }

    private void manejarRuedaHistorico(MouseWheelEvent event) {
        if (!event.isShiftDown()) {
            desplazarScrollVertical(event);
            return;
        }
        if (!historialScrollBar.isEnabled()) {
            return;
        }
        int step = event.isShiftDown() ? historialScrollBar.getBlockIncrement() : historialScrollBar.getUnitIncrement();
        int delta = event.getWheelRotation() * Math.max(1, step);
        int nextValue = Math.max(historialScrollBar.getMinimum(),
                Math.min(historialScrollBar.getMaximum() - historialScrollBar.getVisibleAmount(), historialScrollBar.getValue() + delta));
        if (nextValue != historialScrollBar.getValue()) {
            historialScrollBar.setValue(nextValue);
        }
        event.consume();
    }

    private void desplazarScrollVertical(MouseWheelEvent event) {
        if (chartsScrollPane == null) {
            return;
        }
        JScrollBar verticalBar = chartsScrollPane.getVerticalScrollBar();
        if (verticalBar == null || !verticalBar.isVisible() || !verticalBar.isEnabled()) {
            return;
        }
        int scrollAmount = Math.max(1, event.getScrollAmount());
        int step = verticalBar.getUnitIncrement() * scrollAmount;
        int delta = event.getWheelRotation() * Math.max(1, step);
        int nextValue = Math.max(verticalBar.getMinimum(),
                Math.min(verticalBar.getMaximum() - verticalBar.getVisibleAmount(), verticalBar.getValue() + delta));
        if (nextValue != verticalBar.getValue()) {
            verticalBar.setValue(nextValue);
        }
        event.consume();
    }

    private void configurarScrollEstadisticas() {
        JScrollBar verticalBar = chartsScrollPane.getVerticalScrollBar();
        if (verticalBar != null) {
            verticalBar.setBackground(AppTheme.getSurfaceBackground());
            verticalBar.setForeground(AppTheme.getMainAccent());
        }
        historialScrollBar.setOpaque(true);
        historialScrollBar.setBackground(AppTheme.tint(AppTheme.getSurfaceBackground(), AppTheme.getForeground(), 0.08f));
        historialScrollBar.setForeground(AppTheme.getMainAccent());
        historialScrollBar.setPreferredSize(new Dimension(0, 16));
        historialScrollBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.getBorderColor(), 1, true),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
    }

    private JPanel crearResumen() {
        JPanel summary = new JPanel(new GridLayout(1, 4, 8, 0));
        summary.setOpaque(false);
        summary.add(crearTarjetaResumen("CPU actual", cpuActualValueLabel));
        summary.add(crearTarjetaResumen("RAM actual", ramActualValueLabel));
        summary.add(crearTarjetaResumen("Disco activo", discoActivoValueLabel));
        summary.add(crearTarjetaResumen("Estado", estadoValueLabel));
        return summary;
    }

    private JPanel crearTarjetaResumen(String title, JLabel valueLabel) {
        CardPanel card = new CardPanel(title);
        card.setBackground(AppTheme.getSurfaceBackground());
        JLabel titleLabel = card.getTitleLabel();
        titleLabel.setForeground(AppTheme.getMutedForeground());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));

        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 16f));
        valueLabel.setForeground(AppTheme.getForeground());

        card.getContentPanel().add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel crearPanelAjustesHistorico() {
        CardPanel card = new CardPanel("Configuración");
        card.setBackground(AppTheme.getSurfaceBackground());
        JLabel titulo = card.getTitleLabel();
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 13f));
        titulo.setForeground(AppTheme.getForeground());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        persistenciaCheckBox.setOpaque(false);
        persistenciaCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(persistenciaCheckBox);
        content.add(Box.createVerticalStrut(8));
        content.add(crearFilaSlider("Ventana reciente", ventanaRecienteSlider, ventanaRecienteValueLabel));
        content.add(Box.createVerticalStrut(8));
        content.add(crearFilaSlider("Resolución histórica", resolucionHistoricaSlider, resolucionHistoricaValueLabel));

        content.add(Box.createVerticalStrut(12));
        content.add(crearTablaConfiguracionGraficas());

        card.getContentPanel().add(content, BorderLayout.CENTER);
        return card;
    }

    private void abrirDialogoHistorico() {
        cargarAjustesHistoricoPersistidos();
        cargarControlesHistoricoDesdePersistidos();
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner instanceof Frame frame ? frame : null, "Configuración", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(true);
        wrapper.setBackground(AppTheme.getBackground());
        wrapper.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        wrapper.add(crearPanelAjustesHistorico(), BorderLayout.CENTER);

        JButton cerrarButton = new FlatButton();
        cerrarButton.setText("Cerrar");
        styleActionButton(cerrarButton);
        cerrarButton.addActionListener(e -> dialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        footer.add(reiniciarHistoricoButton);
        footer.add(guardarHistoricoButton);
        footer.add(cerrarButton);
        wrapper.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(wrapper);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(520, dialog.getPreferredSize().height));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel crearFilaSlider(String titulo, JSlider slider, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(titulo);
        titleLabel.setForeground(AppTheme.getMutedForeground());
        titleLabel.setPreferredSize(new Dimension(140, 24));

        valueLabel.setForeground(AppTheme.getForeground());
        valueLabel.setPreferredSize(new Dimension(90, 24));

        slider.setOpaque(false);

        row.add(titleLabel, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    private JPanel crearFilaCombo(String titulo, JComponent component) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(titulo);
        titleLabel.setForeground(AppTheme.getMutedForeground());
        titleLabel.setPreferredSize(new Dimension(140, 24));

        row.add(titleLabel, BorderLayout.WEST);
        row.add(component, BorderLayout.CENTER);
        return row;
    }


    private JPanel crearTablaConfiguracionGraficas() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Graficas");
        titleLabel.setForeground(AppTheme.getMutedForeground());
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(6));

        JPanel table = new JPanel(new GridBagLayout());
        table.setOpaque(false);
        table.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 6, 10);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        table.add(new JLabel("Metrica"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        table.add(new JLabel("Activa"), gbc);

        gbc.gridx = 2;
        table.add(new JLabel("Persistencia"), gbc);

        addGraficaConfigRow(table, 1, StatsChartOption.CPU, cpuActivaCheckBox, cpuPersistenciaCheckBox);
        addGraficaConfigRow(table, 2, StatsChartOption.RAM, ramActivaCheckBox, ramPersistenciaCheckBox);
        addGraficaConfigRow(table, 3, StatsChartOption.DISK, discoActivaCheckBox, discoPersistenciaCheckBox);
        addGraficaConfigRow(table, 4, StatsChartOption.PLAYERS, jugadoresActivaCheckBox, jugadoresPersistenciaCheckBox);

        panel.add(table);
        return panel;
    }

    private void addGraficaConfigRow(JPanel table,
                                     int rowIndex,
                                     StatsChartOption option,
                                     JCheckBox activaCheckBox,
                                     JCheckBox persistenciaRowCheckBox) {
        activaCheckBox.setOpaque(false);
        persistenciaRowCheckBox.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = rowIndex;
        gbc.insets = new Insets(2, 0, 2, 10);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel label = new JLabel(option.toString());
        label.setForeground(AppTheme.getForeground());
        table.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        table.add(activaCheckBox, gbc);

        gbc.gridx = 2;
        table.add(persistenciaRowCheckBox, gbc);
    }


    private void configurarControlesHistorico() {
        ventanaRecienteSlider.setOpaque(false);
        ventanaRecienteSlider.setMinimum(0);
        ventanaRecienteSlider.setMaximum(RECENT_WINDOW_OPTIONS.length - 1);
        ventanaRecienteSlider.setPaintTicks(true);
        ventanaRecienteSlider.setMajorTickSpacing(1);
        ventanaRecienteSlider.setSnapToTicks(true);

        resolucionHistoricaSlider.setOpaque(false);
        resolucionHistoricaSlider.setMinimum(0);
        resolucionHistoricaSlider.setMaximum(HISTORICAL_RESOLUTION_OPTIONS.length - 1);
        resolucionHistoricaSlider.setPaintTicks(true);
        resolucionHistoricaSlider.setMajorTickSpacing(1);
        resolucionHistoricaSlider.setSnapToTicks(true);

        persistenciaCheckBox.addActionListener(e -> {
            if (updatingSettingsControls) return;
            updateHistoricoSaveButtonState();
        });

        ventanaRecienteSlider.addChangeListener(e -> {
            int value = RECENT_WINDOW_OPTIONS[ventanaRecienteSlider.getValue()];
            ventanaRecienteValueLabel.setText(formatRecentWindowValue(value));
            if (updatingSettingsControls) return;
            updateHistoricoSaveButtonState();
        });

        resolucionHistoricaSlider.addChangeListener(e -> {
            int value = HISTORICAL_RESOLUTION_OPTIONS[resolucionHistoricaSlider.getValue()];
            resolucionHistoricaValueLabel.setText(formatHistoricalResolutionValue(value));
            if (updatingSettingsControls) return;
            updateHistoricoSaveButtonState();
        });

        cpuActivaCheckBox.addActionListener(e -> actualizarGraficaVisiblePendiente(StatsChartOption.CPU, cpuActivaCheckBox.isSelected()));
        ramActivaCheckBox.addActionListener(e -> actualizarGraficaVisiblePendiente(StatsChartOption.RAM, ramActivaCheckBox.isSelected()));
        discoActivaCheckBox.addActionListener(e -> actualizarGraficaVisiblePendiente(StatsChartOption.DISK, discoActivaCheckBox.isSelected()));
        jugadoresActivaCheckBox.addActionListener(e -> actualizarGraficaVisiblePendiente(StatsChartOption.PLAYERS, jugadoresActivaCheckBox.isSelected()));
        cpuPersistenciaCheckBox.addActionListener(e -> actualizarPersistenciaGraficaPendiente(StatsChartOption.CPU, cpuPersistenciaCheckBox.isSelected()));
        ramPersistenciaCheckBox.addActionListener(e -> actualizarPersistenciaGraficaPendiente(StatsChartOption.RAM, ramPersistenciaCheckBox.isSelected()));
        discoPersistenciaCheckBox.addActionListener(e -> actualizarPersistenciaGraficaPendiente(StatsChartOption.DISK, discoPersistenciaCheckBox.isSelected()));
        jugadoresPersistenciaCheckBox.addActionListener(e -> actualizarPersistenciaGraficaPendiente(StatsChartOption.PLAYERS, jugadoresPersistenciaCheckBox.isSelected()));

        reiniciarHistoricoButton.addActionListener(e -> cargarControlesHistoricoDesdePersistidos());
        guardarHistoricoButton.addActionListener(e -> guardarAjustesHistorico());

        cargarAjustesHistoricoPersistidos();
        cargarControlesHistoricoDesdePersistidos();
    }

    private void solicitarMuestras() {
        if (gestorServidores != null) {
            REGISTERED_GESTORES.add(gestorServidores);
        }
        cpuChart.setMaxValue(100);
        int ramMaxMb = resolveMaxRamMb();
        int maxPlayers = resolveMaxPlayers();
        ramChart.setMaxValue(ramMaxMb);
        diskChart.setMaxValue(100);
        playersChart.setMaxValue(maxPlayers);

        if (server == null) {
            refrescarGraficasDesdeHistorial();
            cpuActualValueLabel.setText("-");
            ramActualValueLabel.setText("-");
            discoActivoValueLabel.setText("-");
            estadoValueLabel.setText("Sin servidor");
            return;
        }

        Process proceso = server.getServerProcess();
        if (proceso == null || !proceso.isAlive()) {
            refrescarGraficasDesdeHistorial();
            cpuActualValueLabel.setText(buildCpuText(statsHistory.lastCpuPercent(), false));
            ramActualValueLabel.setText(buildRamText(statsHistory.lastRamValue(), ramMaxMb, false));
            discoActivoValueLabel.setText(buildDiskText((int) Math.round(Math.max(0d, Math.min(100d, statsHistory.lastDiskPercent()))), statsHistory.lastDiskSample()));
            estadoValueLabel.setText("Apagado");
            return;
        }
        refrescarGraficasDesdeHistorial();
        cpuActualValueLabel.setText(buildCpuText(statsHistory.lastCpuPercent(), true));
        ramActualValueLabel.setText(buildRamText(statsHistory.lastRamValue(), ramMaxMb, true));
        discoActivoValueLabel.setText(buildDiskText((int) Math.round(Math.max(0d, Math.min(100d, statsHistory.lastDiskPercent()))), statsHistory.lastDiskSample()));
        estadoValueLabel.setText("En ejecución");
    }

    private long normalizeUsage(long usageMb, long maxMb) {
        long normalizedMb = Math.max(0L, usageMb);
        if (maxMb > 0L) {
            normalizedMb = Math.min(normalizedMb, maxMb);
        }
        return normalizedMb;
    }

    private String buildRamText(long usageMb, long maxMb, boolean mostrarPorcentaje) {
        if (maxMb <= 0L) {
            return formatMb(usageMb);
        }
        if (!mostrarPorcentaje) {
            return formatMb(usageMb) + " / " + formatMb(maxMb);
        }
        double percentage = (usageMb * 100.0d) / maxMb;
        return formatMb(usageMb) + " / " + formatMb(maxMb) + " (" + Math.round(percentage) + "%)";
    }

    private String buildCpuText(double cpuPercent, boolean mostrarEtiquetaTiempoReal) {
        int percent = (int) Math.round(Math.max(0d, Math.min(100d, cpuPercent)));
        return mostrarEtiquetaTiempoReal ? percent + "% en uso" : percent + "%";
    }

    private String buildDiskText(int diskPercent, DiskActivitySample diskSample) {
        return diskPercent + "%  L " + formatRate(diskSample.readBytesPerSec()) + "  E " + formatRate(diskSample.writeBytesPerSec());
    }

    static ServerResourceSnapshot getLiveResourceSnapshot(Server server) {
        StatsHistory history = getOrCreateHistory(server);
        long ramMaxMb = 1L;
        if (server != null && server.getServerConfig() != null) {
            ramMaxMb = Math.max(1L, server.getServerConfig().getRamMax());
        }
        boolean running = false;
        if (server != null) {
            Process process = server.getServerProcess();
            running = process != null && process.isAlive();
        }
        if (!running) {
            // Un servidor apagado no consume nada: no mostrar la última lectura (por ejemplo la RAM
            // cargada del histórico de otra sesión o la muestra previa a la parada).
            return new ServerResourceSnapshot(false, 0d, 0L, ramMaxMb, 0d);
        }
        return new ServerResourceSnapshot(
                true,
                history.lastCpuPercent(),
                history.lastRamValue(),
                ramMaxMb,
                history.lastDiskPercent()
        );
    }

    static void registrarGestorParaMuestras(GestorServidores gestorServidores) {
        if (gestorServidores != null) {
            REGISTERED_GESTORES.add(gestorServidores);
        }
    }

    private int resolveMaxRamMb() {
        if (server == null || server.getServerConfig() == null) {
            return 1;
        }
        return Math.max(1, server.getServerConfig().getRamMax());
    }

    private int resolveMaxPlayers() {
        try {
            if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
                return 20;
            }
            Path propertiesPath = Path.of(server.getServerDir()).resolve("server.properties");
            if (!Files.exists(propertiesPath)) {
                return 20;
            }
            Properties props = Utilidades.cargarPropertiesUtf8(propertiesPath);
            String value = props.getProperty("max-players");
            if (value == null || value.isBlank()) {
                return 20;
            }
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (IOException | NumberFormatException ignored) {
            return 20;
        }
    }

    private boolean esMismoServidor(Server other) {
        if (server == null || other == null) {
            return false;
        }
        return server.getId() != null && server.getId().equals(other.getId());
    }

    private void detenerRecursos() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        if (gestorServidores != null && estadoListenerRegistrado) {
            gestorServidores.removePropertyChangeListener("estadoServidor", estadoServidorListener);
            estadoListenerRegistrado = false;
        }
        refreshTimer = null;
    }

    private void iniciarRecursosSiHaceFalta() {
        if (refreshTimer == null) {
            refreshTimer = new Timer(SAMPLE_INTERVAL_MS, e -> solicitarMuestras());
            refreshTimer.setInitialDelay(0);
        }
        if (!refreshTimer.isRunning()) {
            refreshTimer.start();
        }
        if (gestorServidores != null && !estadoListenerRegistrado) {
            gestorServidores.addPropertyChangeListener("estadoServidor", estadoServidorListener);
            estadoListenerRegistrado = true;
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        iniciarRecursosSiHaceFalta();
        SwingUtilities.invokeLater(this::solicitarMuestras);
    }

    @Override
    public void removeNotify() {
        detenerRecursos();
        super.removeNotify();
    }

    private static long readServerRamUsageMb(Server server) {
        if (server == null) {
            return -1L;
        }
        Process process = server.getServerProcess();
        if (process == null || !process.isAlive()) {
            return -1L;
        }

        long rootPid = process.pid();
        long now = System.currentTimeMillis();
        TimedValue<Long> cached = RAM_SAMPLE_CACHE_BY_PID.get(rootPid);
        if (cached != null && now - cached.atMillis() < EXPENSIVE_SAMPLE_INTERVAL_MS) {
            return cached.value();
        }
        long value = readServerRamUsageMbUncached(resolveServerJvm(process).pid());
        RAM_SAMPLE_CACHE_BY_PID.put(rootPid, new TimedValue<>(value, now));
        return value;
    }

    private static long readServerRamUsageMbUncached(long pid) {
        long heapUsageMb = -1L;
        if (JSTAT_FAILURES_BY_PID.getOrDefault(pid, 0) < JSTAT_MAX_FAILURES) {
            heapUsageMb = readHeapUsageWithJstat(pid);
            if (heapUsageMb < 0L) {
                // Sin jstat (solo JRE) o el proceso no es una JVM accesible: tras varios fallos dejamos de lanzarlo.
                JSTAT_FAILURES_BY_PID.merge(pid, 1, Integer::sum);
            } else {
                JSTAT_FAILURES_BY_PID.remove(pid);
            }
        }
        if (heapUsageMb > 0L) {
            return heapUsageMb;
        }

        long residentUsageMb = readResidentUsageByOs(pid);
        if (residentUsageMb >= 0L) {
            return residentUsageMb;
        }
        return heapUsageMb >= 0L ? heapUsageMb : -1L;
    }

    /**
     * Proceso de la JVM del servidor. Forge/NeoForge arrancan con run.bat/run.sh, así que el proceso
     * que guarda Dora es cmd.exe/sh y la JVM real es un hijo: medir el padre daría ~0% de CPU y la RAM
     * del intérprete de comandos.
     */
    static ProcessHandle resolveServerJvm(Process process) {
        ProcessHandle root = process.toHandle();
        if (looksLikeJava(root)) {
            return root;
        }
        try {
            return root.descendants()
                    .filter(ProcessHandle::isAlive)
                    .filter(PanelEstadisticas::looksLikeJava)
                    .findFirst()
                    .orElse(root);
        } catch (RuntimeException e) {
            return root;
        }
    }

    private static boolean looksLikeJava(ProcessHandle handle) {
        String command = handle.info().command().orElse("");
        String name = Path.of(command).getFileName() == null ? "" : Path.of(command).getFileName().toString().toLowerCase(Locale.ROOT);
        return name.equals("java") || name.equals("java.exe") || name.equals("javaw.exe");
    }

    static void clearExpensiveSampleCachesForTests() {
        RAM_SAMPLE_CACHE_BY_PID.clear();
        METRICS_SAMPLE_CACHE_BY_PID.clear();
        JSTAT_FAILURES_BY_PID.clear();
    }

    private static DiskActivitySample readServerDiskActivity(Server server) {
        if (server == null) {
            return new DiskActivitySample(0d, 0L, 0L);
        }
        Process process = server.getServerProcess();
        if (process == null || !process.isAlive()) {
            return new DiskActivitySample(0d, 0L, 0L);
        }

        long pid = process.pid();
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return readWindowsProcessMetrics(pid).diskSample();
        }
        return new DiskActivitySample(0d, 0L, 0L);
    }

    private static ProcessMetricsSample readWindowsProcessMetrics(long pid) {
        String script =
                "$targetPid = " + pid + "; " +
                "$proc = Get-CimInstance Win32_PerfFormattedData_PerfProc_Process | " +
                "Where-Object { $_.IDProcess -eq $targetPid } | Select-Object -First 1; " +
                "$disk = Get-CimInstance Win32_PerfFormattedData_PerfDisk_PhysicalDisk | " +
                "Where-Object { $_.Name -eq '_Total' } | Select-Object -First 1; " +
                "if ($proc -and $disk) { " +
                "[Console]::Out.Write(($proc.IOReadBytesPersec) + ';' + ($proc.IOWriteBytesPersec) + ';' + " +
                "($disk.DiskReadBytesPersec) + ';' + ($disk.DiskWriteBytesPersec) + ';' + " +
                "($disk.PercentDiskReadTime) + ';' + ($disk.PercentDiskWriteTime) + ';' + ($disk.PercentDiskTime)) }";

        String output = ejecutarComando(List.of("powershell", "-NoProfile", "-Command", script), 900);
        if (output == null || output.isBlank()) {
            return new ProcessMetricsSample(0d, new DiskActivitySample(0d, 0L, 0L));
        }

        String[] parts = output.trim().split(";");
        if (parts.length < 7) {
            return new ProcessMetricsSample(0d, new DiskActivitySample(0d, 0L, 0L));
        }

        long processReadBytes = parseLong(parts[0]);
        long processWriteBytes = parseLong(parts[1]);
        long totalReadBytes = Math.max(0L, parseLong(parts[2]));
        long totalWriteBytes = Math.max(0L, parseLong(parts[3]));
        double totalReadPercent = parseDouble(parts[4]);
        double totalWritePercent = parseDouble(parts[5]);
        double totalPercent = parseDouble(parts[6]);

        double percent = estimateProcessDiskPercent(
                processReadBytes,
                processWriteBytes,
                totalReadBytes,
                totalWriteBytes,
                totalReadPercent,
                totalWritePercent,
                totalPercent
        );
        return new ProcessMetricsSample(
                0d,
                new DiskActivitySample(percent, Math.max(0L, processReadBytes), Math.max(0L, processWriteBytes))
        );
    }

    private static double estimateProcessDiskPercent(long processReadBytes,
                                                     long processWriteBytes,
                                                     long totalReadBytes,
                                                     long totalWriteBytes,
                                                     double totalReadPercent,
                                                     double totalWritePercent,
                                                     double totalPercent) {
        double readContribution = 0d;
        double writeContribution = 0d;

        if (totalReadBytes > 0L && totalReadPercent > 0d) {
            readContribution = totalReadPercent * (processReadBytes / (double) totalReadBytes);
        }
        if (totalWriteBytes > 0L && totalWritePercent > 0d) {
            writeContribution = totalWritePercent * (processWriteBytes / (double) totalWriteBytes);
        }

        double estimatedPercent = readContribution + writeContribution;
        if (estimatedPercent <= 0d && totalPercent > 0d) {
            long processTotalBytes = Math.max(0L, processReadBytes) + Math.max(0L, processWriteBytes);
            long diskTotalBytes = Math.max(0L, totalReadBytes) + Math.max(0L, totalWriteBytes);
            if (diskTotalBytes > 0L) {
                estimatedPercent = totalPercent * (processTotalBytes / (double) diskTotalBytes);
            }
        }

        return Math.max(0d, Math.min(100d, estimatedPercent));
    }

    private static long readHeapUsageWithJstat(long pid) {
        String output = ejecutarComando(List.of(resolveJstatCommand(), "-J-Duser.language=en", "-J-Duser.country=US", "-gc", String.valueOf(pid)), 700);
        if (output == null || output.isBlank()) {
            return -1L;
        }

        String[] lines = output.strip().split("\\R");
        if (lines.length < 2) {
            return -1L;
        }

        String headerLine = lines[0].trim();
        String valueLine = lines[lines.length - 1].trim();
        if (headerLine.isBlank() || valueLine.isBlank()) {
            return -1L;
        }

        String[] headers = headerLine.split("\\s+");
        String[] values = valueLine.split("\\s+");
        if (headers.length != values.length) {
            return -1L;
        }

        double[] heapColumns = {
                readDoubleColumn(headers, values, "S0U"),
                readDoubleColumn(headers, values, "S1U"),
                readDoubleColumn(headers, values, "EU"),
                readDoubleColumn(headers, values, "OU")
        };
        double usedKb = 0d;
        int parsedColumns = 0;
        for (double heapColumn : heapColumns) {
            if (Double.isNaN(heapColumn)) {
                continue;
            }
            usedKb += heapColumn;
            parsedColumns++;
        }

        if (parsedColumns == 0) {
            return -1L;
        }
        if (usedKb <= 0d) {
            return 0L;
        }
        return Math.round(usedKb / 1024d);
    }

    // Preferimos el jstat del JDK con el que corre Dora; si no está (JRE), el del PATH.
    private static String resolveJstatCommand() {
        try {
            String javaCommand = ProcessHandle.current().info().command().orElse(null);
            if (javaCommand != null) {
                Path binDir = Path.of(javaCommand).getParent();
                if (binDir != null) {
                    for (String name : List.of("jstat", "jstat.exe")) {
                        Path candidate = binDir.resolve(name);
                        if (Files.isExecutable(candidate)) {
                            return candidate.toString();
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return "jstat";
    }

    private static double readDoubleColumn(String[] headers, String[] values, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (!name.equalsIgnoreCase(headers[i])) {
                continue;
            }
            try {
                return parseLocalizedDouble(values[i]);
            } catch (NumberFormatException ignored) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    private static long readResidentUsageByOs(long pid) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return readResidentUsageWindows(pid);
        }
        return readResidentUsageUnix(pid);
    }

    private static long readResidentUsageWindows(long pid) {
        String command = "$targetPid = " + pid + "; $p = Get-Process -Id $targetPid -ErrorAction SilentlyContinue; "
                + "if ($p) { [Console]::Out.Write($p.WorkingSet64) }";
        String output = ejecutarComando(List.of("powershell", "-NoProfile", "-Command", command), 700);
        if (output == null || output.isBlank()) {
            return -1L;
        }
        try {
            long bytes = Long.parseLong(output.trim());
            return Math.round(bytes / 1024d / 1024d);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static long readResidentUsageUnix(long pid) {
        long fromProc = readResidentUsageFromProc(pid);
        if (fromProc >= 0L) {
            return fromProc;
        }
        String output = ejecutarComando(List.of("ps", "-o", "rss=", "-p", String.valueOf(pid)), 700);
        if (output == null || output.isBlank()) {
            return -1L;
        }
        try {
            long kb = Long.parseLong(output.trim());
            return Math.round(kb / 1024d);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    // Linux: /proc/<pid>/status se lee sin lanzar ningún proceso.
    private static long readResidentUsageFromProc(long pid) {
        Path status = Path.of("/proc", String.valueOf(pid), "status");
        if (!Files.isRegularFile(status)) {
            return -1L;
        }
        try {
            for (String line : Files.readAllLines(status, StandardCharsets.UTF_8)) {
                if (line.startsWith("VmRSS:")) {
                    String[] parts = line.substring("VmRSS:".length()).trim().split("\\s+");
                    return Math.round(Long.parseLong(parts[0]) / 1024d);
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return -1L;
    }

    private static String ejecutarComando(List<String> command, long timeoutMs) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                process.getInputStream().transferTo(output);
                return output.toString(StandardCharsets.UTF_8).trim();
            }
        } catch (IOException | InterruptedException ignored) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static double parseDouble(String value) {
        try {
            return parseLocalizedDouble(value);
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private static double parseLocalizedDouble(String value) {
        if (value == null) {
            throw new NumberFormatException("null");
        }
        return Double.parseDouble(value.trim().replace(',', '.'));
    }

    private String formatMb(long mb) {
        return mb + " MB";
    }

    private String formatRate(long bytesPerSecond) {
        double value = Math.max(0L, bytesPerSecond);
        String[] units = {"B/s", "KB/s", "MB/s", "GB/s"};
        int unitIndex = 0;
        while (value >= 1024d && unitIndex < units.length - 1) {
            value /= 1024d;
            unitIndex++;
        }
        return String.format(Locale.US, unitIndex == 0 ? "%.0f %s" : "%.1f %s", value, units[unitIndex]);
    }

    private int indexForValue(int[] options, int value) {
        for (int i = 0; i < options.length; i++) {
            if (options[i] == value) {
                return i;
            }
        }
        int nearestIndex = 0;
        int nearestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < options.length; i++) {
            int distance = Math.abs(options[i] - value);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    static String formatRecentWindowValue(int seconds) {
        int hours = Math.max(1, seconds / 3600);
        if (hours < 24 || hours % 24 != 0) {
            return hours + " h";
        }
        int days = hours / 24;
        return days + (days == 1 ? " día" : " días");
    }

    private String formatHistoricalResolutionValue(int seconds) {
        if (seconds < 3600) {
            return (seconds / 60) + " min";
        }
        int hours = seconds / 3600;
        return hours + " h";
    }

    private String formatRecentWindow(int seconds) {
        int hours = seconds / 3600;
        if (hours >= 24 && hours % 24 == 0) {
            int days = hours / 24;
            return days + (days == 1 ? " día" : " días");
        }
        return hours + " h";
    }

    private String formatHistoricalResolution(int seconds) {
        int minutes = seconds / 60;
        return minutes + " min";
    }

    private int getStatsRangeSeconds() {
        Server settingsServer = getStatsSettingsServer();
        if (settingsServer == null || settingsServer.getEstadisticasRangoSegundos() == null || settingsServer.getEstadisticasRangoSegundos() <= 0) {
            return 300;
        }
        return settingsServer.getEstadisticasRangoSegundos();
    }

    private boolean isStatsPersistenceEnabled() {
        Server settingsServer = getStatsSettingsServer();
        return settingsServer == null || settingsServer.getEstadisticasPersistenciaActiva() == null
                ? true
                : Boolean.TRUE.equals(settingsServer.getEstadisticasPersistenciaActiva());
    }

    private int getStatsRecentWindowSeconds() {
        return getStatsRecentWindowSeconds(server);
    }

    private static int getStatsRecentWindowSeconds(Server server) {
        return normalizarVentanaReciente(server == null ? null : server.getEstadisticasVentanaRecienteSegundos());
    }

    static int normalizarVentanaReciente(Integer seconds) {
        if (seconds == null || seconds <= 0 || seconds > Server.VENTANA_RECIENTE_MAXIMA_SEGUNDOS) {
            return Server.VENTANA_RECIENTE_POR_DEFECTO_SEGUNDOS;
        }
        return seconds;
    }

    private int getStatsHistoricalResolutionSeconds() {
        return getStatsHistoricalResolutionSeconds(server);
    }

    private static int getStatsHistoricalResolutionSeconds(Server server) {
        if (server == null || server.getEstadisticasResolucionHistoricaSegundos() == null || server.getEstadisticasResolucionHistoricaSegundos() <= 0) {
            return 60;
        }
        return server.getEstadisticasResolucionHistoricaSegundos();
    }

    private boolean isChartEnabled(StatsChartOption option) {
        return isChartEnabled(getStatsSettingsServer(), option);
    }

    private boolean isChartPersistenceEnabled(StatsChartOption option) {
        return isChartPersistenceEnabled(getStatsSettingsServer(), option);
    }

    private static boolean isChartPersistenceEnabled(Server server, StatsChartOption option) {
        if (option == null) {
            return false;
        }
        if (server == null) {
            return true;
        }
        return switch (option) {
            case CPU -> server.getEstadisticasCpuHistorial() == null || Boolean.TRUE.equals(server.getEstadisticasCpuHistorial());
            case RAM -> server.getEstadisticasRamHistorial() == null || Boolean.TRUE.equals(server.getEstadisticasRamHistorial());
            case DISK -> server.getEstadisticasDiscoHistorial() == null || Boolean.TRUE.equals(server.getEstadisticasDiscoHistorial());
            case PLAYERS -> server.getEstadisticasJugadoresHistorial() == null || Boolean.TRUE.equals(server.getEstadisticasJugadoresHistorial());
        };
    }

    private static boolean isChartEnabled(Server server, StatsChartOption option) {
        if (option == null) {
            return false;
        }
        if (server == null) {
            return option.defaultEnabled();
        }
        return switch (option) {
            case CPU -> server.getEstadisticasCpuActiva() == null ? option.defaultEnabled() : Boolean.TRUE.equals(server.getEstadisticasCpuActiva());
            case RAM -> server.getEstadisticasRamActiva() == null ? option.defaultEnabled() : Boolean.TRUE.equals(server.getEstadisticasRamActiva());
            case DISK -> server.getEstadisticasDiscoActiva() == null ? option.defaultEnabled() : Boolean.TRUE.equals(server.getEstadisticasDiscoActiva());
            case PLAYERS -> server.getEstadisticasJugadoresActiva() == null ? option.defaultEnabled() : Boolean.TRUE.equals(server.getEstadisticasJugadoresActiva());
        };
    }

    private Server getStatsSettingsServer() {
        if (gestorServidores != null && server != null && server.getId() != null) {
            Server persistedServer = gestorServidores.getServerById(server.getId());
            if (persistedServer != null) {
                return persistedServer;
            }
        }
        return server;
    }

    private void guardarConfiguracionServidor() {
        if (gestorServidores != null && server != null) {
            gestorServidores.guardarServidor(server);
        }
    }

    private void cargarAjustesHistoricoPersistidos() {
        persistedStatsPersistenceEnabled = isStatsPersistenceEnabled();
        persistedStatsRecentWindowSeconds = getStatsRecentWindowSeconds();
        persistedStatsHistoricalResolutionSeconds = getStatsHistoricalResolutionSeconds();
        for (StatsChartOption option : StatsChartOption.values()) {
            boolean enabled = isChartEnabled(option);
            boolean persistenceEnabled = isChartPersistenceEnabled(option);
            persistedChartEnabled.put(option, enabled);
            pendingChartEnabled.put(option, enabled);
            persistedChartPersistenceEnabled.put(option, persistenceEnabled);
            pendingChartPersistenceEnabled.put(option, persistenceEnabled);
        }
    }

    private void cargarControlesHistoricoDesdePersistidos() {
        updatingSettingsControls = true;
        persistenciaCheckBox.setSelected(persistedStatsPersistenceEnabled);
        ventanaRecienteSlider.setValue(indexForValue(RECENT_WINDOW_OPTIONS, persistedStatsRecentWindowSeconds));
        ventanaRecienteValueLabel.setText(formatRecentWindowValue(RECENT_WINDOW_OPTIONS[ventanaRecienteSlider.getValue()]));
        resolucionHistoricaSlider.setValue(indexForValue(HISTORICAL_RESOLUTION_OPTIONS, persistedStatsHistoricalResolutionSeconds));
        resolucionHistoricaValueLabel.setText(formatHistoricalResolutionValue(HISTORICAL_RESOLUTION_OPTIONS[resolucionHistoricaSlider.getValue()]));
        cpuActivaCheckBox.setSelected(Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(StatsChartOption.CPU, StatsChartOption.CPU.defaultEnabled())));
        ramActivaCheckBox.setSelected(Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(StatsChartOption.RAM, StatsChartOption.RAM.defaultEnabled())));
        discoActivaCheckBox.setSelected(Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(StatsChartOption.DISK, StatsChartOption.DISK.defaultEnabled())));
        jugadoresActivaCheckBox.setSelected(Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(StatsChartOption.PLAYERS, StatsChartOption.PLAYERS.defaultEnabled())));
        cpuPersistenciaCheckBox.setSelected(Boolean.TRUE.equals(pendingChartPersistenceEnabled.getOrDefault(StatsChartOption.CPU, true)));
        ramPersistenciaCheckBox.setSelected(Boolean.TRUE.equals(pendingChartPersistenceEnabled.getOrDefault(StatsChartOption.RAM, true)));
        discoPersistenciaCheckBox.setSelected(Boolean.TRUE.equals(pendingChartPersistenceEnabled.getOrDefault(StatsChartOption.DISK, true)));
        jugadoresPersistenciaCheckBox.setSelected(Boolean.TRUE.equals(pendingChartPersistenceEnabled.getOrDefault(StatsChartOption.PLAYERS, true)));
        syncChartPersistenceCheckboxState(StatsChartOption.CPU);
        syncChartPersistenceCheckboxState(StatsChartOption.RAM);
        syncChartPersistenceCheckboxState(StatsChartOption.DISK);
        syncChartPersistenceCheckboxState(StatsChartOption.PLAYERS);
        updatingSettingsControls = false;
        updateHistoricoSaveButtonState();
        rebuildChartsPanel();
    }

    private boolean hasUnsavedHistoricoSettingsChanges() {
        if (server == null) {
            return false;
        }
        if (persistenciaCheckBox.isSelected() != persistedStatsPersistenceEnabled
                || RECENT_WINDOW_OPTIONS[ventanaRecienteSlider.getValue()] != persistedStatsRecentWindowSeconds
                || HISTORICAL_RESOLUTION_OPTIONS[resolucionHistoricaSlider.getValue()] != persistedStatsHistoricalResolutionSeconds) {
            return true;
        }
        for (StatsChartOption option : StatsChartOption.values()) {
            if (!Objects.equals(pendingChartEnabled.get(option), persistedChartEnabled.get(option))) {
                return true;
            }
            if (!Objects.equals(pendingChartPersistenceEnabled.get(option), persistedChartPersistenceEnabled.get(option))) {
                return true;
            }
        }
        return false;
    }

    private void updateHistoricoSaveButtonState() {
        boolean hasUnsavedChanges = !updatingSettingsControls && hasUnsavedHistoricoSettingsChanges();
        applyDefaultHistoricoSaveButtonStyle();
        guardarHistoricoButton.setEnabled(hasUnsavedChanges);
        reiniciarHistoricoButton.setEnabled(hasUnsavedChanges);
        if (hasUnsavedChanges) {
            AppTheme.applyAccentButtonStyle(guardarHistoricoButton);
        }
        guardarHistoricoButton.repaint();
        reiniciarHistoricoButton.repaint();
    }

    private void actualizarGraficaVisiblePendiente(StatsChartOption option, boolean enabled) {
        if (updatingSettingsControls) {
            return;
        }
        pendingChartEnabled.put(option, enabled);
        syncChartPersistenceCheckboxState(option);
        updateHistoricoSaveButtonState();
        rebuildChartsPanel();
    }

    private void actualizarPersistenciaGraficaPendiente(StatsChartOption option, boolean enabled) {
        if (updatingSettingsControls) {
            return;
        }
        pendingChartPersistenceEnabled.put(option, enabled);
        updateHistoricoSaveButtonState();
    }

    private void syncChartPersistenceCheckboxState(StatsChartOption option) {
        JCheckBox checkBox = getChartPersistenceCheckBox(option);
        if (checkBox == null) {
            return;
        }
        boolean chartEnabled = Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(option, option.defaultEnabled()));
        checkBox.setEnabled(chartEnabled);
    }

    private JCheckBox getChartPersistenceCheckBox(StatsChartOption option) {
        return switch (option) {
            case CPU -> cpuPersistenciaCheckBox;
            case RAM -> ramPersistenciaCheckBox;
            case DISK -> discoPersistenciaCheckBox;
            case PLAYERS -> jugadoresPersistenciaCheckBox;
        };
    }

    private void applyDefaultHistoricoSaveButtonStyle() {
        styleActionButton(guardarHistoricoButton);
        styleActionButton(reiniciarHistoricoButton);
    }

    private void guardarAjustesHistorico() {
        if (server == null || !hasUnsavedHistoricoSettingsChanges()) {
            updateHistoricoSaveButtonState();
            return;
        }
        boolean persistedPersistenceEnabled = persistedStatsPersistenceEnabled;
        boolean persistenciaActiva = persistenciaCheckBox.isSelected();
        int ventanaReciente = RECENT_WINDOW_OPTIONS[ventanaRecienteSlider.getValue()];
        int resolucionHistorica = HISTORICAL_RESOLUTION_OPTIONS[resolucionHistoricaSlider.getValue()];

        if (persistedPersistenceEnabled && !persistenciaActiva) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "La persistencia se va a desactivar. ¿Quieres borrar también el histórico guardado de este servidor?",
                    "Desactivar persistencia",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                cargarControlesHistoricoDesdePersistidos();
                return;
            }
            limpiarHistorialActual();
            if (choice == JOptionPane.YES_OPTION) {
                borrarHistorialPersistido();
            }
        } else if (!persistedPersistenceEnabled && persistenciaActiva) {
            recargarHistorialPersistido();
        }

        server.setEstadisticasPersistenciaActiva(persistenciaActiva);
        server.setEstadisticasVentanaRecienteSegundos(ventanaReciente);
        server.setEstadisticasResolucionHistoricaSegundos(resolucionHistorica);
        aplicarConfiguracionGraficasEnServidor(server);

        Server persistedServer = getStatsSettingsServer();
        if (persistedServer != null && persistedServer != server) {
            persistedServer.setEstadisticasPersistenciaActiva(persistenciaActiva);
            persistedServer.setEstadisticasVentanaRecienteSegundos(ventanaReciente);
            persistedServer.setEstadisticasResolucionHistoricaSegundos(resolucionHistorica);
            aplicarConfiguracionGraficasEnServidor(persistedServer);
        }

        guardarConfiguracionServidor();
        cargarAjustesHistoricoPersistidos();
        refrescarGraficasDesdeHistorial();
        updateHistoricoSaveButtonState();
    }

    private void refrescarGraficasDesdeHistorial() {
        TimeRangeOption selectedRange = getSelectedRange();
        int bucketSeconds = getStatsHistoricalResolutionSeconds();
        actualizarNavegacionHistorico(selectedRange.seconds(), bucketSeconds);
        int offsetSeconds = getTimelineOffsetSeconds();
        cpuChart.setSamples(statsHistory.snapshotForRangeCpu(selectedRange.seconds(), offsetSeconds, bucketSeconds));
        ramChart.setSamples(statsHistory.snapshotForRange(selectedRange.seconds(), offsetSeconds, bucketSeconds));
        diskChart.setSamples(statsHistory.snapshotForRangeDisk(selectedRange.seconds(), offsetSeconds, bucketSeconds));
        playersChart.setSamples(statsHistory.snapshotForRangePlayers(selectedRange.seconds(), offsetSeconds, bucketSeconds));
        cpuChart.setRangeSeconds(selectedRange.seconds());
        ramChart.setRangeSeconds(selectedRange.seconds());
        diskChart.setRangeSeconds(selectedRange.seconds());
        playersChart.setRangeSeconds(selectedRange.seconds());
        rebuildChartsPanel();
    }

    private TimeRangeOption getSelectedRange() {
        Object selected = rangoCombo.getSelectedItem();
        return selected instanceof TimeRangeOption option ? option : TimeRangeOption.SECONDS_60;
    }

    private int getTimelineOffsetSeconds() {
        return Math.max(0, timelineMaxOffsetSeconds - historialScrollBar.getValue());
    }

    private void actualizarNavegacionHistorico(int rangeSeconds, int bucketSeconds) {
        int maxOffsetSeconds = statsHistory.getMaxOffsetSeconds(rangeSeconds, bucketSeconds);
        int currentOffset = Math.max(0, Math.min(getTimelineOffsetSeconds(), maxOffsetSeconds));
        timelineMaxOffsetSeconds = maxOffsetSeconds;
        updatingTimelineControls = true;
        historialScrollBar.setEnabled(maxOffsetSeconds > 0);
        historialScrollBar.setValues(maxOffsetSeconds - currentOffset, 1, 0, maxOffsetSeconds + 1);
        historialScrollBar.setBlockIncrement(Math.max(10, rangeSeconds / 12));
        historialScrollBar.setUnitIncrement(1);
        updatingTimelineControls = false;
        posicionHistoricoLabel.setText(buildTimelineWindowLabel(currentOffset, bucketSeconds));
    }

    private String buildTimelineWindowLabel(int offsetSeconds, int bucketSeconds) {
        long endTimestamp = statsHistory.getWindowEndTimestamp(offsetSeconds, bucketSeconds);
        if (endTimestamp <= 0L) {
            return "Sin histórico";
        }
        String endText = SAMPLE_TIME_FORMAT.format(Instant.ofEpochMilli(endTimestamp));
        if (offsetSeconds <= 0) {
            return "Ventana actual hasta " + endText;
        }
        return "Ventana desplazada hasta " + endText;
    }

    private void styleActionButton(JButton button) {
        if (button == null) {
            return;
        }
        AppTheme.applyActionButtonStyle(button);
    }

    private String getServerHistoryKey() {
        return (server != null && server.getId() != null && !server.getId().isBlank()) ? server.getId() : "__no_server__";
    }

    private void limpiarHistorialActual() {
        statsHistory.clear();
        refrescarGraficasDesdeHistorial();
        solicitarMuestras();
    }

    private void recargarHistorialPersistido() {
        StatsHistory loadedHistory = loadHistoryFromDisk(getStatsSettingsServer(), getServerHistoryKey());
        statsHistory.replaceWith(loadedHistory);
        refrescarGraficasDesdeHistorial();
        solicitarMuestras();
    }

    private void borrarHistorialPersistido() {
        try {
            Files.deleteIfExists(getHistoryFile(getStatsSettingsServer(), getServerHistoryKey()));
        } catch (IOException e) {
            System.err.println("No se ha podido borrar el historial de estadísticas de " + getServerHistoryKey() + ": " + e.getMessage());
        }
    }

    private void aplicarConfiguracionGraficasEnServidor(Server targetServer) {
        if (targetServer == null) {
            return;
        }
        targetServer.setEstadisticasRamActiva(Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(StatsChartOption.RAM, StatsChartOption.RAM.defaultEnabled())));
        targetServer.setEstadisticasCpuActiva(Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(StatsChartOption.CPU, StatsChartOption.CPU.defaultEnabled())));
        targetServer.setEstadisticasCpuHistorial(Boolean.TRUE.equals(pendingChartPersistenceEnabled.getOrDefault(StatsChartOption.CPU, true)));
        targetServer.setEstadisticasRamHistorial(Boolean.TRUE.equals(pendingChartPersistenceEnabled.getOrDefault(StatsChartOption.RAM, true)));
        targetServer.setEstadisticasDiscoActiva(Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(StatsChartOption.DISK, StatsChartOption.DISK.defaultEnabled())));
        targetServer.setEstadisticasDiscoHistorial(Boolean.TRUE.equals(pendingChartPersistenceEnabled.getOrDefault(StatsChartOption.DISK, true)));
        targetServer.setEstadisticasJugadoresActiva(Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(StatsChartOption.PLAYERS, StatsChartOption.PLAYERS.defaultEnabled())));
        targetServer.setEstadisticasJugadoresHistorial(Boolean.TRUE.equals(pendingChartPersistenceEnabled.getOrDefault(StatsChartOption.PLAYERS, true)));
    }

    private void abrirDialogoExportacion() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner instanceof Frame frame ? frame : null, "Exportar estadísticas", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        ExportPaletteModel paletteModel = createDefaultExportPaletteModel();
        ExportRenderOptionsModel renderOptionsModel = createDefaultExportRenderOptionsModel();
        FlatComboBox<ExportFormatOption> formatoCombo = new FlatComboBox<>();
        formatoCombo.setModel(new DefaultComboBoxModel<>(ExportFormatOption.values()));
        formatoCombo.setRoundRect(true);
        ExportSnapshot exportSnapshot = createFrozenExportSnapshot();
        FlatComboBox<ExportInstantOption> desdeCombo = new FlatComboBox<>();
        desdeCombo.setModel(createExportInstantModel(exportSnapshot, true));
        desdeCombo.setRoundRect(true);
        FlatComboBox<ExportInstantOption> hastaCombo = new FlatComboBox<>();
        hastaCombo.setModel(createExportInstantModel(exportSnapshot, false));
        hastaCombo.setRoundRect(true);
        FlatComboBox<ExportLayoutOption> disposicionCombo = new FlatComboBox<>();
        disposicionCombo.setModel(new DefaultComboBoxModel<>(ExportLayoutOption.values()));
        disposicionCombo.setRoundRect(true);
        JCheckBox incluirCpuCheckBox = new JCheckBox("CPU");
        JCheckBox incluirRamCheckBox = new JCheckBox("RAM");
        JCheckBox incluirDiscoCheckBox = new JCheckBox("Disco");
        JCheckBox gridCheckBox = new JCheckBox("Grid");
        JCheckBox leyendaCheckBox = new JCheckBox("Leyenda");
        JCheckBox rellenoCheckBox = new JCheckBox("Relleno");
        JCheckBox bordeCheckBox = new JCheckBox("Borde");
        JCheckBox valorActualCheckBox = new JCheckBox("Valor actual");
        JCheckBox apagadosCheckBox = new JCheckBox("Marcadores");
        JLabel previewLabel = new JLabel("Generando previsualizacion...", SwingConstants.CENTER);

        if (desdeCombo.getItemCount() > 0) {
            desdeCombo.setSelectedIndex(0);
        }
        if (hastaCombo.getItemCount() > 0) {
            hastaCombo.setSelectedIndex(Math.max(0, hastaCombo.getItemCount() - 1));
        }
        incluirCpuCheckBox.setSelected(true);
        incluirRamCheckBox.setSelected(true);
        incluirDiscoCheckBox.setSelected(true);
        gridCheckBox.setSelected(renderOptionsModel.showGrid);
        leyendaCheckBox.setSelected(renderOptionsModel.showLegend);
        rellenoCheckBox.setSelected(renderOptionsModel.showAreaFill);
        bordeCheckBox.setSelected(renderOptionsModel.showBorder);
        valorActualCheckBox.setSelected(renderOptionsModel.showLatestValue);
        apagadosCheckBox.setSelected(renderOptionsModel.showShutdownMarkers);
        formatoCombo.setFocusable(false);
        desdeCombo.setFocusable(false);
        hastaCombo.setFocusable(false);
        disposicionCombo.setFocusable(false);
        List<JCheckBox> exportCheckBoxes = List.of(
                incluirCpuCheckBox,
                incluirRamCheckBox,
                incluirDiscoCheckBox,
                gridCheckBox,
                leyendaCheckBox,
                rellenoCheckBox,
                bordeCheckBox,
                valorActualCheckBox,
                apagadosCheckBox
        );
        for (JCheckBox checkBox : exportCheckBoxes) {
            checkBox.setOpaque(false);
        }
        previewLabel.setOpaque(false);
        previewLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        previewLabel.setForeground(AppTheme.getMutedForeground());
        previewLabel.setPreferredSize(new Dimension(920, 320));
        previewLabel.setMinimumSize(new Dimension(720, 260));

        JButton backgroundColorButton = new FlatButton();
        backgroundColorButton.setText("Fondo");
        JButton borderColorButton = new FlatButton();
        borderColorButton.setText("Borde");
        JButton gridColorButton = new FlatButton();
        gridColorButton.setText("Grid");
        JButton textColorButton = new FlatButton();
        textColorButton.setText("Texto");
        JButton cpuColorButton = new FlatButton();
        cpuColorButton.setText("CPU");
        JButton ramColorButton = new FlatButton();
        ramColorButton.setText("RAM");
        JButton diskColorButton = new FlatButton();
        diskColorButton.setText("Disco");
        List<JButton> colorButtons = List.of(backgroundColorButton, borderColorButton, gridColorButton, textColorButton, cpuColorButton, ramColorButton, diskColorButton);
        for (JButton button : colorButtons) {
            styleActionButton(button);
        }
        configurarColorButton(backgroundColorButton, paletteModel.backgroundColor);
        configurarColorButton(borderColorButton, paletteModel.borderColor);
        configurarColorButton(gridColorButton, paletteModel.gridColor);
        configurarColorButton(textColorButton, paletteModel.textColor);
        configurarColorButton(cpuColorButton, paletteModel.cpuAccentColor);
        configurarColorButton(ramColorButton, paletteModel.ramAccentColor);
        configurarColorButton(diskColorButton, paletteModel.diskAccentColor);

        final boolean[] updatingExportUi = {false};
        Runnable refreshPreview = () -> {
            if (updatingExportUi[0]) {
                return;
            }
            updatingExportUi[0] = true;
            try {
                ExportFormatOption formato = (ExportFormatOption) formatoCombo.getSelectedItem();
                actualizarEstadoControlesExportacion(formato, disposicionCombo, incluirCpuCheckBox.isSelected(), incluirRamCheckBox.isSelected(), incluirDiscoCheckBox.isSelected(), colorButtons,
                        List.of(gridCheckBox, leyendaCheckBox, rellenoCheckBox, bordeCheckBox, valorActualCheckBox, apagadosCheckBox));
                ExportLayoutOption disposicion = (ExportLayoutOption) disposicionCombo.getSelectedItem();
                actualizarPrevisualizacionExportacion(
                        previewLabel,
                        exportSnapshot,
                        resolveExportBoundary((ExportInstantOption) desdeCombo.getSelectedItem(), exportSnapshot, true),
                        resolveExportBoundary((ExportInstantOption) hastaCombo.getSelectedItem(), exportSnapshot, false),
                        incluirCpuCheckBox.isSelected(),
                        incluirRamCheckBox.isSelected(),
                        incluirDiscoCheckBox.isSelected(),
                        disposicion,
                        paletteModel.toPalette(),
                        renderOptionsModel.toOptions()
                );
            } finally {
                updatingExportUi[0] = false;
            }
        };

        formatoCombo.addActionListener(e -> refreshPreview.run());
        desdeCombo.addActionListener(e -> refreshPreview.run());
        hastaCombo.addActionListener(e -> refreshPreview.run());
        disposicionCombo.addActionListener(e -> refreshPreview.run());
        incluirCpuCheckBox.addActionListener(e -> refreshPreview.run());
        incluirRamCheckBox.addActionListener(e -> refreshPreview.run());
        incluirDiscoCheckBox.addActionListener(e -> refreshPreview.run());
        gridCheckBox.addActionListener(e -> {
            renderOptionsModel.showGrid = gridCheckBox.isSelected();
            refreshPreview.run();
        });
        leyendaCheckBox.addActionListener(e -> {
            renderOptionsModel.showLegend = leyendaCheckBox.isSelected();
            refreshPreview.run();
        });
        rellenoCheckBox.addActionListener(e -> {
            renderOptionsModel.showAreaFill = rellenoCheckBox.isSelected();
            refreshPreview.run();
        });
        bordeCheckBox.addActionListener(e -> {
            renderOptionsModel.showBorder = bordeCheckBox.isSelected();
            refreshPreview.run();
        });
        valorActualCheckBox.addActionListener(e -> {
            renderOptionsModel.showLatestValue = valorActualCheckBox.isSelected();
            refreshPreview.run();
        });
        apagadosCheckBox.addActionListener(e -> {
            renderOptionsModel.showShutdownMarkers = apagadosCheckBox.isSelected();
            refreshPreview.run();
        });

        backgroundColorButton.addActionListener(e -> seleccionarColor(dialog, backgroundColorButton, "Color de fondo", color -> {
            paletteModel.backgroundColor = color;
            refreshPreview.run();
        }));
        borderColorButton.addActionListener(e -> seleccionarColor(dialog, borderColorButton, "Color del borde", color -> {
            paletteModel.borderColor = color;
            refreshPreview.run();
        }));
        gridColorButton.addActionListener(e -> seleccionarColor(dialog, gridColorButton, "Color de la rejilla", color -> {
            paletteModel.gridColor = color;
            refreshPreview.run();
        }));
        textColorButton.addActionListener(e -> seleccionarColor(dialog, textColorButton, "Color del texto", color -> {
            paletteModel.textColor = color;
            refreshPreview.run();
        }));
        cpuColorButton.addActionListener(e -> seleccionarColor(dialog, cpuColorButton, "Color de CPU", color -> {
            paletteModel.cpuAccentColor = color;
            refreshPreview.run();
        }));
        ramColorButton.addActionListener(e -> seleccionarColor(dialog, ramColorButton, "Color de RAM", color -> {
            paletteModel.ramAccentColor = color;
            refreshPreview.run();
        }));
        diskColorButton.addActionListener(e -> seleccionarColor(dialog, diskColorButton, "Color de Disco", color -> {
            paletteModel.diskAccentColor = color;
            refreshPreview.run();
        }));

        JPanel wrapper = new JPanel(new BorderLayout(0, 12));
        wrapper.setOpaque(true);
        wrapper.setBackground(AppTheme.getBackground());
        wrapper.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        CardPanel previewCard = new CardPanel("Previsualización");
        previewCard.setBackground(AppTheme.getSurfaceBackground());
        JLabel previewTitle = previewCard.getTitleLabel();
        previewTitle.setFont(previewTitle.getFont().deriveFont(Font.BOLD, 13f));
        previewTitle.setForeground(AppTheme.getForeground());
        JPanel previewContent = new JPanel(new BorderLayout());
        previewContent.setOpaque(false);
        previewContent.add(previewLabel, BorderLayout.CENTER);
        previewCard.getContentPanel().add(previewContent, BorderLayout.CENTER);
        wrapper.add(previewCard, BorderLayout.NORTH);

        CardPanel configCard = new CardPanel("Opciones");
        configCard.setBackground(AppTheme.getSurfaceBackground());
        JLabel configTitle = configCard.getTitleLabel();
        configTitle.setFont(configTitle.getFont().deriveFont(Font.BOLD, 13f));
        configTitle.setForeground(AppTheme.getForeground());

        JPanel configContent = new JPanel();
        configContent.setOpaque(false);
        configContent.setLayout(new BoxLayout(configContent, BoxLayout.Y_AXIS));
        JLabel datosTitle = crearSubtituloExportacion("Datos");
        configContent.add(datosTitle);
        configContent.add(Box.createVerticalStrut(6));
        configContent.add(crearFilaExportacion("Formato", formatoCombo));
        configContent.add(Box.createVerticalStrut(8));
        configContent.add(crearFilaExportacion("Desde", desdeCombo));
        configContent.add(Box.createVerticalStrut(8));
        configContent.add(crearFilaExportacion("Hasta", hastaCombo));
        configContent.add(Box.createVerticalStrut(8));
        configContent.add(crearFilaChecksExportacion("Series", incluirCpuCheckBox, incluirRamCheckBox, incluirDiscoCheckBox));
        configContent.add(Box.createVerticalStrut(8));
        configContent.add(crearFilaExportacion("Disposicion", disposicionCombo));
        configContent.add(Box.createVerticalStrut(14));

        JLabel vistaTitle = crearSubtituloExportacion("Capas");
        configContent.add(vistaTitle);
        configContent.add(Box.createVerticalStrut(6));
        JPanel togglesPanel = new JPanel(new GridLayout(2, 3, 8, 8));
        togglesPanel.setOpaque(false);
        togglesPanel.add(gridCheckBox);
        togglesPanel.add(leyendaCheckBox);
        togglesPanel.add(rellenoCheckBox);
        togglesPanel.add(bordeCheckBox);
        togglesPanel.add(valorActualCheckBox);
        togglesPanel.add(apagadosCheckBox);
        configContent.add(togglesPanel);
        configContent.add(Box.createVerticalStrut(14));

        JLabel colorTitle = crearSubtituloExportacion("Colores PNG");
        configContent.add(colorTitle);
        configContent.add(Box.createVerticalStrut(6));

        JPanel coloresPanel = new JPanel(new GridLayout(3, 3, 8, 8));
        coloresPanel.setOpaque(false);
        coloresPanel.add(backgroundColorButton);
        coloresPanel.add(borderColorButton);
        coloresPanel.add(gridColorButton);
        coloresPanel.add(textColorButton);
        coloresPanel.add(cpuColorButton);
        coloresPanel.add(ramColorButton);
        coloresPanel.add(diskColorButton);
        configContent.add(coloresPanel);

        FlatScrollPane configScrollPane = new FlatScrollPane();
        configScrollPane.setViewportView(configContent);
        configScrollPane.setBorder(null);
        configScrollPane.setOpaque(false);
        configScrollPane.getViewport().setOpaque(false);
        configScrollPane.getViewport().setBackground(AppTheme.getSurfaceBackground());
        configScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        configScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        configScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        configCard.getContentPanel().add(configScrollPane, BorderLayout.CENTER);
        wrapper.add(configCard, BorderLayout.CENTER);

        JButton exportarButton = new FlatButton();
        exportarButton.setText("Exportar");
        JButton cancelarButton = new FlatButton();
        cancelarButton.setText("Cancelar");
        styleActionButton(exportarButton);
        styleActionButton(cancelarButton);
        cancelarButton.addActionListener(e -> dialog.dispose());
        exportarButton.addActionListener(e -> {
            if (!incluirCpuCheckBox.isSelected() && !incluirRamCheckBox.isSelected() && !incluirDiscoCheckBox.isSelected()) {
                JOptionPane.showMessageDialog(dialog, "Selecciona al menos una gráfica para exportar.", "Exportar estadísticas", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                if (formatoCombo.getSelectedItem() == ExportFormatOption.PNG) {
                    exportarGraficasComoPng(
                            dialog,
                            exportSnapshot,
                            resolveExportBoundary((ExportInstantOption) desdeCombo.getSelectedItem(), exportSnapshot, true),
                            resolveExportBoundary((ExportInstantOption) hastaCombo.getSelectedItem(), exportSnapshot, false),
                            incluirCpuCheckBox.isSelected(),
                            incluirRamCheckBox.isSelected(),
                            incluirDiscoCheckBox.isSelected(),
                            (ExportLayoutOption) disposicionCombo.getSelectedItem(),
                            paletteModel.toPalette(),
                            renderOptionsModel.toOptions()
                    );
                } else {
                    exportarGraficasComoCsv(
                            dialog,
                            exportSnapshot,
                            resolveExportBoundary((ExportInstantOption) desdeCombo.getSelectedItem(), exportSnapshot, true),
                            resolveExportBoundary((ExportInstantOption) hastaCombo.getSelectedItem(), exportSnapshot, false),
                            incluirCpuCheckBox.isSelected(),
                            incluirRamCheckBox.isSelected(),
                            incluirDiscoCheckBox.isSelected()
                    );
                }
                dialog.dispose();
            } catch (IOException ex) {
                if ("EXPORT_CANCELLED".equals(ex.getMessage())) {
                    return;
                }
                JOptionPane.showMessageDialog(dialog, "No se ha podido exportar las estadísticas: " + ex.getMessage(), "Exportar estadísticas", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.add(cancelarButton);
        footer.add(exportarButton);
        wrapper.add(footer, BorderLayout.SOUTH);

        previewLabel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                refreshPreview.run();
            }
        });

        dialog.setContentPane(wrapper);
        dialog.pack();
        ajustarTamanoDialogoExportacion(dialog);
        dialog.setMinimumSize(new Dimension(960, 700));
        dialog.setLocationRelativeTo(this);
        refreshPreview.run();
        dialog.setVisible(true);
    }

    private void ajustarTamanoDialogoExportacion(JDialog dialog) {
        if (dialog == null) {
            return;
        }
        GraphicsConfiguration graphicsConfiguration = dialog.getGraphicsConfiguration();
        if (graphicsConfiguration == null) {
            graphicsConfiguration = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        }
        Rectangle screenBounds = graphicsConfiguration.getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration);
        int usableWidth = Math.max(1, screenBounds.width - screenInsets.left - screenInsets.right);
        int usableHeight = Math.max(1, screenBounds.height - screenInsets.top - screenInsets.bottom);
        int maxWidth = Math.max(960, (int) Math.floor(usableWidth * 0.92d));
        int maxHeight = Math.max(560, (int) Math.floor(usableHeight * 0.80d));

        Dimension packedSize = dialog.getSize();
        dialog.setSize(
                Math.min(packedSize.width, maxWidth),
                Math.min(packedSize.height, maxHeight)
        );
    }

    private JPanel crearFilaExportacion(String titulo, JComponent component) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        JLabel label = new JLabel(titulo);
        label.setForeground(AppTheme.getMutedForeground());
        label.setPreferredSize(new Dimension(140, 28));
        row.add(label, BorderLayout.WEST);
        row.add(component, BorderLayout.CENTER);
        return row;
    }

    private JPanel crearFilaChecksExportacion(String titulo, JComponent... components) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        JLabel label = new JLabel(titulo);
        label.setForeground(AppTheme.getMutedForeground());
        label.setPreferredSize(new Dimension(140, 28));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        right.setOpaque(false);
        for (JComponent component : components) {
            if (component instanceof JCheckBox checkBox) {
                checkBox.setOpaque(false);
            }
            right.add(component);
        }
        row.add(label, BorderLayout.WEST);
        row.add(right, BorderLayout.CENTER);
        return row;
    }

    private JLabel crearSubtituloExportacion(String texto) {
        JLabel label = new JLabel(texto);
        label.setForeground(AppTheme.getMutedForeground());
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void configurarColorButton(JButton button, Color color) {
        button.setBackground(color);
        button.setForeground(contrastingTextColor(color));
    }

    private void seleccionarColor(Component parent, JButton button, String title, java.util.function.Consumer<Color> consumer) {
        Color selectedColor = JColorChooser.showDialog(parent, title, button.getBackground());
        if (selectedColor == null) {
            return;
        }
        consumer.accept(selectedColor);
        configurarColorButton(button, selectedColor);
    }

    private Color contrastingTextColor(Color background) {
        int luminance = (int) Math.round((background.getRed() * 0.299) + (background.getGreen() * 0.587) + (background.getBlue() * 0.114));
        return luminance >= 160 ? Color.BLACK : Color.WHITE;
    }

    private void actualizarPrevisualizacionExportacion(JLabel previewLabel,
                                                       ExportSnapshot exportSnapshot,
                                                       long desdeTimestampMillis,
                                                       long hastaTimestampMillis,
                                                       boolean incluirCpu,
                                                       boolean incluirRam,
                                                       boolean incluirDisco,
                                                       ExportLayoutOption disposicion,
                                                       ExportPalette palette,
                                                       ExportRenderOptions renderOptions) {
        if (previewLabel == null) {
            return;
        }
        if (!incluirCpu && !incluirRam && !incluirDisco) {
            previewLabel.setIcon(null);
            previewLabel.setText("Selecciona al menos una grafica");
            previewLabel.setForeground(AppTheme.getMutedForeground());
            return;
        }
        int previewWidth = Math.max(720, previewLabel.getWidth() > 0 ? previewLabel.getWidth() : 760);
        int previewHeight = Math.max(260, previewLabel.getHeight() > 0 ? previewLabel.getHeight() : 320);
        BufferedImage image = crearImagenPrevisualizacionExportacion(
                previewWidth,
                320,
                exportSnapshot,
                desdeTimestampMillis,
                hastaTimestampMillis,
                incluirCpu,
                incluirRam,
                incluirDisco,
                disposicion != null ? disposicion : ExportLayoutOption.MISMA_IMAGEN_SEPARADAS,
                palette,
                renderOptions
        );
        previewLabel.setText(null);
        previewLabel.setIcon(new ImageIcon(scaleImageToFit(image, previewWidth, previewHeight)));
    }

    private void exportarGraficasComoPng(Component parent,
                                         ExportSnapshot exportSnapshot,
                                         long desdeTimestampMillis,
                                         long hastaTimestampMillis,
                                         boolean incluirCpu,
                                         boolean incluirRam,
                                         boolean incluirDisco,
                                         ExportLayoutOption disposicion,
                                         ExportPalette palette,
                                         ExportRenderOptions renderOptions) throws IOException {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exportar graficas como PNG");
        chooser.setFileFilter(new FileNameExtensionFilter("Imagen PNG", "png"));
        java.io.File imagenesDir = FileSystemView.getFileSystemView().getDefaultDirectory();
        if (imagenesDir != null && imagenesDir.isDirectory()) {
            chooser.setCurrentDirectory(imagenesDir);
        }
        chooser.setSelectedFile(new java.io.File(getSuggestedExportName("png")));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path destino = chooser.getSelectedFile().toPath();
        if (!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            destino = destino.resolveSibling(destino.getFileName() + ".png");
        }

        List<ExportImageTarget> exportTargets = crearDestinosExportacionPng(
                destino,
                exportSnapshot,
                desdeTimestampMillis,
                hastaTimestampMillis,
                incluirCpu,
                incluirRam,
                incluirDisco,
                disposicion != null ? disposicion : ExportLayoutOption.MISMA_IMAGEN_SEPARADAS,
                palette,
                renderOptions
        );
        for (ExportImageTarget exportTarget : exportTargets) {
            confirmarSobrescrituraSiHaceFalta(parent, exportTarget.path());
        }
        for (ExportImageTarget exportTarget : exportTargets) {
            ImageIO.write(exportTarget.image(), "png", exportTarget.path().toFile());
        }
    }

    private void exportarGraficasComoCsv(Component parent,
                                         ExportSnapshot exportSnapshot,
                                         long desdeTimestampMillis,
                                         long hastaTimestampMillis,
                                         boolean incluirCpu,
                                         boolean incluirRam,
                                         boolean incluirDisco) throws IOException {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exportar graficas como CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV", "csv"));
        java.io.File documentosDir = FileSystemView.getFileSystemView().getDefaultDirectory();
        if (documentosDir != null && documentosDir.isDirectory()) {
            chooser.setCurrentDirectory(documentosDir);
        }
        chooser.setSelectedFile(new java.io.File(getSuggestedExportName("csv")));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path destino = chooser.getSelectedFile().toPath();
        if (!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            destino = destino.resolveSibling(destino.getFileName() + ".csv");
        }
        confirmarSobrescrituraSiHaceFalta(parent, destino);

        ExportData exportData = buildExportData(exportSnapshot, desdeTimestampMillis, hastaTimestampMillis);

        try (BufferedWriter writer = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            writer.write("metric,timestamp_iso,timestamp_millis,value,shutdown_marker");
            writer.newLine();
            if (incluirCpu) {
                escribirMuestrasCsv(writer, "cpu", exportData.cpuSamples());
            }
            if (incluirRam) {
                escribirMuestrasCsv(writer, "ram", exportData.ramSamples());
            }
            if (incluirDisco) {
                escribirMuestrasCsv(writer, "disk", exportData.diskSamples());
            }
        }
    }

    private void actualizarEstadoControlesExportacion(ExportFormatOption formato,
                                                      JComboBox<ExportLayoutOption> disposicionCombo,
                                                      boolean incluirCpu,
                                                      boolean incluirRam,
                                                      boolean incluirDisco,
                                                      List<JButton> colorButtons,
                                                      List<JCheckBox> styleCheckBoxes) {
        boolean pngSelected = formato == ExportFormatOption.PNG;
        ExportLayoutOption selected = disposicionCombo != null ? (ExportLayoutOption) disposicionCombo.getSelectedItem() : null;
        int selectedSeries = (incluirCpu ? 1 : 0) + (incluirRam ? 1 : 0) + (incluirDisco ? 1 : 0);
        boolean overlayAllowed = selectedSeries >= 2;
        if (disposicionCombo != null) {
            List<ExportLayoutOption> expectedOptions = new ArrayList<>();
            expectedOptions.add(ExportLayoutOption.MISMA_IMAGEN_SEPARADAS);
            if (overlayAllowed) {
                expectedOptions.add(ExportLayoutOption.MISMA_IMAGEN_SUPERPUESTAS);
            }
            expectedOptions.add(ExportLayoutOption.ARCHIVOS_SEPARADOS);
            boolean modelChanged = disposicionCombo.getItemCount() != expectedOptions.size();
            if (!modelChanged) {
                for (int i = 0; i < expectedOptions.size(); i++) {
                    if (disposicionCombo.getItemAt(i) != expectedOptions.get(i)) {
                        modelChanged = true;
                        break;
                    }
                }
            }
            if (modelChanged) {
                DefaultComboBoxModel<ExportLayoutOption> model = new DefaultComboBoxModel<>();
                for (ExportLayoutOption option : expectedOptions) {
                    model.addElement(option);
                }
                disposicionCombo.setModel(model);
            }
            if (selected == ExportLayoutOption.MISMA_IMAGEN_SUPERPUESTAS && !overlayAllowed) {
                disposicionCombo.setSelectedItem(ExportLayoutOption.MISMA_IMAGEN_SEPARADAS);
            } else if (selected != null) {
                disposicionCombo.setSelectedItem(selected);
            }
            if (disposicionCombo.getSelectedItem() == null) {
                disposicionCombo.setSelectedItem(ExportLayoutOption.MISMA_IMAGEN_SEPARADAS);
            }
            disposicionCombo.setEnabled(pngSelected);
        }
        if (colorButtons != null) {
            for (JButton button : colorButtons) {
                button.setEnabled(pngSelected);
            }
        }
        if (styleCheckBoxes != null) {
            for (JCheckBox checkBox : styleCheckBoxes) {
                checkBox.setEnabled(pngSelected);
            }
        }
    }

    private BufferedImage crearImagenExportacion(int width,
                                                 int singleChartHeight,
                                                 ExportSnapshot exportSnapshot,
                                                 long desdeTimestampMillis,
                                                 long hastaTimestampMillis,
                                                 boolean incluirCpu,
                                                 boolean incluirRam,
                                                 boolean incluirDisco,
                                                 ExportLayoutOption disposicion,
                                                 ExportPalette palette,
                                                 ExportRenderOptions renderOptions) {
        List<BufferedImage> images = crearImagenesExportacion(width, singleChartHeight, exportSnapshot, desdeTimestampMillis, hastaTimestampMillis, incluirCpu, incluirRam, incluirDisco, disposicion, palette, renderOptions);
        return combinarImagenesExportacion(images, width, palette);
    }

    private BufferedImage crearImagenPrevisualizacionExportacion(int width,
                                                                 int singleChartHeight,
                                                                 ExportSnapshot exportSnapshot,
                                                                 long desdeTimestampMillis,
                                                                 long hastaTimestampMillis,
                                                                 boolean incluirCpu,
                                                                 boolean incluirRam,
                                                                 boolean incluirDisco,
                                                                 ExportLayoutOption disposicion,
                                                                 ExportPalette palette,
                                                                 ExportRenderOptions renderOptions) {
        int selectedSeries = (incluirCpu ? 1 : 0) + (incluirRam ? 1 : 0) + (incluirDisco ? 1 : 0);
        int imageWidth = Math.max(420, (disposicion == ExportLayoutOption.ARCHIVOS_SEPARADOS && selectedSeries >= 2) ? (width / 2) - 12 : width);
        List<BufferedImage> images = crearImagenesExportacion(imageWidth, singleChartHeight, exportSnapshot, desdeTimestampMillis, hastaTimestampMillis, incluirCpu, incluirRam, incluirDisco, disposicion, palette, renderOptions);
        if (disposicion == ExportLayoutOption.ARCHIVOS_SEPARADOS && images.size() > 1) {
            return combinarImagenesHorizontales(images, palette);
        }
        return combinarImagenesExportacion(images, width, palette);
    }

    private List<BufferedImage> crearImagenesExportacion(int width,
                                                         int singleChartHeight,
                                                         ExportSnapshot exportSnapshot,
                                                         long desdeTimestampMillis,
                                                         long hastaTimestampMillis,
                                                         boolean incluirCpu,
                                                         boolean incluirRam,
                                                         boolean incluirDisco,
                                                         ExportLayoutOption disposicion,
                                                         ExportPalette palette,
                                                         ExportRenderOptions renderOptions) {
        ExportData exportData = buildExportData(exportSnapshot, desdeTimestampMillis, hastaTimestampMillis);
        ExportLayoutOption effectiveLayout = disposicion != null ? disposicion : ExportLayoutOption.MISMA_IMAGEN_SEPARADAS;
        List<BufferedImage> images = new ArrayList<>();
        int selectedSeries = (incluirCpu ? 1 : 0) + (incluirRam ? 1 : 0) + (incluirDisco ? 1 : 0);
        if (effectiveLayout == ExportLayoutOption.MISMA_IMAGEN_SUPERPUESTAS && selectedSeries >= 2) {
            images.add(renderOverlayExportChart(width, singleChartHeight, exportData, palette, renderOptions));
            return images;
        }
        if (incluirCpu) {
            images.add(cpuChart.renderToImage(width, singleChartHeight, exportData.cpuSamples(), exportData.rangeSeconds(), palette, palette.cpuAccentColor(), renderOptions));
        }
        if (incluirRam) {
            images.add(ramChart.renderToImage(width, singleChartHeight, exportData.ramSamples(), exportData.rangeSeconds(), palette, palette.ramAccentColor(), renderOptions));
        }
        if (incluirDisco) {
            images.add(diskChart.renderToImage(width, singleChartHeight, exportData.diskSamples(), exportData.rangeSeconds(), palette, palette.diskAccentColor(), renderOptions));
        }
        return images;
    }

    private BufferedImage combinarImagenesExportacion(List<BufferedImage> images, int width, ExportPalette palette) {
        if (images == null || images.isEmpty()) {
            return new BufferedImage(Math.max(1, width), 1, BufferedImage.TYPE_INT_ARGB);
        }
        int spacing = images.size() > 1 ? 14 : 0;
        int finalWidth = images.stream().mapToInt(BufferedImage::getWidth).max().orElse(width);
        int finalHeight = images.stream().mapToInt(BufferedImage::getHeight).sum() + (Math.max(0, images.size() - 1) * spacing);
        BufferedImage combined = new BufferedImage(finalWidth, Math.max(1, finalHeight), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = combined.createGraphics();
        try {
            g2.setColor(palette.backgroundColor());
            g2.fillRect(0, 0, finalWidth, finalHeight);
            int y = 0;
            for (int i = 0; i < images.size(); i++) {
                BufferedImage image = images.get(i);
                g2.drawImage(image, 0, y, null);
                y += image.getHeight();
                if (i < images.size() - 1) {
                    y += spacing;
                }
            }
        } finally {
            g2.dispose();
        }
        return combined;
    }

    private BufferedImage combinarImagenesHorizontales(List<BufferedImage> images, ExportPalette palette) {
        if (images == null || images.isEmpty()) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        int spacing = 14;
        int finalWidth = images.stream().mapToInt(BufferedImage::getWidth).sum() + (Math.max(0, images.size() - 1) * spacing);
        int finalHeight = images.stream().mapToInt(BufferedImage::getHeight).max().orElse(1);
        BufferedImage combined = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = combined.createGraphics();
        try {
            g2.setColor(palette.backgroundColor());
            g2.fillRect(0, 0, finalWidth, finalHeight);
            int x = 0;
            for (int i = 0; i < images.size(); i++) {
                BufferedImage image = images.get(i);
                g2.drawImage(image, x, 0, null);
                x += image.getWidth();
                if (i < images.size() - 1) {
                    x += spacing;
                }
            }
        } finally {
            g2.dispose();
        }
        return combined;
    }

    private BufferedImage scaleImageToFit(BufferedImage source, int maxWidth, int maxHeight) {
        if (source == null) {
            return null;
        }
        int safeMaxWidth = Math.max(1, maxWidth);
        int safeMaxHeight = Math.max(1, maxHeight);
        double scale = Math.min(safeMaxWidth / (double) Math.max(1, source.getWidth()),
                safeMaxHeight / (double) Math.max(1, source.getHeight()));
        scale = Math.min(1d, scale);
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g2.dispose();
        }
        return scaled;
    }

    private List<ExportImageTarget> crearDestinosExportacionPng(Path destinoBase,
                                                                ExportSnapshot exportSnapshot,
                                                                long desdeTimestampMillis,
                                                                long hastaTimestampMillis,
                                                                boolean incluirCpu,
                                                                boolean incluirRam,
                                                                boolean incluirDisco,
                                                                ExportLayoutOption disposicion,
                                                                ExportPalette palette,
                                                                ExportRenderOptions renderOptions) {
        List<ExportImageTarget> exportTargets = new ArrayList<>();
        ExportLayoutOption effectiveLayout = disposicion != null ? disposicion : ExportLayoutOption.MISMA_IMAGEN_SEPARADAS;
        int selectedSeries = (incluirCpu ? 1 : 0) + (incluirRam ? 1 : 0) + (incluirDisco ? 1 : 0);
        if (effectiveLayout == ExportLayoutOption.ARCHIVOS_SEPARADOS && selectedSeries >= 2) {
            ExportData exportData = buildExportData(exportSnapshot, desdeTimestampMillis, hastaTimestampMillis);
            if (incluirCpu) {
                exportTargets.add(new ExportImageTarget(
                        appendToFileName(destinoBase, "-cpu"),
                        cpuChart.renderToImage(1400, 340, exportData.cpuSamples(), exportData.rangeSeconds(), palette, palette.cpuAccentColor(), renderOptions)
                ));
            }
            if (incluirRam) {
            exportTargets.add(new ExportImageTarget(
                    appendToFileName(destinoBase, "-ram"),
                    ramChart.renderToImage(1400, 340, exportData.ramSamples(), exportData.rangeSeconds(), palette, palette.ramAccentColor(), renderOptions)
            ));
            }
            if (incluirDisco) {
            exportTargets.add(new ExportImageTarget(
                    appendToFileName(destinoBase, "-disco"),
                    diskChart.renderToImage(1400, 340, exportData.diskSamples(), exportData.rangeSeconds(), palette, palette.diskAccentColor(), renderOptions)
            ));
            }
            return exportTargets;
        }
        exportTargets.add(new ExportImageTarget(
                destinoBase,
                crearImagenExportacion(1400, 340, exportSnapshot, desdeTimestampMillis, hastaTimestampMillis, incluirCpu, incluirRam, incluirDisco, effectiveLayout, palette, renderOptions)
        ));
        return exportTargets;
    }

    private Path appendToFileName(Path path, String suffix) {
        String fileName = path.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex >= 0 ? fileName.substring(0, extensionIndex) : fileName;
        String extension = extensionIndex >= 0 ? fileName.substring(extensionIndex) : "";
        return path.resolveSibling(baseName + suffix + extension);
    }

    private ExportPaletteModel createDefaultExportPaletteModel() {
        Color mainAccent = AppTheme.getMainAccent();
        Color cpuAccent = AppTheme.tint(mainAccent, AppTheme.getSuccessColor(), 0.25f);
        Color diskAccent = AppTheme.tint(mainAccent, AppTheme.getWarningColor(), 0.45f);
        return new ExportPaletteModel(
                AppTheme.getSurfaceBackground(),
                AppTheme.getBorderColor(),
                AppTheme.getSubtleBorderColor(),
                AppTheme.getForeground(),
                cpuAccent,
                mainAccent,
                diskAccent
        );
    }

    private ExportRenderOptionsModel createDefaultExportRenderOptionsModel() {
        return new ExportRenderOptionsModel(
                true,
                true,
                true,
                true,
                true,
                true
        );
    }

    private ExportSnapshot createFrozenExportSnapshot() {
        int bucketSeconds = getStatsHistoricalResolutionSeconds();
        List<ChartSample> cpuTimeline = statsHistory.snapshotTimelineCpu(bucketSeconds);
        List<ChartSample> ramTimeline = statsHistory.snapshotTimelineRam(bucketSeconds);
        List<ChartSample> diskTimeline = statsHistory.snapshotTimelineDisk(bucketSeconds);
        TreeMap<Long, Long> timestamps = new TreeMap<>();
        for (ChartSample sample : cpuTimeline) {
            if (sample != null) {
                timestamps.put(sample.timestampMillis(), sample.timestampMillis());
            }
        }
        for (ChartSample sample : ramTimeline) {
            if (sample != null) {
                timestamps.put(sample.timestampMillis(), sample.timestampMillis());
            }
        }
        for (ChartSample sample : diskTimeline) {
            if (sample != null) {
                timestamps.put(sample.timestampMillis(), sample.timestampMillis());
            }
        }
        List<Long> availableTimestamps = new ArrayList<>(timestamps.values());
        long minTimestamp = availableTimestamps.isEmpty() ? 0L : availableTimestamps.get(0);
        long maxTimestamp = availableTimestamps.isEmpty() ? 0L : availableTimestamps.get(availableTimestamps.size() - 1);
        return new ExportSnapshot(
                cpuTimeline,
                ramTimeline,
                diskTimeline,
                availableTimestamps,
                100,
                Math.max(1, resolveMaxRamMb()),
                100,
                minTimestamp,
                maxTimestamp
        );
    }

    private DefaultComboBoxModel<ExportInstantOption> createExportInstantModel(ExportSnapshot snapshot, boolean startModel) {
        DefaultComboBoxModel<ExportInstantOption> model = new DefaultComboBoxModel<>();
        model.addElement(startModel ? new ExportInstantOption("Principio", null, true, false)
                : new ExportInstantOption("Final", null, false, true));
        if (snapshot != null && snapshot.availableTimestamps() != null) {
            for (Long timestamp : snapshot.availableTimestamps()) {
                if (timestamp == null) {
                    continue;
                }
                model.addElement(new ExportInstantOption(SAMPLE_TIME_FORMAT.format(Instant.ofEpochMilli(timestamp)), timestamp, false, false));
            }
        }
        if (startModel) {
            model.addElement(new ExportInstantOption("Final", null, false, true));
        } else {
            model.insertElementAt(new ExportInstantOption("Principio", null, true, false), 1);
        }
        return model;
    }

    private long resolveExportBoundary(ExportInstantOption option, ExportSnapshot snapshot, boolean startBoundary) {
        if (snapshot == null) {
            return 0L;
        }
        if (option == null) {
            return startBoundary ? snapshot.minTimestampMillis() : snapshot.maxTimestampMillis();
        }
        if (option.timestampMillis() != null) {
            return option.timestampMillis();
        }
        if (option.boundaryStart()) {
            return snapshot.minTimestampMillis();
        }
        if (option.boundaryEnd()) {
            return snapshot.maxTimestampMillis();
        }
        return startBoundary ? snapshot.minTimestampMillis() : snapshot.maxTimestampMillis();
    }

    private ExportData buildExportData(ExportSnapshot exportSnapshot, long desdeTimestampMillis, long hastaTimestampMillis) {
        ExportSnapshot snapshot = exportSnapshot != null ? exportSnapshot : createFrozenExportSnapshot();
        long safeStart = Math.min(desdeTimestampMillis, hastaTimestampMillis);
        long safeEnd = Math.max(desdeTimestampMillis, hastaTimestampMillis);
        if (safeStart <= 0L && snapshot.minTimestampMillis() > 0L) {
            safeStart = snapshot.minTimestampMillis();
        }
        if (safeEnd <= 0L && snapshot.maxTimestampMillis() > 0L) {
            safeEnd = snapshot.maxTimestampMillis();
        }
        List<ChartSample> cpuSamples = filterSamplesByTimestamp(snapshot.cpuTimeline(), safeStart, safeEnd);
        List<ChartSample> ramSamples = filterSamplesByTimestamp(snapshot.ramTimeline(), safeStart, safeEnd);
        List<ChartSample> diskSamples = filterSamplesByTimestamp(snapshot.diskTimeline(), safeStart, safeEnd);
        int rangeSeconds = Math.max(1, (int) Math.max(1L, Math.round((safeEnd - safeStart) / 1000d)));
        return new ExportData(cpuSamples, ramSamples, diskSamples, rangeSeconds, snapshot.cpuMaxValue(), snapshot.ramMaxValue(), snapshot.diskMaxValue());
    }

    private List<ChartSample> filterSamplesByTimestamp(List<ChartSample> samples, long desdeTimestampMillis, long hastaTimestampMillis) {
        if (samples == null || samples.isEmpty()) {
            return new ArrayList<>();
        }
        List<ChartSample> filtered = new ArrayList<>();
        for (ChartSample sample : samples) {
            if (sample == null) {
                continue;
            }
            if (sample.timestampMillis() < desdeTimestampMillis || sample.timestampMillis() > hastaTimestampMillis) {
                continue;
            }
            filtered.add(sample);
        }
        return filtered;
    }

    private BufferedImage renderOverlayExportChart(int width,
                                                   int height,
                                                   ExportData exportData,
                                                   ExportPalette palette,
                                                   ExportRenderOptions renderOptions) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(palette.backgroundColor());
            g2.fillRect(0, 0, width, height);
            if (renderOptions == null || renderOptions.showBorder()) {
                g2.setColor(palette.borderColor());
                g2.drawRect(0, 0, width - 1, height - 1);
            }

            int chartWidth = Math.max(1, width - CHART_LEFT_PADDING - CHART_RIGHT_PADDING);
            int chartHeight = Math.max(1, height - CHART_TOP_PADDING - CHART_BOTTOM_PADDING);
            int x0 = CHART_LEFT_PADDING;
            int y0 = CHART_TOP_PADDING;
            int yBottom = y0 + chartHeight;

            g2.setColor(palette.textColor());
            g2.drawString("CPU + RAM + Disco", x0, 16);

            if (renderOptions == null || renderOptions.showGrid()) {
                g2.setColor(palette.gridColor());
                for (int i = 0; i <= 4; i++) {
                    int y = y0 + Math.round((chartHeight * i) / 4f);
                    g2.drawLine(x0, y, x0 + chartWidth, y);
                }
            }
            int stepSeconds = calcularPasoGridSegundosExport(exportData.rangeSeconds());
            for (int seconds = 0; seconds <= exportData.rangeSeconds(); seconds += stepSeconds) {
                float fraction = seconds / (float) exportData.rangeSeconds();
                int x = Math.round(x0 + (fraction * chartWidth));
                if (renderOptions == null || renderOptions.showGrid()) {
                    g2.setColor(palette.gridColor());
                    g2.drawLine(x, y0, x, yBottom);
                }
                if (seconds < exportData.rangeSeconds()) {
                    String label = formatRangeLabelExport(exportData.rangeSeconds() - seconds);
                    FontMetrics fm = g2.getFontMetrics();
                    int labelX = Math.max(x0, Math.min(x0 + chartWidth - fm.stringWidth(label), x - (fm.stringWidth(label) / 2)));
                    g2.setColor(palette.textColor());
                    g2.drawString(label, labelX, yBottom + 16);
                }
            }

            drawOverlaySeries(g2, exportData.cpuSamples(), exportData.cpuMaxValue(), x0, yBottom, chartWidth, chartHeight, palette.cpuAccentColor(), palette.backgroundColor(), renderOptions);
            drawOverlaySeries(g2, exportData.ramSamples(), exportData.ramMaxValue(), x0, yBottom, chartWidth, chartHeight, palette.ramAccentColor(), palette.backgroundColor(), renderOptions);
            drawOverlaySeries(g2, exportData.diskSamples(), exportData.diskMaxValue(), x0, yBottom, chartWidth, chartHeight, palette.diskAccentColor(), palette.backgroundColor(), renderOptions);

            if (renderOptions == null || renderOptions.showLegend()) {
                int legendY = 18;
                g2.setColor(palette.cpuAccentColor());
                g2.fillRect(width - 250, legendY - 8, 16, 8);
                g2.setColor(palette.textColor());
                g2.drawString("CPU", width - 228, legendY);
                g2.setColor(palette.ramAccentColor());
                g2.fillRect(width - 180, legendY - 8, 16, 8);
                g2.setColor(palette.textColor());
                g2.drawString("RAM", width - 158, legendY);
                g2.setColor(palette.diskAccentColor());
                g2.fillRect(width - 100, legendY - 8, 16, 8);
                g2.setColor(palette.textColor());
                g2.drawString("Disco", width - 78, legendY);
            }
        } finally {
            g2.dispose();
        }
        return image;
    }

    private void drawOverlaySeries(Graphics2D g2,
                                   List<ChartSample> samples,
                                   long maxValue,
                                   int x0,
                                   int yBottom,
                                   int chartWidth,
                                   int chartHeight,
                                   Color accent,
                                   Color backgroundColor,
                                   ExportRenderOptions renderOptions) {
        List<ChartSample> values = samples != null ? samples : List.of();
        if (values.isEmpty()) {
            return;
        }
        Path2D.Float line = new Path2D.Float();
        Path2D.Float area = new Path2D.Float();
        boolean started = false;
        int latestIndex = -1;
        long latest = 0L;
        for (int i = 0; i < values.size(); i++) {
            ChartSample chartSample = values.get(i);
            if (chartSample == null) {
                continue;
            }
            long sample = Math.min(chartSample.value(), Math.max(1L, maxValue));
            float ratio = Math.max(0f, Math.min(1f, sample / (float) Math.max(1L, maxValue)));
            float x = x0 + (i / (float) Math.max(1, values.size() - 1)) * chartWidth;
            float y = yBottom - (ratio * chartHeight);
            if (!started) {
                started = true;
                line.moveTo(x, y);
                area.moveTo(x, yBottom);
                area.lineTo(x, y);
            } else {
                line.lineTo(x, y);
                area.lineTo(x, y);
            }
            latestIndex = i;
            latest = sample;
        }
        if (!started) {
            return;
        }
        area.lineTo(x0 + chartWidth, yBottom);
        area.closePath();
        if (renderOptions == null || renderOptions.showAreaFill()) {
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 52));
            g2.fill(area);
        }
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(line);
        if ((renderOptions == null || renderOptions.showLatestValue()) && latestIndex >= 0) {
            float latestRatio = Math.max(0f, Math.min(1f, latest / (float) Math.max(1L, maxValue)));
            int latestX = Math.round(x0 + (latestIndex / (float) Math.max(1, values.size() - 1)) * chartWidth);
            int latestY = Math.round(yBottom - (latestRatio * chartHeight));
            g2.setColor(backgroundColor);
            g2.fillOval(latestX - 5, latestY - 5, 10, 10);
            g2.setColor(accent);
            g2.fillOval(latestX - 3, latestY - 3, 6, 6);
        }
        if (renderOptions != null && !renderOptions.showShutdownMarkers()) {
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            ChartSample chartSample = values.get(i);
            if (chartSample == null || !chartSample.isShutdownMarker()) {
                continue;
            }
            long sample = Math.min(chartSample.value(), Math.max(1L, maxValue));
            float ratio = Math.max(0f, Math.min(1f, sample / (float) Math.max(1L, maxValue)));
            int shutdownX = Math.round(x0 + (i / (float) Math.max(1, values.size() - 1)) * chartWidth);
            int shutdownY = Math.round(yBottom - (ratio * chartHeight));
            g2.setColor(Color.BLACK);
            g2.fillOval(shutdownX - 3, shutdownY - 3, 6, 6);
        }
    }

    private void escribirMuestrasCsv(BufferedWriter writer, String metric, List<ChartSample> samples) throws IOException {
        for (ChartSample sample : samples) {
            if (sample == null) {
                continue;
            }
            writer.write(metric);
            writer.write(',');
            writer.write(Instant.ofEpochMilli(sample.timestampMillis()).toString());
            writer.write(',');
            writer.write(Long.toString(sample.timestampMillis()));
            writer.write(',');
            writer.write(Long.toString(sample.value()));
            writer.write(',');
            writer.write(Boolean.toString(sample.isShutdownMarker()));
            writer.newLine();
        }
    }

    private void confirmarSobrescrituraSiHaceFalta(Component parent, Path destino) throws IOException {
        if (!Files.exists(destino)) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                parent,
                "El archivo ya existe. ¿Quieres reemplazarlo?",
                "Exportar gráficas",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) {
            throw new IOException("EXPORT_CANCELLED");
        }
    }

    private String getSuggestedExportName(String extension) {
        String serverName = server != null && server.getDisplayName() != null && !server.getDisplayName().isBlank()
                ? server.getDisplayName().replaceAll("[^a-zA-Z0-9._-]", "_")
                : "estadisticas";
        return serverName + "-stats." + extension;
    }

    private int calcularPasoGridSegundosExport(int totalSeconds) {
        if (totalSeconds <= 60) return 10;
        if (totalSeconds <= 300) return 60;
        if (totalSeconds <= 600) return 120;
        if (totalSeconds <= 1800) return 300;
        return 600;
    }

    private String formatRangeLabelExport(int seconds) {
        if (seconds < 60) {
            return "-" + seconds + "s";
        }
        int minutes = seconds / 60;
        if (seconds % 60 == 0) {
            return "-" + minutes + "m";
        }
        return "-" + minutes + "m " + (seconds % 60) + "s";
    }

    private enum UsageMode {
        MEGABYTES,
        PERCENT,
        COUNT
    }

    private enum StatsChartOption {
        CPU("CPU", true),
        RAM("RAM", true),
        DISK("Disco", true),
        PLAYERS("Número de jugadores", false);

        private final String label;
        private final boolean defaultEnabled;

        StatsChartOption(String label, boolean defaultEnabled) {
            this.label = label;
            this.defaultEnabled = defaultEnabled;
        }

        private boolean defaultEnabled() {
            return defaultEnabled;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record ExportPalette(Color backgroundColor,
                                 Color borderColor,
                                 Color gridColor,
                                 Color textColor,
                                 Color cpuAccentColor,
                                 Color ramAccentColor,
                                 Color diskAccentColor) {
    }

    private record ExportRenderOptions(boolean showGrid,
                                       boolean showLegend,
                                       boolean showAreaFill,
                                       boolean showBorder,
                                       boolean showLatestValue,
                                       boolean showShutdownMarkers) {
    }

    private record ExportData(List<ChartSample> cpuSamples,
                              List<ChartSample> ramSamples,
                              List<ChartSample> diskSamples,
                              int rangeSeconds,
                              long cpuMaxValue,
                              long ramMaxValue,
                              long diskMaxValue) {
    }

    private record ExportSnapshot(List<ChartSample> cpuTimeline,
                                  List<ChartSample> ramTimeline,
                                  List<ChartSample> diskTimeline,
                                  List<Long> availableTimestamps,
                                  long cpuMaxValue,
                                  long ramMaxValue,
                                  long diskMaxValue,
                                  long minTimestampMillis,
                                  long maxTimestampMillis) {
    }

    private static final class ExportPaletteModel {
        private Color backgroundColor;
        private Color borderColor;
        private Color gridColor;
        private Color textColor;
        private Color cpuAccentColor;
        private Color ramAccentColor;
        private Color diskAccentColor;

        private ExportPaletteModel(Color backgroundColor,
                                   Color borderColor,
                                   Color gridColor,
                                   Color textColor,
                                   Color cpuAccentColor,
                                   Color ramAccentColor,
                                   Color diskAccentColor) {
            this.backgroundColor = backgroundColor;
            this.borderColor = borderColor;
            this.gridColor = gridColor;
            this.textColor = textColor;
            this.cpuAccentColor = cpuAccentColor;
            this.ramAccentColor = ramAccentColor;
            this.diskAccentColor = diskAccentColor;
        }

        private ExportPalette toPalette() {
            return new ExportPalette(backgroundColor, borderColor, gridColor, textColor, cpuAccentColor, ramAccentColor, diskAccentColor);
        }
    }

    private static final class ExportRenderOptionsModel {
        private boolean showGrid;
        private boolean showLegend;
        private boolean showAreaFill;
        private boolean showBorder;
        private boolean showLatestValue;
        private boolean showShutdownMarkers;

        private ExportRenderOptionsModel(boolean showGrid,
                                         boolean showLegend,
                                         boolean showAreaFill,
                                         boolean showBorder,
                                         boolean showLatestValue,
                                         boolean showShutdownMarkers) {
            this.showGrid = showGrid;
            this.showLegend = showLegend;
            this.showAreaFill = showAreaFill;
            this.showBorder = showBorder;
            this.showLatestValue = showLatestValue;
            this.showShutdownMarkers = showShutdownMarkers;
        }

        private ExportRenderOptions toOptions() {
            return new ExportRenderOptions(showGrid, showLegend, showAreaFill, showBorder, showLatestValue, showShutdownMarkers);
        }
    }

    private record ExportImageTarget(Path path, BufferedImage image) {
    }

    private record ExportInstantOption(String label, Long timestampMillis, boolean boundaryStart, boolean boundaryEnd) {
        @Override
        public String toString() {
            return label;
        }
    }

    private enum ExportFormatOption {
        PNG("PNG"),
        CSV("CSV");

        private final String label;

        ExportFormatOption(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum ExportLayoutOption {
        MISMA_IMAGEN_SEPARADAS("Misma imagen, separadas"),
        MISMA_IMAGEN_SUPERPUESTAS("Misma imagen, superpuestas"),
        ARCHIVOS_SEPARADOS("Archivos separados");

        private final String label;

        ExportLayoutOption(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum TimeRangeOption {
        SECONDS_60("60 s", 60),
        MINUTES_5("5 min", 300),
        MINUTES_10("10 min", 600),
        MINUTES_30("30 min", 1800),
        HOUR_1("1 h", 3600);

        private final String label;
        private final int seconds;

        TimeRangeOption(String label, int seconds) {
            this.label = label;
            this.seconds = seconds;
        }

        private int seconds() {
            return seconds;
        }

        private static TimeRangeOption fromSeconds(int seconds) {
            for (TimeRangeOption option : values()) {
                if (option.seconds == seconds) {
                    return option;
                }
            }
            return SECONDS_60;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record DiskActivitySample(double percent, long readBytesPerSec, long writeBytesPerSec) {
    }

    private record TimedValue<T>(T value, long atMillis) {
    }

    private record ProcessMetricsSample(double cpuPercent, DiskActivitySample diskSample) {
        private ProcessMetricsSample {
            diskSample = diskSample != null ? diskSample : new DiskActivitySample(0d, 0L, 0L);
        }
    }

    private record ChartSample(long value, long timestampMillis, Boolean shutdownMarker) {
        private ChartSample(long value, long timestampMillis) {
            this(value, timestampMillis, false);
        }

        private ChartSample {
            shutdownMarker = Boolean.TRUE.equals(shutdownMarker);
        }

        private boolean isShutdownMarker() {
            return Boolean.TRUE.equals(shutdownMarker);
        }
    }

    private static final class AggregateSample {
        public long bucketStartMillis;
        public long sum;
        public long count;

        public AggregateSample() {
        }

        private AggregateSample(long bucketStartMillis) {
            this.bucketStartMillis = bucketStartMillis;
        }

        private void add(long value) {
            sum += value;
            count++;
        }
    }

    private static final class PersistedStatsHistory {
        public List<ChartSample> recentCpu = new ArrayList<>();
        public List<ChartSample> recentRam = new ArrayList<>();
        public List<ChartSample> recentDisk = new ArrayList<>();
        public List<ChartSample> recentPlayers = new ArrayList<>();
        public List<AggregateSample> archiveCpu = new ArrayList<>();
        public List<AggregateSample> archiveRam = new ArrayList<>();
        public List<AggregateSample> archiveDisk = new ArrayList<>();
        public List<AggregateSample> archivePlayers = new ArrayList<>();
        public double lastCpuPercent;
        public long lastRamValue;
        public double lastDiskPercent;
        public long lastDiskReadBytesPerSec;
        public long lastDiskWriteBytesPerSec;
        public long lastPlayersValue;
        public boolean active;
    }

    private static final class StatsHistory {
        private final Deque<ChartSample> cpuSamples = new ArrayDeque<>();
        private final Deque<ChartSample> ramSamples = new ArrayDeque<>();
        private final Deque<ChartSample> diskSamples = new ArrayDeque<>();
        private final Deque<ChartSample> playerSamples = new ArrayDeque<>();
        private final TreeMap<Long, AggregateSample> cpuArchive = new TreeMap<>();
        private final TreeMap<Long, AggregateSample> ramArchive = new TreeMap<>();
        private final TreeMap<Long, AggregateSample> diskArchive = new TreeMap<>();
        private final TreeMap<Long, AggregateSample> playerArchive = new TreeMap<>();
        private volatile double lastCpuPercent = 0d;
        private volatile long lastRamValue = 0L;
        private volatile DiskActivitySample lastDiskSample = new DiskActivitySample(0d, 0L, 0L);
        private volatile long lastPlayersValue = 0L;
        private volatile boolean active = false;
        private volatile boolean dirty = false;
        private final LinkedHashSet<String> connectedPlayers = new LinkedHashSet<>();
        private int processedRawLogCount = 0;

        synchronized void compactAll(int recentWindowSeconds, int bucketSeconds) {
            int before = cpuSamples.size() + ramSamples.size() + diskSamples.size() + playerSamples.size();
            compactOldSamples(cpuSamples, cpuArchive, recentWindowSeconds, bucketSeconds);
            compactOldSamples(ramSamples, ramArchive, recentWindowSeconds, bucketSeconds);
            compactOldSamples(diskSamples, diskArchive, recentWindowSeconds, bucketSeconds);
            compactOldSamples(playerSamples, playerArchive, recentWindowSeconds, bucketSeconds);
            int after = cpuSamples.size() + ramSamples.size() + diskSamples.size() + playerSamples.size();
            if (after != before) {
                dirty = true;
            }
        }

        private synchronized void addCpuSample(long value, long timestampMillis, int recentWindowSeconds, int bucketSeconds) {
            addSample(cpuSamples, value, timestampMillis);
            lastCpuPercent = value;
            compactOldSamples(cpuSamples, cpuArchive, recentWindowSeconds, bucketSeconds);
            dirty = true;
        }

        private synchronized void addRamSample(long value, long timestampMillis, int recentWindowSeconds, int bucketSeconds) {
            addSample(ramSamples, value, timestampMillis);
            lastRamValue = value;
            compactOldSamples(ramSamples, ramArchive, recentWindowSeconds, bucketSeconds);
            dirty = true;
        }

        private synchronized void addDiskSample(long value, long timestampMillis, int recentWindowSeconds, int bucketSeconds) {
            addSample(diskSamples, value, timestampMillis);
            compactOldSamples(diskSamples, diskArchive, recentWindowSeconds, bucketSeconds);
            dirty = true;
        }

        private synchronized void addPlayerSample(long value, long timestampMillis, int recentWindowSeconds, int bucketSeconds) {
            addSample(playerSamples, value, timestampMillis);
            lastPlayersValue = value;
            compactOldSamples(playerSamples, playerArchive, recentWindowSeconds, bucketSeconds);
            dirty = true;
        }

        private synchronized List<ChartSample> snapshotForRangeCpu(int seconds, int offsetSeconds, int bucketSeconds) {
            return buildSnapshot(cpuSamples, cpuArchive, seconds, offsetSeconds, bucketSeconds);
        }

        private synchronized List<ChartSample> snapshotForRange(int seconds, int offsetSeconds, int bucketSeconds) {
            return buildSnapshot(ramSamples, ramArchive, seconds, offsetSeconds, bucketSeconds);
        }

        private synchronized List<ChartSample> snapshotForRangeDisk(int seconds, int offsetSeconds, int bucketSeconds) {
            return buildSnapshot(diskSamples, diskArchive, seconds, offsetSeconds, bucketSeconds);
        }

        private synchronized List<ChartSample> snapshotForRangePlayers(int seconds, int offsetSeconds, int bucketSeconds) {
            return buildSnapshot(playerSamples, playerArchive, seconds, offsetSeconds, bucketSeconds);
        }

        private synchronized List<ChartSample> snapshotTimelineCpu(int bucketSeconds) {
            return new ArrayList<>(buildTimelineSamples(cpuSamples, cpuArchive, bucketSeconds));
        }

        private synchronized List<ChartSample> snapshotTimelineRam(int bucketSeconds) {
            return new ArrayList<>(buildTimelineSamples(ramSamples, ramArchive, bucketSeconds));
        }

        private synchronized List<ChartSample> snapshotTimelineDisk(int bucketSeconds) {
            return new ArrayList<>(buildTimelineSamples(diskSamples, diskArchive, bucketSeconds));
        }

        private synchronized List<ChartSample> snapshotTimelinePlayers(int bucketSeconds) {
            return new ArrayList<>(buildTimelineSamples(playerSamples, playerArchive, bucketSeconds));
        }

        private void addSample(Deque<ChartSample> samples, long value, long timestampMillis) {
            addSample(samples, value, timestampMillis, false);
        }

        private void addSample(Deque<ChartSample> samples, long value, long timestampMillis, boolean shutdownMarker) {
            samples.addLast(new ChartSample(Math.max(0L, value), timestampMillis, shutdownMarker));
        }

        private synchronized void markActive() {
            active = true;
        }

        private synchronized void markInactive() {
            active = false;
        }

        private synchronized boolean isActive() {
            return active;
        }

        private synchronized void addShutdownSamples(long timestampMillis, int recentWindowSeconds, int bucketSeconds) {
            addSample(cpuSamples, 0L, timestampMillis, true);
            addSample(ramSamples, 0L, timestampMillis, true);
            addSample(diskSamples, 0L, timestampMillis, true);
            addSample(playerSamples, 0L, timestampMillis, true);
            compactOldSamples(cpuSamples, cpuArchive, recentWindowSeconds, bucketSeconds);
            compactOldSamples(ramSamples, ramArchive, recentWindowSeconds, bucketSeconds);
            compactOldSamples(diskSamples, diskArchive, recentWindowSeconds, bucketSeconds);
            compactOldSamples(playerSamples, playerArchive, recentWindowSeconds, bucketSeconds);
            lastCpuPercent = 0d;
            lastRamValue = 0L;
            lastDiskSample = new DiskActivitySample(0d, 0L, 0L);
            lastPlayersValue = 0L;
            connectedPlayers.clear();
            processedRawLogCount = 0;
            active = false;
            dirty = true;
        }

        private double lastCpuPercent() {
            return lastCpuPercent;
        }

        private long lastRamValue() {
            return lastRamValue;
        }

        private double lastDiskPercent() {
            return lastDiskSample.percent();
        }

        private DiskActivitySample lastDiskSample() {
            return lastDiskSample;
        }

        private long lastPlayersValue() {
            return lastPlayersValue;
        }

        private synchronized long syncAndGetCurrentPlayers(Server server) {
            if (server == null) {
                connectedPlayers.clear();
                processedRawLogCount = 0;
                lastPlayersValue = 0L;
                return 0L;
            }
            List<String> rawLogLines = server.getRawLogLines();
            if (rawLogLines == null) {
                return connectedPlayers.size();
            }
            if (rawLogLines.size() < processedRawLogCount) {
                connectedPlayers.clear();
                processedRawLogCount = 0;
            }
            for (int i = processedRawLogCount; i < rawLogLines.size(); i++) {
                String line = rawLogLines.get(i);
                if (line == null || line.isBlank()) {
                    continue;
                }
                Matcher joinMatcher = PLAYER_JOIN.matcher(line);
                if (joinMatcher.find()) {
                    connectedPlayers.add(joinMatcher.group(1));
                    continue;
                }
                Matcher leftMatcher = PLAYER_LEFT.matcher(line);
                if (leftMatcher.find()) {
                    connectedPlayers.remove(leftMatcher.group(1));
                }
            }
            processedRawLogCount = rawLogLines.size();
            lastPlayersValue = connectedPlayers.size();
            return lastPlayersValue;
        }

        private void setLastDiskSample(DiskActivitySample sample) {
            lastDiskSample = sample != null ? sample : new DiskActivitySample(0d, 0L, 0L);
            dirty = true;
        }

        private List<ChartSample> buildSnapshot(Deque<ChartSample> recentSamples,
                                                TreeMap<Long, AggregateSample> archiveSamples,
                                                int seconds,
                                                int offsetSeconds,
                                                int bucketSeconds) {
            int rangeSeconds = Math.max(1, seconds);
            List<ChartSample> timeline = buildTimelineSamples(recentSamples, archiveSamples, bucketSeconds);
            List<ChartSample> snapshot = new ArrayList<>(rangeSeconds);
            for (int i = 0; i < rangeSeconds; i++) {
                snapshot.add(null);
            }

            if (timeline.isEmpty()) {
                return snapshot;
            }

            int safeOffset = Math.max(0, offsetSeconds);
            int endIndexExclusive = Math.max(0, timeline.size() - safeOffset);
            int startIndex = Math.max(0, endIndexExclusive - rangeSeconds);
            int copyStart = Math.max(0, rangeSeconds - (endIndexExclusive - startIndex));
            for (int timelineIndex = startIndex; timelineIndex < endIndexExclusive; timelineIndex++) {
                int snapshotIndex = copyStart + (timelineIndex - startIndex);
                if (snapshotIndex >= 0 && snapshotIndex < snapshot.size()) {
                    snapshot.set(snapshotIndex, timeline.get(timelineIndex));
                }
            }
            return snapshot;
        }

        private List<ChartSample> buildTimelineSamples(Deque<ChartSample> recentSamples,
                                                       TreeMap<Long, AggregateSample> archiveSamples,
                                                       int bucketSeconds) {
            List<ChartSample> timeline = new ArrayList<>(archiveSamples.size() + recentSamples.size());
            for (AggregateSample aggregate : archiveSamples.values()) {
                if (aggregate == null || aggregate.count <= 0L) {
                    continue;
                }
                long averageValue = Math.round(aggregate.sum / (double) aggregate.count);
                timeline.add(new ChartSample(averageValue, aggregate.bucketStartMillis, false));
            }
            timeline.addAll(recentSamples);
            return timeline;
        }

        private void compactOldSamples(Deque<ChartSample> recentSamples,
                                       TreeMap<Long, AggregateSample> archiveSamples,
                                       int recentWindowSeconds,
                                       int bucketSeconds) {
            if (recentSamples.isEmpty() || recentWindowSeconds <= 0 || bucketSeconds <= 0) {
                return;
            }

            long cutoffTimestamp = System.currentTimeMillis() - (recentWindowSeconds * 1000L);
            while (!recentSamples.isEmpty()) {
                ChartSample sample = recentSamples.peekFirst();
                if (sample == null || sample.timestampMillis() >= cutoffTimestamp) {
                    break;
                }
                recentSamples.removeFirst();
                long bucketStart = (sample.timestampMillis() / (bucketSeconds * 1000L)) * (bucketSeconds * 1000L);
                AggregateSample aggregate = archiveSamples.computeIfAbsent(bucketStart, ignored -> new AggregateSample(bucketStart));
                aggregate.add(sample.value());
            }
        }

        private synchronized int getMaxOffsetSeconds(int rangeSeconds, int bucketSeconds) {
            int totalSamples = Math.max(snapshotTimelineCpu(bucketSeconds).size(),
                    Math.max(snapshotTimelineRam(bucketSeconds).size(),
                            Math.max(snapshotTimelineDisk(bucketSeconds).size(),
                                    snapshotTimelinePlayers(bucketSeconds).size())));
            return Math.max(0, totalSamples - Math.max(1, rangeSeconds));
        }

        private synchronized long getWindowEndTimestamp(int offsetSeconds, int bucketSeconds) {
            List<ChartSample> timeline = snapshotTimelineRam(bucketSeconds);
            if (snapshotTimelineCpu(bucketSeconds).size() > timeline.size()) {
                timeline = snapshotTimelineCpu(bucketSeconds);
            }
            if (snapshotTimelineDisk(bucketSeconds).size() > timeline.size()) {
                timeline = snapshotTimelineDisk(bucketSeconds);
            }
            if (snapshotTimelinePlayers(bucketSeconds).size() > timeline.size()) {
                timeline = snapshotTimelinePlayers(bucketSeconds);
            }
            if (timeline.isEmpty()) {
                return 0L;
            }
            int safeOffset = Math.max(0, Math.min(offsetSeconds, timeline.size() - 1));
            return timeline.get(timeline.size() - 1 - safeOffset).timestampMillis();
        }

        private synchronized PersistedStatsHistory toPersistedModel() {
            PersistedStatsHistory model = new PersistedStatsHistory();
            model.recentCpu = new ArrayList<>(cpuSamples);
            model.recentRam = new ArrayList<>(ramSamples);
            model.recentDisk = new ArrayList<>(diskSamples);
            model.recentPlayers = new ArrayList<>(playerSamples);
            model.archiveCpu = new ArrayList<>(cpuArchive.values());
            model.archiveRam = new ArrayList<>(ramArchive.values());
            model.archiveDisk = new ArrayList<>(diskArchive.values());
            model.archivePlayers = new ArrayList<>(playerArchive.values());
            model.lastCpuPercent = lastCpuPercent;
            model.lastRamValue = lastRamValue;
            model.lastDiskPercent = lastDiskSample.percent();
            model.lastDiskReadBytesPerSec = lastDiskSample.readBytesPerSec();
            model.lastDiskWriteBytesPerSec = lastDiskSample.writeBytesPerSec();
            model.lastPlayersValue = lastPlayersValue;
            model.active = active;
            return model;
        }

        private static StatsHistory fromPersistedModel(PersistedStatsHistory model) {
            StatsHistory history = new StatsHistory();
            if (model == null) {
                return history;
            }
            if (model.recentCpu != null) {
                history.cpuSamples.addAll(model.recentCpu);
            }
            if (model.recentRam != null) {
                history.ramSamples.addAll(model.recentRam);
            }
            if (model.recentDisk != null) {
                history.diskSamples.addAll(model.recentDisk);
            }
            if (model.recentPlayers != null) {
                history.playerSamples.addAll(model.recentPlayers);
            }
            if (model.archiveCpu != null) {
                for (AggregateSample sample : model.archiveCpu) {
                    if (sample != null) {
                        history.cpuArchive.put(sample.bucketStartMillis, sample);
                    }
                }
            }
            if (model.archiveRam != null) {
                for (AggregateSample sample : model.archiveRam) {
                    if (sample != null) {
                        history.ramArchive.put(sample.bucketStartMillis, sample);
                    }
                }
            }
            if (model.archiveDisk != null) {
                for (AggregateSample sample : model.archiveDisk) {
                    if (sample != null) {
                        history.diskArchive.put(sample.bucketStartMillis, sample);
                    }
                }
            }
            if (model.archivePlayers != null) {
                for (AggregateSample sample : model.archivePlayers) {
                    if (sample != null) {
                        history.playerArchive.put(sample.bucketStartMillis, sample);
                    }
                }
            }
            history.lastCpuPercent = model.lastCpuPercent;
            history.lastRamValue = model.lastRamValue;
            history.lastDiskSample = new DiskActivitySample(
                    model.lastDiskPercent,
                    model.lastDiskReadBytesPerSec,
                    model.lastDiskWriteBytesPerSec
            );
            history.lastPlayersValue = model.lastPlayersValue;
            history.active = model.active;
            history.dirty = false;
            return history;
        }

        private synchronized void clear() {
            cpuSamples.clear();
            ramSamples.clear();
            diskSamples.clear();
            playerSamples.clear();
            cpuArchive.clear();
            ramArchive.clear();
            diskArchive.clear();
            playerArchive.clear();
            lastCpuPercent = 0d;
            lastRamValue = 0L;
            lastDiskSample = new DiskActivitySample(0d, 0L, 0L);
            lastPlayersValue = 0L;
            connectedPlayers.clear();
            processedRawLogCount = 0;
            active = false;
            dirty = false;
        }

        private synchronized void clearMetricHistory(StatsChartOption option) {
            if (option == null) {
                return;
            }
            switch (option) {
                case CPU -> {
                    cpuSamples.clear();
                    cpuArchive.clear();
                    lastCpuPercent = 0d;
                }
                case RAM -> {
                    ramSamples.clear();
                    ramArchive.clear();
                    lastRamValue = 0L;
                }
                case DISK -> {
                    diskSamples.clear();
                    diskArchive.clear();
                    lastDiskSample = new DiskActivitySample(0d, 0L, 0L);
                }
                case PLAYERS -> {
                    playerSamples.clear();
                    playerArchive.clear();
                    lastPlayersValue = 0L;
                    connectedPlayers.clear();
                    processedRawLogCount = 0;
                }
            }
            dirty = true;
        }

        private synchronized void replaceWith(StatsHistory other) {
            clear();
            if (other == null) {
                return;
            }
            cpuSamples.addAll(other.cpuSamples);
            ramSamples.addAll(other.ramSamples);
            diskSamples.addAll(other.diskSamples);
            playerSamples.addAll(other.playerSamples);
            cpuArchive.putAll(other.cpuArchive);
            ramArchive.putAll(other.ramArchive);
            diskArchive.putAll(other.diskArchive);
            playerArchive.putAll(other.playerArchive);
            lastCpuPercent = other.lastCpuPercent;
            lastRamValue = other.lastRamValue;
            lastDiskSample = other.lastDiskSample;
            lastPlayersValue = other.lastPlayersValue;
            active = other.active;
            dirty = other.dirty;
        }
    }

    private static StatsHistory getOrCreateHistory(Server server) {
        String key = (server != null && server.getId() != null && !server.getId().isBlank())
                ? server.getId()
                : "__no_server__";
        StatsHistory history = HISTORY_BY_SERVER.get(key);
        if (history != null) {
            return history;
        }
        StatsHistory loadedHistory = loadHistoryFromDisk(server, key);
        HISTORY_BY_SERVER.put(key, loadedHistory);
        return loadedHistory;
    }

    private static StatsHistory loadHistoryFromDisk(Server server, String serverKey) {
        if (server != null && !Boolean.TRUE.equals(server.getEstadisticasPersistenciaActiva())) {
            return new StatsHistory();
        }
        return loadHistoryFromDiskByKey(server, serverKey);
    }

    private static StatsHistory loadHistoryFromDiskByKey(String serverKey) {
        return loadHistoryFromDiskByKey(findServerByKey(serverKey), serverKey);
    }

    private static StatsHistory loadHistoryFromDiskByKey(Server server, String serverKey) {
        Path historyFile = getHistoryFile(server, serverKey);
        if (!Files.exists(historyFile)) {
            return new StatsHistory();
        }
        try {
            PersistedStatsHistory model = HISTORY_MAPPER.readValue(historyFile.toFile(), PersistedStatsHistory.class);
            StatsHistory history = StatsHistory.fromPersistedModel(model);
            // Historiales antiguos podían guardar semanas a 1 s: se agregan ya al cargar.
            history.compactAll(getStatsRecentWindowSeconds(server), getStatsHistoricalResolutionSeconds(server));
            return history;
        } catch (Exception e) {
            System.err.println("No se ha podido cargar el historial de estadísticas de " + serverKey + ": " + e.getMessage());
            return new StatsHistory();
        }
    }

    private static void flushDirtyHistories() {
        for (Map.Entry<String, StatsHistory> entry : HISTORY_BY_SERVER.entrySet()) {
            String serverKey = entry.getKey();
            StatsHistory history = entry.getValue();
            if (serverKey == null || history == null || !history.dirty) {
                continue;
            }
            Server server = findServerByKey(serverKey);
            if (server != null && !Boolean.TRUE.equals(server.getEstadisticasPersistenciaActiva())) {
                continue;
            }
            saveHistoryToDisk(serverKey, history);
        }
    }

    private static void saveHistoryToDisk(String serverKey, StatsHistory history) {
        try {
            Server persistedServer = findServerByKey(serverKey);
            Path historyFile = getHistoryFile(persistedServer, serverKey);
            Files.createDirectories(historyFile.getParent());
            PersistedStatsHistory model = history.toPersistedModel();
            // JSON compacto y escritura atómica: un cierre a mitad no deja el historial corrupto.
            Path tmp = historyFile.resolveSibling(historyFile.getFileName() + ".tmp");
            HISTORY_MAPPER.writeValue(tmp.toFile(), model);
            try {
                Files.move(tmp, historyFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(tmp, historyFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            history.dirty = false;
        } catch (Exception e) {
            System.err.println("No se ha podido guardar el historial de estadísticas de " + serverKey + ": " + e.getMessage());
        }
    }

    private static Path getHistoryFile(Server server, String serverKey) {
        if (server != null && server.getServerDir() != null && !server.getServerDir().isBlank()) {
            return Path.of(server.getServerDir()).resolve("dora-stats.json");
        }
        return getAppStatsHistoryFile(serverKey);
    }

    private static Path getAppStatsHistoryFile(String serverKey) {
        return AppPaths.statsDirectory().resolve(getSafeHistoryFileName(serverKey));
    }

    private static String getSafeHistoryFileName(String serverKey) {
        String safeName = (serverKey == null || serverKey.isBlank()) ? "__no_server__" : serverKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safeName + ".json";
    }

    private static void sampleActiveServers() {
        // Las cachés van por PID; las de procesos que ya terminaron se descartan solas.
        long staleBefore = System.currentTimeMillis() - 12 * EXPENSIVE_SAMPLE_INTERVAL_MS;
        RAM_SAMPLE_CACHE_BY_PID.values().removeIf(cached -> cached.atMillis() < staleBefore);
        METRICS_SAMPLE_CACHE_BY_PID.values().removeIf(cached -> cached.atMillis() < staleBefore);
        for (GestorServidores gestor : REGISTERED_GESTORES) {
            if (gestor == null || gestor.getListaServidores() == null) {
                continue;
            }
            for (Server currentServer : gestor.getListaServidores()) {
                if (currentServer == null) {
                    continue;
                }
                StatsHistory history = getOrCreateHistory(currentServer);
                Process process = currentServer.getServerProcess();
                int recentWindowSeconds = getStatsRecentWindowSeconds(currentServer);
                int bucketSeconds = getStatsHistoricalResolutionSeconds(currentServer);
                if (process == null || !process.isAlive()) {
                    removeCpuSampleState(currentServer, process);
                    if (history.isActive()) {
                        history.addShutdownSamples(System.currentTimeMillis(), recentWindowSeconds, bucketSeconds);
                    } else {
                        history.markInactive();
                    }
                    continue;
                }

                history.markActive();
                int ramMaxMb = Math.max(1, currentServer.getServerConfig() != null ? currentServer.getServerConfig().getRamMax() : 1);
                long rawRamUsage = readServerRamUsageMb(currentServer);
                long ramUsage = rawRamUsage >= 0L
                        ? normalizeStaticUsage(rawRamUsage, ramMaxMb)
                        : normalizeStaticUsage(history.lastRamValue(), ramMaxMb);
                ProcessMetricsSample processMetrics = readServerProcessMetrics(currentServer);
                double cpuPercentValue = Math.max(0d, Math.min(100d, readServerCpuUsagePercent(currentServer, history)));
                int cpuPercent = (int) Math.round(cpuPercentValue);
                DiskActivitySample diskSample = processMetrics.diskSample();
                int diskPercent = (int) Math.round(Math.max(0d, Math.min(100d, diskSample.percent())));
                long playersCount = history.syncAndGetCurrentPlayers(currentServer);
                long timestamp = System.currentTimeMillis();

                history.addCpuSample(cpuPercent, timestamp, recentWindowSeconds, bucketSeconds);
                history.addRamSample(ramUsage, timestamp, recentWindowSeconds, bucketSeconds);
                history.addDiskSample(diskPercent, timestamp, recentWindowSeconds, bucketSeconds);
                history.addPlayerSample(playersCount, timestamp, recentWindowSeconds, bucketSeconds);
                history.setLastDiskSample(diskSample);
            }
        }
    }

    private static ProcessMetricsSample readServerProcessMetrics(Server server) {
        if (server == null) {
            return new ProcessMetricsSample(0d, new DiskActivitySample(0d, 0L, 0L));
        }
        Process process = server.getServerProcess();
        if (process == null || !process.isAlive()) {
            return new ProcessMetricsSample(0d, new DiskActivitySample(0d, 0L, 0L));
        }
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            long rootPid = process.pid();
            long now = System.currentTimeMillis();
            TimedValue<ProcessMetricsSample> cached = METRICS_SAMPLE_CACHE_BY_PID.get(rootPid);
            if (cached != null && now - cached.atMillis() < EXPENSIVE_SAMPLE_INTERVAL_MS) {
                return cached.value();
            }
            ProcessMetricsSample sample = readWindowsProcessMetrics(resolveServerJvm(process).pid());
            METRICS_SAMPLE_CACHE_BY_PID.put(rootPid, new TimedValue<>(sample, now));
            return sample;
        }
        return new ProcessMetricsSample(0d, new DiskActivitySample(0d, 0L, 0L));
    }

    private static double readServerCpuUsagePercent(Server server, StatsHistory history) {
        if (server == null) {
            return 0d;
        }
        Process process = server.getServerProcess();
        if (process == null || !process.isAlive()) {
            return 0d;
        }

        String stateKey = server.getId() != null && !server.getId().isBlank()
                ? server.getId()
                : "pid-" + process.pid();
        long wallClockNanos = System.nanoTime();

        try {
            java.time.Duration totalCpuDuration = resolveServerJvm(process).info().totalCpuDuration().orElse(null);
            if (totalCpuDuration == null) {
                return history != null ? history.lastCpuPercent() : 0d;
            }

            long processCpuNanos = Math.max(0L, totalCpuDuration.toNanos());
            CpuSampleState previous = CPU_SAMPLE_STATE_BY_SERVER.put(stateKey, new CpuSampleState(processCpuNanos, wallClockNanos));
            if (previous == null) {
                return history != null ? history.lastCpuPercent() : 0d;
            }

            long cpuDelta = processCpuNanos - previous.processCpuNanos();
            long wallDelta = wallClockNanos - previous.wallClockNanos();
            if (cpuDelta < 0L || wallDelta <= 0L) {
                return history != null ? history.lastCpuPercent() : 0d;
            }

            int logicalProcessors = Math.max(1, Runtime.getRuntime().availableProcessors());
            double cpuPercent = (cpuDelta * 100d) / (wallDelta * logicalProcessors);
            return Math.max(0d, Math.min(100d, cpuPercent));
        } catch (Exception ignored) {
            return history != null ? history.lastCpuPercent() : 0d;
        }
    }

    private static Server findServerByKey(String serverKey) {
        for (GestorServidores gestor : REGISTERED_GESTORES) {
            if (gestor == null || gestor.getListaServidores() == null) {
                continue;
            }
            for (Server currentServer : gestor.getListaServidores()) {
                if (currentServer == null || currentServer.getId() == null) {
                    continue;
                }
                if (currentServer.getId().equals(serverKey)) {
                    return currentServer;
                }
            }
        }
        return null;
    }

    private static void removeCpuSampleState(Server server, Process process) {
        if (process != null) {
            RAM_SAMPLE_CACHE_BY_PID.remove(process.pid());
            METRICS_SAMPLE_CACHE_BY_PID.remove(process.pid());
            JSTAT_FAILURES_BY_PID.remove(process.pid());
        }
        if (server != null && server.getId() != null && !server.getId().isBlank()) {
            CPU_SAMPLE_STATE_BY_SERVER.remove(server.getId());
            return;
        }
        if (process != null) {
            CPU_SAMPLE_STATE_BY_SERVER.remove("pid-" + process.pid());
        }
    }

    private static long normalizeStaticUsage(long usageMb, long maxMb) {
        long normalizedMb = Math.max(0L, usageMb);
        if (maxMb > 0L) {
            normalizedMb = Math.min(normalizedMb, maxMb);
        }
        return normalizedMb;
    }

    private record CpuSampleState(long processCpuNanos, long wallClockNanos) {
    }

    private final class UsageChart extends JComponent {
        private final String title;
        private final UsageMode mode;
        private final String emptyMessage;
        private long maxValue = 1L;
        private int rangeSeconds = 60;
        private int hoveredSampleIndex = -1;
        private Long hoveredTimestampMillis = null;
        private String lastTooltipText;
        private List<ChartSample> samples = new ArrayList<>();

        private UsageChart(String title, UsageMode mode, String emptyMessage) {
            this.title = title;
            this.mode = mode;
            this.emptyMessage = emptyMessage;
            setOpaque(false);
            setPreferredSize(new Dimension(0, 210));
            setMinimumSize(new Dimension(0, 128));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
            ToolTipManager.sharedInstance().registerComponent(this);
            MouseAdapter hoverListener = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateTooltipAt(e.getX(), e.getY());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoveredSampleIndex = -1;
                    hoveredTimestampMillis = null;
                    lastTooltipText = null;
                    setToolTipText(null);
                    repaint();
                }
            };
            addMouseMotionListener(hoverListener);
            addMouseListener(hoverListener);
        }

        private void setMaxValue(long maxValue) {
            this.maxValue = Math.max(1L, maxValue);
            repaint();
        }

        private void setRangeSeconds(int rangeSeconds) {
            this.rangeSeconds = Math.max(1, rangeSeconds);
            repaint();
        }

        private void setPreferredChartHeight(int preferredHeight) {
            int normalizedHeight = Math.max(128, preferredHeight);
            setPreferredSize(new Dimension(0, normalizedHeight));
            setMinimumSize(new Dimension(0, 128));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
            revalidate();
            repaint();
        }

        private void setSamples(List<ChartSample> samples) {
            this.samples = samples != null ? new ArrayList<>(samples) : new ArrayList<>();
            if (hoveredTimestampMillis != null) {
                hoveredSampleIndex = findSampleIndexByTimestamp(hoveredTimestampMillis);
                if (hoveredSampleIndex < 0) {
                    hoveredTimestampMillis = null;
                    lastTooltipText = null;
                    setToolTipText(null);
                }
            }
            repaint();
        }

        private List<ChartSample> getSamplesSnapshot() {
            return new ArrayList<>(samples);
        }

        private BufferedImage renderToImage(int width,
                                            int height,
                                            List<ChartSample> renderSamples,
                                            int renderRangeSeconds,
                                            ExportPalette palette,
                                            Color accentColor,
                                            ExportRenderOptions renderOptions) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                drawChart(g2, width, height, renderSamples, renderRangeSeconds, palette, accentColor, false, false, renderOptions);
            } finally {
                g2.dispose();
            }
            return image;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                drawChart(g2,
                        getWidth(),
                        getHeight(),
                        samples,
                        rangeSeconds,
                        new ExportPalette(
                                AppTheme.getSurfaceBackground(),
                                AppTheme.getBorderColor(),
                                AppTheme.getSubtleBorderColor(),
                                AppTheme.getForeground(),
                                AppTheme.tint(AppTheme.getMainAccent(), AppTheme.getSuccessColor(), 0.25f),
                                AppTheme.getMainAccent(),
                                AppTheme.getMainAccent()
                        ),
                        AppTheme.getMainAccent(),
                        true,
                        true,
                        createDefaultExportRenderOptionsModel().toOptions());
            } finally {
                g2.dispose();
            }
        }

        private void drawChart(Graphics2D g2,
                               int width,
                               int height,
                               List<ChartSample> renderSamples,
                               int renderRangeSeconds,
                               ExportPalette palette,
                               Color accent,
                               boolean includeHover,
                               boolean roundedCorners,
                               ExportRenderOptions renderOptions) {
            if (width <= 0 || height <= 0) {
                return;
            }

            g2.setColor(palette.backgroundColor());
            if (roundedCorners) {
                int arc = AppTheme.getArc();
                g2.fillRoundRect(0, 0, width, height, arc, arc);
                if (renderOptions == null || renderOptions.showBorder()) {
                    g2.setColor(palette.borderColor());
                    g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
                }
            } else {
                g2.fillRect(0, 0, width, height);
                if (renderOptions == null || renderOptions.showBorder()) {
                    g2.setColor(palette.borderColor());
                    g2.drawRect(0, 0, width - 1, height - 1);
                }
            }

            int chartWidth = Math.max(1, width - CHART_LEFT_PADDING - CHART_RIGHT_PADDING);
            int chartHeight = Math.max(1, height - CHART_TOP_PADDING - CHART_BOTTOM_PADDING);
            int x0 = CHART_LEFT_PADDING;
            int y0 = CHART_TOP_PADDING;
            int yBottom = CHART_TOP_PADDING + chartHeight;

            g2.setColor(palette.textColor());
            g2.drawString(title, x0, 16);
            g2.drawString(formatAxisValue(maxValue), 12, CHART_TOP_PADDING + 5);
            g2.drawString(formatAxisValue(0), 16, yBottom);

            if (renderOptions == null || renderOptions.showGrid()) {
                g2.setColor(palette.gridColor());
                for (int i = 0; i <= 4; i++) {
                    int y = y0 + Math.round((chartHeight * i) / 4f);
                    g2.drawLine(x0, y, x0 + chartWidth, y);
                }
            }
            int safeRangeSeconds = Math.max(1, renderRangeSeconds);
            int stepSeconds = calcularPasoGridSegundos(safeRangeSeconds);
            for (int seconds = 0; seconds <= safeRangeSeconds; seconds += stepSeconds) {
                float fraction = seconds / (float) safeRangeSeconds;
                int x = Math.round(x0 + (fraction * chartWidth));
                if (renderOptions == null || renderOptions.showGrid()) {
                    g2.setColor(palette.gridColor());
                    g2.drawLine(x, y0, x, yBottom);
                }
                if (seconds < safeRangeSeconds) {
                    String label = formatRangeLabel(safeRangeSeconds - seconds);
                    FontMetrics fm = g2.getFontMetrics();
                    int labelX = Math.max(x0, Math.min(x0 + chartWidth - fm.stringWidth(label), x - (fm.stringWidth(label) / 2)));
                    g2.setColor(palette.textColor());
                    g2.drawString(label, labelX, yBottom + 16);
                }
            }

            List<ChartSample> values = renderSamples != null ? new ArrayList<>(renderSamples) : new ArrayList<>();
            boolean hasAnyValue = values.stream().anyMatch(Objects::nonNull);
            if (!hasAnyValue) {
                g2.setColor(palette.textColor());
                drawCenteredString(g2, emptyMessage, new Rectangle(x0, y0, chartWidth, chartHeight));
                return;
            }

            Path2D.Float area = new Path2D.Float();
            Path2D.Float line = new Path2D.Float();
            boolean started = false;
            int latestIndex = -1;
            long latest = 0L;

            for (int i = 0; i < values.size(); i++) {
                ChartSample chartSample = values.get(i);
                if (chartSample == null) {
                    continue;
                }
                long sample = Math.min(chartSample.value(), maxValue);
                float ratio = Math.max(0f, Math.min(1f, sample / (float) maxValue));
                float x = x0 + (i / (float) Math.max(1, values.size() - 1)) * chartWidth;
                float y = yBottom - (ratio * chartHeight);
                if (!started) {
                    started = true;
                    line.moveTo(x, y);
                    area.moveTo(x, yBottom);
                    area.lineTo(x, y);
                } else {
                    line.lineTo(x, y);
                    area.lineTo(x, y);
                }
                latestIndex = i;
                latest = sample;
            }

            if (!started) {
                g2.setColor(palette.textColor());
                drawCenteredString(g2, emptyMessage, new Rectangle(x0, y0, chartWidth, chartHeight));
                return;
            }

            area.lineTo(x0 + chartWidth, yBottom);
            area.closePath();

            if (renderOptions == null || renderOptions.showAreaFill()) {
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 52));
                g2.fill(area);
            }

            g2.setColor(accent);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(line);

            if (renderOptions == null || renderOptions.showShutdownMarkers()) {
                for (int i = 0; i < values.size(); i++) {
                    ChartSample chartSample = values.get(i);
                    if (chartSample == null || !chartSample.isShutdownMarker()) {
                        continue;
                    }
                    long sample = Math.min(chartSample.value(), maxValue);
                    float ratio = Math.max(0f, Math.min(1f, sample / (float) maxValue));
                    int shutdownX = Math.round(x0 + (i / (float) Math.max(1, values.size() - 1)) * chartWidth);
                    int shutdownY = Math.round(yBottom - (ratio * chartHeight));
                    g2.setColor(palette.backgroundColor());
                    g2.fillOval(shutdownX - 5, shutdownY - 5, 10, 10);
                    g2.setColor(Color.BLACK);
                    g2.fillOval(shutdownX - 3, shutdownY - 3, 6, 6);
                }
            }

            if (renderOptions == null || renderOptions.showLatestValue()) {
                float latestRatio = Math.max(0f, Math.min(1f, latest / (float) maxValue));
                int latestX = Math.round(x0 + (latestIndex / (float) Math.max(1, values.size() - 1)) * chartWidth);
                int latestY = Math.round(yBottom - (latestRatio * chartHeight));

                g2.setColor(palette.backgroundColor());
                g2.fillOval(latestX - 5, latestY - 5, 10, 10);
                g2.setColor(values.get(latestIndex) != null && values.get(latestIndex).isShutdownMarker() ? Color.BLACK : accent);
                g2.fillOval(latestX - 3, latestY - 3, 6, 6);

                String latestValueLabel = formatAxisValue(latest);
                FontMetrics latestValueMetrics = g2.getFontMetrics();
                int latestLabelX = Math.min(width - latestValueMetrics.stringWidth(latestValueLabel) - 8, latestX + 12);
                int latestLabelY = Math.max(y0 + latestValueMetrics.getAscent(), Math.min(yBottom - 4, latestY + (latestValueMetrics.getAscent() / 2)));
                g2.setColor(palette.textColor());
                g2.drawString(latestValueLabel, latestLabelX, latestLabelY);
            }

            if (includeHover && hoveredSampleIndex >= 0 && hoveredSampleIndex < values.size() && values.get(hoveredSampleIndex) != null) {
                ChartSample hoveredSample = values.get(hoveredSampleIndex);
                long hoveredValue = Math.min(hoveredSample.value(), maxValue);
                float hoveredRatio = Math.max(0f, Math.min(1f, hoveredValue / (float) maxValue));
                int hoveredX = Math.round(x0 + (hoveredSampleIndex / (float) Math.max(1, values.size() - 1)) * chartWidth);
                int hoveredY = Math.round(yBottom - (hoveredRatio * chartHeight));

                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90));
                g2.drawLine(hoveredX, y0, hoveredX, yBottom);

                g2.setColor(palette.backgroundColor());
                g2.fillOval(hoveredX - 6, hoveredY - 6, 12, 12);
                g2.setColor(hoveredSample.isShutdownMarker() ? Color.BLACK : accent);
                g2.fillOval(hoveredX - 4, hoveredY - 4, 8, 8);
            }

            g2.setColor(palette.textColor());
            g2.drawString("Ahora", x0 + chartWidth - 28, height - 8);
        }

        private String formatAxisValue(long value) {
            if (mode == UsageMode.PERCENT) {
                return value + "%";
            }
            if (mode == UsageMode.COUNT) {
                return Long.toString(value);
            }
            return formatMb(value);
        }

        private void updateTooltipAt(int mouseX, int mouseY) {
            HoverInfo hoverInfo = findNearestSample(mouseX, mouseY);
            if (hoverInfo == null) {
                hoveredSampleIndex = -1;
                hoveredTimestampMillis = null;
                lastTooltipText = null;
                setToolTipText(null);
                repaint();
                return;
            }
            hoveredSampleIndex = hoverInfo.index();
            ChartSample sample = hoverInfo.sample();
            hoveredTimestampMillis = sample.timestampMillis();
            String valueText = mode == UsageMode.PERCENT ? (sample.value() + "%")
                    : mode == UsageMode.COUNT ? Long.toString(sample.value())
                    : formatMb(sample.value());
            String timeText = SAMPLE_TIME_FORMAT.format(Instant.ofEpochMilli(sample.timestampMillis()));
            String tooltipText = "<html><b>" + title + "</b>: " + valueText + "<br>" + timeText + "</html>";
            if (!Objects.equals(lastTooltipText, tooltipText)) {
                lastTooltipText = tooltipText;
                setToolTipText(tooltipText);
                ToolTipManager.sharedInstance().mouseMoved(new MouseEvent(
                        this,
                        MouseEvent.MOUSE_MOVED,
                        System.currentTimeMillis(),
                        0,
                        mouseX,
                        mouseY,
                        0,
                        false
                ));
            }
            repaint();
        }

        private HoverInfo findNearestSample(int mouseX, int mouseY) {
            List<ChartSample> values = new ArrayList<>(samples);
            if (values.isEmpty()) {
                return null;
            }

            int chartWidth = Math.max(1, getWidth() - CHART_LEFT_PADDING - CHART_RIGHT_PADDING);
            int chartHeight = Math.max(1, getHeight() - CHART_TOP_PADDING - CHART_BOTTOM_PADDING);
            int x0 = CHART_LEFT_PADDING;
            int y0 = CHART_TOP_PADDING;
            int yBottom = CHART_TOP_PADDING + chartHeight;
            if (mouseX < x0 || mouseX > x0 + chartWidth || mouseY < y0 || mouseY > yBottom) {
                return null;
            }

            double relativeX = (mouseX - x0) / (double) chartWidth;
            relativeX = Math.max(0d, Math.min(1d, relativeX));
            int nearestIndex = (int) Math.round(relativeX * Math.max(1, values.size() - 1));
            nearestIndex = Math.max(0, Math.min(values.size() - 1, nearestIndex));

            ChartSample sample = values.get(nearestIndex);
            if (sample == null) {
                int leftIndex = nearestIndex - 1;
                int rightIndex = nearestIndex + 1;
                while (leftIndex >= 0 || rightIndex < values.size()) {
                    if (leftIndex >= 0 && values.get(leftIndex) != null) {
                        return new HoverInfo(leftIndex, values.get(leftIndex));
                    }
                    if (rightIndex < values.size() && values.get(rightIndex) != null) {
                        return new HoverInfo(rightIndex, values.get(rightIndex));
                    }
                    leftIndex--;
                    rightIndex++;
                }
                return null;
            }
            return new HoverInfo(nearestIndex, sample);
        }

        private int findSampleIndexByTimestamp(long timestampMillis) {
            for (int i = 0; i < samples.size(); i++) {
                ChartSample sample = samples.get(i);
                if (sample != null && sample.timestampMillis() == timestampMillis) {
                    return i;
                }
            }
            return -1;
        }

        private void drawCenteredString(Graphics2D g2, String text, Rectangle area) {
            FontMetrics fm = g2.getFontMetrics();
            int x = area.x + (area.width - fm.stringWidth(text)) / 2;
            int y = area.y + ((area.height - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(text, x, y);
        }

        private int calcularPasoGridSegundos(int totalSeconds) {
            if (totalSeconds <= 60) return 10;
            if (totalSeconds <= 300) return 60;
            if (totalSeconds <= 600) return 120;
            if (totalSeconds <= 1800) return 300;
            return 600;
        }

        private String formatRangeLabel(int seconds) {
            if (seconds < 60) {
                return "-" + seconds + "s";
            }
            int minutes = seconds / 60;
            if (seconds % 60 == 0) {
                return "-" + minutes + "m";
            }
            return "-" + minutes + "m " + (seconds % 60) + "s";
        }
    }

    private record HoverInfo(int index, ChartSample sample) {
    }
}



