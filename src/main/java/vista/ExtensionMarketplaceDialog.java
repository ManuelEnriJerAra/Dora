package vista;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.formdev.flatlaf.extras.components.FlatButton;

import controlador.GestorServidores;
import controlador.MojangAPI;
import controlador.extensions.ExtensionCatalogDetails;
import controlador.extensions.ExtensionCatalogEntry;
import controlador.extensions.ExtensionCatalogCapability;
import controlador.extensions.ExtensionCatalogProviderDescriptor;
import controlador.extensions.ExtensionCatalogQuery;
import controlador.extensions.ExtensionCatalogVersion;
import controlador.extensions.ExtensionCompatibilityStatus;
import controlador.extensions.ExtensionDependency;
import controlador.extensions.ExtensionDependencyMatcher;
import controlador.extensions.ExtensionDownloadPlan;
import controlador.extensions.ExtensionInstallResolution;
import controlador.extensions.ExtensionInstallResolutionState;
import controlador.extensions.ExtensionSideFilter;
import modelo.Server;
import modelo.extensions.ExtensionLocalMetadata;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

@SuppressWarnings("unused")
final class ExtensionMarketplaceDialog extends JDialog {
    private static final DateTimeFormatter VERSION_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault());
    private static final Logger LOGGER = Logger.getLogger(ExtensionMarketplaceDialog.class.getName());
    private static final int DEFAULT_SEARCH_BATCH_SIZE = 25;
    private static final int MAX_SEARCH_RESULTS = 1000;
    private static final int RELAXED_CLICK_TOLERANCE_PX = 18;
    private static final int RESULT_ACTION_HIT_WIDTH = 72;
    private static final String NO_PROVIDER_ID = "__none__";
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)]+");
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");

    private final GestorServidores gestorServidores;
    private final Server server;
    private final Runnable onInstallCompleted;

    private final DefaultListModel<MarketplaceEntryViewModel> resultsModel = new DefaultListModel<>();
    private final JList<MarketplaceEntryViewModel> resultsList = new JList<>(resultsModel);
    private final DefaultComboBoxModel<ProviderFilterOption> providerModel = new DefaultComboBoxModel<>();
    private final JComboBox<ProviderFilterOption> providerCombo = new JComboBox<>(providerModel);
    private final DefaultComboBoxModel<PlatformFilterOption> loaderModel = new DefaultComboBoxModel<>();
    private final JComboBox<PlatformFilterOption> loaderCombo = new JComboBox<>(loaderModel);
    private final DefaultComboBoxModel<SideFilterOption> sideModel = new DefaultComboBoxModel<>();
    private final JComboBox<SideFilterOption> sideCombo = new JComboBox<>(sideModel);
    private final JTextField searchField = new JTextField();
    private final DefaultComboBoxModel<String> searchVersionModel = new DefaultComboBoxModel<>();
    private final JComboBox<String> searchVersionCombo = new JComboBox<>(searchVersionModel);
    private final DefaultComboBoxModel<SearchSortOption> sortModel = new DefaultComboBoxModel<>();
    private final JComboBox<SearchSortOption> sortCombo = new JComboBox<>(sortModel);
    private final DefaultComboBoxModel<ResultLimitOption> resultLimitModel = new DefaultComboBoxModel<>();
    private final JComboBox<ResultLimitOption> resultLimitCombo = new JComboBox<>(resultLimitModel);
    private final JLabel catalogStatusLabel = new JLabel("Busca una extensión para empezar.");
    private final JLabel queueStatusLabel = new JLabel("La cola está vacía.");
    private final JLabel queueSummaryLabel = new JLabel("");
    private final DefaultListModel<DownloadQueueItem> queueModel = new DefaultListModel<>();
    private final DefaultListModel<DownloadQueueItem> pendingQueueModel = new DefaultListModel<>();
    private final DefaultListModel<DownloadQueueItem> resolvedQueueModel = new DefaultListModel<>();
    private final JList<DownloadQueueItem> pendingQueueList = new JList<>(pendingQueueModel);
    private final JList<DownloadQueueItem> resolvedQueueList = new JList<>(resolvedQueueModel);
    private final JLabel pendingQueueTitleLabel = new JLabel("Por descargar: 0");
    private final JLabel resolvedQueueTitleLabel = new JLabel("Descargadas: 0");
    private final JButton installNowButton = new FlatButton();
    private final JButton queueButton = new FlatButton();
    private final JButton processQueueButton = new FlatButton();
    private final JButton clearFinishedButton = new FlatButton();
    private final JButton queueCollapseButton = new FlatButton();
    private final DefaultComboBoxModel<VersionOption> versionModel = new DefaultComboBoxModel<>();
    private final JComboBox<VersionOption> versionCombo = new JComboBox<>(versionModel);
    private final JLabel catalogLoadingIconLabel = new JLabel();
    private final SvgIconFactory.RotatingIcon catalogLoadingIcon =
            SvgIconFactory.createSpinning("doraicons/hourglass.svg", 18, 18, AppTheme::getForeground);
    private final SvgIconFactory.RotatingIcon queueDownloadingIcon =
            SvgIconFactory.createSpinning("doraicons/loading.svg", 18, 18, () -> Color.WHITE);
    private final javax.swing.Icon queueCompletedIcon =
            SvgIconFactory.create("doraicons/check.svg", 18, 18, () -> Color.WHITE);
    private final Timer catalogLoadingTimer;

    private final JLabel detailTitleLabel = new JLabel("Selecciona una extensión");
    private final JLabel detailSubtitleLabel = new JLabel("-");
    private final JLabel detailStatusBadgeLabel = new ExtensionDetailsLayout.StatusBadgeLabel("Sin selección");
    private final JLabel detailIconLabel = new JLabel();
    private final JLabel detailProviderNameLabel = new JLabel("-");
    private final JLabel detailProviderLabel = new JLabel("-");
    private final JLabel detailTypeLabel = new JLabel("-");
    private final JLabel detailDependenciesLabel = new JLabel("-");
    private final JLabel detailProjectUrlLabel = new JLabel("-");
    private final JLabel detailDownloadsLabel = new JLabel("-");
    private final JLabel detailLicenseLabel = new JLabel("-");
    private final JLabel detailVersionLabel = new JLabel("-");
    private final JLabel detailFileLabel = new JLabel("-");
    private final JTextPane detailDescriptionArea = new JTextPane();
    private final JPanel detailDescriptionContentPanel = new JPanel();

    private final Timer debounceTimer;
    private ExtensionCatalogEntry selectedEntry;
    private MarketplaceEntryViewModel selectedEntryViewModel;
    private ExtensionCatalogDetails selectedDetails;
    private ExtensionDownloadPlan selectedDownloadPlan;
    private ExtensionInstallResolution selectedInstallResolution;
    private String detailBaseDescription = "Selecciona un resultado para revisar su ficha, sus versiones y su estado en este servidor.";
    private MarketplaceCompatibilityAssessment selectedEntryCompatibility;
    private long searchRequestSequence;
    private long detailRequestSequence;
    private long previewRequestSequence;
    private long installedSyncSequence;
    private SearchViewState searchState = new SearchViewState(ViewState.IDLE, "Busca una extensión para empezar.", 0L);
    private DetailViewState detailState = new DetailViewState(ViewState.IDLE, "-", null, 0L);
    private PreviewViewState previewState = new PreviewViewState(ViewState.IDLE, "-", null, 0L);
    private QueueViewState queueState = new QueueViewState(ViewState.EMPTY, "La cola está vacía.");
    private QueueViewState syncState = new QueueViewState(ViewState.IDLE, "");
    private IconViewState iconState = new IconViewState(ViewState.IDLE, "Sin iconos pendientes");
    private String queueFeedbackHeadline;
    private String queueFeedbackMessage;
    private boolean suppressResultSelectionEvents;
    private boolean suppressVersionSelectionEvents;
    private Point resultsListPressPoint;
    private int resultsListPressIndex = -1;
    private Point queueListPressPoint;
    private int queueListPressIndex = -1;
    private JList<DownloadQueueItem> queueListPressSource;
    private MarketplaceSearchSpec lastExecutedSearchSpec;
    private int searchLimit = DEFAULT_SEARCH_BATCH_SIZE;
    private JSplitPane marketplaceVerticalSplit;
    private CardPanel queueCard;
    private JPanel queueBodyPanel;
    private JPanel versionSelectionPanel;
    private JScrollPane detailBodyScrollPane;
    private boolean queueCollapsed;
    private boolean queueProcessingActive;
    private int queueExpandedHeight = 210;
    private double catalogLoadingIconAngle;
    private String detailProjectUrl;
    private long resultDecorationSequence;
    private ResultDecorationRefreshWorker resultDecorationWorker;
    private final List<LinkRange> detailDescriptionLinks = new ArrayList<>();
    private final Map<String, ExtensionInstallResolution> installResolutionCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<ExtensionDownloadPlan>> downloadPlanCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<ExtensionCatalogDetails>> detailsCache = new ConcurrentHashMap<>();
    private final Map<String, MarketplaceCompatibilityAssessment> compatibilityCache = new ConcurrentHashMap<>();
    private final Map<String, String> providerLabelsById = new HashMap<>();

    static void showDialog(Component parent,
                           GestorServidores gestorServidores,
                           Server server,
                           Runnable onInstallCompleted) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        ExtensionMarketplaceDialog dialog = new ExtensionMarketplaceDialog(owner, gestorServidores, server, onInstallCompleted);
        dialog.setVisible(true);
    }

    private ExtensionMarketplaceDialog(Window owner,
                                       GestorServidores gestorServidores,
                                       Server server,
                                       Runnable onInstallCompleted) {
        super(owner, marketplaceTitle(server), Dialog.ModalityType.MODELESS);
        this.gestorServidores = gestorServidores;
        this.server = server;
        this.onInstallCompleted = onInstallCompleted;
        this.catalogStatusLabel.setText("Busca " + extensionPluralLower() + " para empezar.");
        this.searchState = new SearchViewState(ViewState.IDLE, "Busca " + extensionPluralLower() + " para empezar.", 0L);
        this.detailTitleLabel.setText("Selecciona " + articleForCurrentExtension() + " " + extensionSingularLower());
        this.detailBaseDescription = hasExtensionEcosystem()
                ? "Selecciona un resultado para revisar su ficha, sus versiones y su estado en este servidor."
                : "Las extensiones requieren un servidor de mods o plugins. Este servidor no tiene un ecosistema compatible para el marketplace.";
        this.debounceTimer = new Timer(280, e -> runSearch(false));
        this.debounceTimer.setRepeats(false);
        this.catalogLoadingTimer = new Timer(40, e -> rotateCatalogLoadingIcon());

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 760));
        setPreferredSize(new Dimension(1280, 820));
        getContentPane().setBackground(AppTheme.getBackground());
        setLayout(new BorderLayout(10, 10));

        add(buildMarketplaceCard(), BorderLayout.CENTER);

        configureFilters();
        configureResultsList();
        configureQueue();
        configureDetails();

        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(() -> {
            setQueueDefaultDividerLocation();
            runSearch(true);
        });
    }

    private JComponent buildMarketplaceCard() {
        JPanel marketplacePanel = new JPanel(new BorderLayout(0, 10));
        marketplacePanel.setOpaque(false);
        marketplacePanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        marketplacePanel.add(buildCatalogToolbar(), BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildResultsCard(), buildDetailsCard());
        splitPane.setOpaque(false);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setResizeWeight(0.56d);
        splitPane.setDividerLocation(620);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(10);
        marketplaceVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, splitPane, buildFooter());
        marketplaceVerticalSplit.setOpaque(false);
        marketplaceVerticalSplit.setBorder(BorderFactory.createEmptyBorder());
        marketplaceVerticalSplit.setResizeWeight(1d);
        marketplaceVerticalSplit.setContinuousLayout(true);
        marketplaceVerticalSplit.setDividerSize(10);
        marketplacePanel.add(marketplaceVerticalSplit, BorderLayout.CENTER);
        return marketplacePanel;
    }

    private JComponent buildCatalogToolbar() {
        return buildFiltersPanel();
    }

    private JComponent buildFiltersPanel() {
        JPanel toolbar = new JPanel(new BorderLayout(0, 10));
        toolbar.setOpaque(false);

        searchField.setMinimumSize(new Dimension(240, 34));
        searchField.setPreferredSize(new Dimension(360, 34));
        toolbar.add(buildSearchBar(), BorderLayout.NORTH);

        JPanel filters = new JPanel(new GridBagLayout());
        filters.setOpaque(true);
        filters.setBackground(AppTheme.getPanelBackground());
        filters.setBorder(AppTheme.createRoundedBorder(new Insets(8, 8, 8, 8), AppTheme.withAlpha(AppTheme.getBorderColor(), 160), 1f));
        providerCombo.setMinimumSize(new Dimension(170, 38));
        providerCombo.setPreferredSize(new Dimension(180, 34));
        loaderCombo.setMinimumSize(new Dimension(155, 38));
        loaderCombo.setPreferredSize(new Dimension(165, 34));
        sideCombo.setMinimumSize(new Dimension(145, 38));
        sideCombo.setPreferredSize(new Dimension(150, 34));
        sortCombo.setMinimumSize(new Dimension(120, 38));
        sortCombo.setPreferredSize(new Dimension(130, 34));
        resultLimitCombo.setMinimumSize(new Dimension(92, 38));
        resultLimitCombo.setPreferredSize(new Dimension(100, 34));
        styleFilterCombo(providerCombo);
        styleFilterCombo(loaderCombo);
        styleFilterCombo(sideCombo);
        styleFilterCombo(sortCombo);
        styleFilterCombo(resultLimitCombo);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 10);

        gbc.gridx = 0;
        gbc.weightx = 0d;
        filters.add(buildInlineFilter("Proveedor", providerCombo), gbc);

        gbc.gridx = 1;
        filters.add(buildInlineFilter(platformFilterLabel(), loaderCombo), gbc);

        gbc.gridx = 2;
        filters.add(buildInlineFilter("Lado", sideCombo), gbc);

        gbc.gridx = 3;
        filters.add(buildInlineFilter("Orden", sortCombo), gbc);

        gbc.gridx = 4;
        filters.add(buildInlineFilter("Cargar", resultLimitCombo), gbc);

        gbc.gridx = 5;
        gbc.weightx = 1d;
        gbc.insets = new Insets(0, 0, 0, 0);
        filters.add(Box.createHorizontalGlue(), gbc);

        toolbar.add(filters, BorderLayout.CENTER);
        return toolbar;
    }

    private JComponent buildResultsCard() {
        CardPanel card = new CardPanel("Explorar");
        card.setBorder(BorderFactory.createEmptyBorder());
        catalogLoadingIconLabel.setIcon(catalogLoadingIcon);
        catalogLoadingIconLabel.setToolTipText("Actualizando");
        catalogLoadingIconLabel.setVisible(false);
        card.getHeaderActionsPanel().add(catalogLoadingIconLabel);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        card.getContentPanel().add(content, BorderLayout.CENTER);

        catalogStatusLabel.setForeground(AppTheme.getMutedForeground());

        resultsList.setCellRenderer(new SearchResultRenderer());
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsList.setOpaque(false);
        resultsList.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        resultsList.setFixedCellHeight(-1);
        JScrollPane scrollPane = new JScrollPane(resultsList);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(AppTheme.getPanelBackground());
        scrollPane.setBackground(AppTheme.getPanelBackground());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(catalogStatusLabel, BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildDetailsCard() {
        CardPanel card = new CardPanel("Detalles");
        card.setBorder(BorderFactory.createEmptyBorder());

        setDetailIcon(null);
        AppTheme.applyHeaderIconButtonStyle(queueButton);
        queueButton.setPreferredSize(new Dimension(56, 56));
        queueButton.setMinimumSize(new Dimension(56, 56));
        queueButton.setToolTipText("Añadir a la cola");
        queueButton.addActionListener(e -> toggleSelectedQueueState());
        styleDetailStatusBadge();
        setDetailDescriptionText(detailBaseDescription);

        ExtensionDetailsLayout content = new ExtensionDetailsLayout(
                detailIconLabel,
                detailTitleLabel,
                detailSubtitleLabel,
                detailStatusBadgeLabel,
                queueButton,
                detailDescriptionContentPanel,
                null,
                List.of(buildProjectLicensePanel()),
                List.of(buildVersionSelectionPanel()),
                this::repaint
        );
        detailBodyScrollPane = content.bodyScrollPane();
        card.getContentPanel().add(content, BorderLayout.CENTER);

        return card;
    }

    private JPanel buildProjectLicensePanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        ExtensionDetailsLayout.configureFullWidth(panel);
        panel.add(ExtensionDetailsLayout.buildInfoRow("Tipo", detailTypeLabel));
        panel.add(Box.createVerticalStrut(8));
        panel.add(ExtensionDetailsLayout.buildInfoRow("Plataformas compatibles", detailProviderLabel));
        panel.add(Box.createVerticalStrut(8));
        panel.add(ExtensionDetailsLayout.buildInfoRow("Dependencias", detailDependenciesLabel));
        panel.add(Box.createVerticalStrut(8));
        panel.add(ExtensionDetailsLayout.buildInfoRowWithDivider("Proveedor", detailProviderNameLabel, detailDownloadsLabel));
        panel.add(Box.createVerticalStrut(8));
        panel.add(ExtensionDetailsLayout.buildInfoRowWithDivider("Proyecto", detailProjectUrlLabel, detailLicenseLabel));
        return panel;
    }

    private void setDetailIcon(String iconUrl) {
        detailIconLabel.setIcon(ExtensionIconLoader.getIcon(iconUrl, 52, () -> {
            SwingUtilities.invokeLater(() -> {
                if (!isDisplayable()) {
                    return;
                }
                detailIconLabel.setIcon(ExtensionIconLoader.getIcon(iconUrl, 52, this::repaint));
                detailIconLabel.repaint();
            });
        }));
    }

    private JPanel buildVersionSelectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        versionSelectionPanel = panel;

        JLabel title = new JLabel("Versiones disponibles");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 225));
        panel.add(title, BorderLayout.NORTH);

        versionCombo.setPreferredSize(new Dimension(0, 38));
        panel.add(versionCombo, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildDescriptionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JLabel descriptionTitle = new JLabel("Resumen y estado");
        descriptionTitle.setFont(descriptionTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(descriptionTitle, BorderLayout.NORTH);
        panel.add(detailDescriptionArea, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildFooter() {
        queueCard = new CardPanel("Cola");
        queueCard.setBorder(BorderFactory.createEmptyBorder());
        queueCollapseButton.setToolTipText("Minimizar cola");
        AppTheme.applyHeaderIconButtonStyle(queueCollapseButton);
        queueCollapseButton.setIcon(SvgIconFactory.create("doraicons/arrow-down.svg", 20, 20, AppTheme::getForeground));
        queueCollapseButton.addActionListener(e -> toggleQueueCollapsed());
        queueCard.getHeaderActionsPanel().add(queueCollapseButton);

        queueBodyPanel = new JPanel(new BorderLayout(0, 8));
        queueBodyPanel.setOpaque(false);
        queueCard.getContentPanel().add(queueBodyPanel, BorderLayout.CENTER);

        queueSummaryLabel.setFont(queueSummaryLabel.getFont().deriveFont(Font.BOLD, 13.5f));
        queueSummaryLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 220));
        queueStatusLabel.setForeground(AppTheme.getMutedForeground());
        queueStatusLabel.setFont(queueStatusLabel.getFont().deriveFont(12.5f));
        catalogStatusLabel.setFont(catalogStatusLabel.getFont().deriveFont(12.75f));
        queueStatusLabel.setVerticalAlignment(SwingConstants.TOP);

        pendingQueueList.setCellRenderer(new QueueItemRenderer());
        resolvedQueueList.setCellRenderer(new QueueItemRenderer());
        pendingQueueList.setVisibleRowCount(4);
        resolvedQueueList.setVisibleRowCount(4);
        pendingQueueList.setOpaque(false);
        resolvedQueueList.setOpaque(false);
        pendingQueueList.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        resolvedQueueList.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JSplitPane queueSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                buildQueueColumn("Por descargar", pendingQueueList),
                buildQueueColumn("Descargadas", resolvedQueueList)
        );
        queueSplit.setOpaque(false);
        queueSplit.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 0, AppTheme.getBorderColor()));
        queueSplit.setDividerSize(1);
        queueSplit.setResizeWeight(0.5d);
        queueSplit.setContinuousLayout(true);
        queueSplit.setPreferredSize(new Dimension(0, 130));
        queueBodyPanel.add(queueSplit, BorderLayout.CENTER);

        processQueueButton.setText("Procesar cola");
        AppTheme.applyAccentButtonStyle(processQueueButton);
        processQueueButton.setEnabled(false);
        processQueueButton.addActionListener(e -> processQueue());

        clearFinishedButton.setText("Limpiar historial");
        AppTheme.applyActionButtonStyle(clearFinishedButton);
        clearFinishedButton.setEnabled(false);
        clearFinishedButton.addActionListener(e -> clearFinishedQueueItems());

        queueCard.getActionsPanel().add(clearFinishedButton);
        queueCard.getActionsPanel().add(processQueueButton);
        return queueCard;
    }

    private JComponent buildQueueColumn(String title, JList<DownloadQueueItem> list) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        JLabel label = title.startsWith("Por descargar") ? pendingQueueTitleLabel : resolvedQueueTitleLabel;
        label.setText(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12.5f));
        label.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 190));
        panel.add(label, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(AppTheme.getPanelBackground());
        scrollPane.setBackground(AppTheme.getPanelBackground());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void toggleQueueCollapsed() {
        queueCollapsed = !queueCollapsed;
        updateQueueCollapsedState();
    }

    private void updateQueueCollapsedState() {
        if (queueBodyPanel != null) {
            queueBodyPanel.setVisible(!queueCollapsed);
        }
        if (queueCard != null) {
            queueCard.getActionsPanel().setVisible(!queueCollapsed);
        }
        queueCollapseButton.setToolTipText(queueCollapsed ? "Expandir cola" : "Minimizar cola");
        queueCollapseButton.setIcon(SvgIconFactory.create(
                queueCollapsed ? "doraicons/arrow-up.svg" : "doraicons/arrow-down.svg",
                20,
                20,
                AppTheme::getForeground
        ));
        if (marketplaceVerticalSplit != null) {
            if (!queueCollapsed) {
                int height = Math.max(1, marketplaceVerticalSplit.getHeight());
                marketplaceVerticalSplit.setDividerLocation(Math.max(360, height - queueExpandedHeight));
            } else {
                queueExpandedHeight = Math.max(160, marketplaceVerticalSplit.getBottomComponent().getHeight());
                marketplaceVerticalSplit.setDividerLocation(Math.max(360, marketplaceVerticalSplit.getHeight() - 48));
            }
        }
        revalidate();
        repaint();
    }

    private void setQueueDefaultDividerLocation() {
        if (marketplaceVerticalSplit == null || queueCollapsed) {
            return;
        }
        int height = marketplaceVerticalSplit.getHeight();
        if (height <= 0) {
            marketplaceVerticalSplit.setDividerLocation(0.8d);
            return;
        }
        queueExpandedHeight = Math.max(160, height / 5);
        marketplaceVerticalSplit.setDividerLocation(Math.max(360, height - queueExpandedHeight));
    }

    private void configureFilters() {
        loadProviderOptions();
        loadPlatformOptions();
        loadSideOptions();
        loadSortOptions();
        loadResultLimitOptions();

        searchField.putClientProperty("JTextField.placeholderText", searchPlaceholderText());
        searchField.setToolTipText("Busca " + extensionPluralLower() + " por nombre, autor o descripción.");
        providerCombo.setToolTipText("Filtra por proveedor compatible con este tipo de servidor.");
        loaderCombo.setToolTipText("Filtra por " + platformFilterLabel().toLowerCase(Locale.ROOT) + " compatible.");
        sideCombo.setToolTipText("Filtra paquetes por lado de ejecución.");
        sortCombo.setToolTipText("Ordena resultados según el proveedor.");
        resultLimitCombo.setToolTipText("Cantidad de resultados cargados por tanda.");
        searchField.setFont(searchField.getFont().deriveFont(13.25f));
        providerCombo.setFont(providerCombo.getFont().deriveFont(13f));
        loaderCombo.setFont(loaderCombo.getFont().deriveFont(13f));
        sideCombo.setFont(sideCombo.getFont().deriveFont(13f));
        sortCombo.setFont(sortCombo.getFont().deriveFont(13f));
        resultLimitCombo.setFont(resultLimitCombo.getFont().deriveFont(13f));
        versionCombo.setFont(versionCombo.getFont().deriveFont(13f));
        searchField.addActionListener(e -> runSearch(true));
        providerCombo.addActionListener(e -> {
            searchLimit = selectedResultLimit();
            restartDebounce();
        });
        loaderCombo.addActionListener(e -> {
            searchLimit = selectedResultLimit();
            restartDebounce();
        });
        sideCombo.addActionListener(e -> {
            searchLimit = selectedResultLimit();
            restartDebounce();
        });
        sortCombo.addActionListener(e -> {
            searchLimit = selectedResultLimit();
            restartDebounce();
        });
        resultLimitCombo.addActionListener(e -> {
            int requestedBatchSize = selectedResultLimit();
            int currentSize = resultEntryCount();
            if (currentSize < requestedBatchSize) {
                searchLimit = requestedBatchSize;
                restartDebounce();
            } else {
                searchLimit = Math.min(MAX_SEARCH_RESULTS, Math.max(searchLimit, currentSize));
            }
        });

        searchField.getDocument().addDocumentListener(SimpleDocumentListener.of(() -> {
            searchLimit = selectedResultLimit();
            restartDebounce();
        }));
    }

    private void configureSearchVersionCombo(String serverVersion) {
        searchVersionCombo.setEditable(true);
        searchVersionModel.removeAllElements();
        String preferred = serverVersion == null ? "" : serverVersion.trim();
        if (!preferred.isBlank()) {
            searchVersionModel.addElement(preferred);
        }
        for (String version : List.of("1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.1", "1.20.6", "1.20.4", "1.20.1", "1.19.4", "1.19.2", "1.18.2", "1.16.5", "1.12.2")) {
            if (!version.equals(preferred)) {
                searchVersionModel.addElement(version);
            }
        }
        searchVersionCombo.setSelectedItem(preferred);
        loadMinecraftVersionsAsync(preferred);
    }

    private void loadSortOptions() {
        sortModel.removeAllElements();
        sortModel.addElement(new SearchSortOption("Descargas", "downloads", false));
        sortModel.addElement(new SearchSortOption("Relevancia", "relevance", false));
        sortModel.addElement(new SearchSortOption("Actualizados", "updated", false));
        sortCombo.setSelectedIndex(0);
    }

    private void loadResultLimitOptions() {
        resultLimitModel.removeAllElements();
        for (int limit : List.of(25, 50, 100, 250)) {
            resultLimitModel.addElement(new ResultLimitOption(limit));
        }
        resultLimitCombo.setSelectedIndex(0);
        searchLimit = selectedResultLimit();
    }

    private int selectedResultLimit() {
        Object selected = resultLimitCombo.getSelectedItem();
        return selected instanceof ResultLimitOption option ? option.limit() : DEFAULT_SEARCH_BATCH_SIZE;
    }

    private void loadMinecraftVersionsAsync(String preferredVersion) {
        new MinecraftVersionsWorker(preferredVersion).execute();
    }

    private boolean comboContains(DefaultComboBoxModel<String> model, String value) {
        if (model == null || value == null) {
            return false;
        }
        for (int i = 0; i < model.getSize(); i++) {
            if (value.equals(model.getElementAt(i))) {
                return true;
            }
        }
        return false;
    }

    private void configureResultsList() {
        resultsList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        resultsList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || suppressResultSelectionEvents) {
                return;
            }
            MarketplaceEntryViewModel viewModel = resultsList.getSelectedValue();
            if (viewModel != null && viewModel.loadMoreRow()) {
                loadMoreResults();
                return;
            }
            if (viewModel == null || viewModel.entry() == null) {
                clearDetails();
                return;
            }
            loadDetails(viewModel);
        });
        resultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    clearResultsListPress();
                    return;
                }
                resultsListPressPoint = e.getPoint();
                resultsListPressIndex = resultIndexAtPoint(e.getPoint());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleResultsListRelease(e);
                clearResultsListPress();
            }
        });
        resultsList.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = resultIndexAtPoint(e.getPoint());
                if (resultsListHoverIndex != index) {
                    int previous = resultsListHoverIndex;
                    resultsListHoverIndex = index;
                    repaintListRows(resultsList, previous, index);
                }
                if (index < 0) {
                    resultsList.setToolTipText(null);
                    return;
                }
                MarketplaceEntryViewModel viewModel = resultsModel.get(index);
                if (viewModel == null) {
                    resultsList.setToolTipText(null);
                } else if (viewModel.loadMoreRow()) {
                    resultsList.setToolTipText("Cargar más");
                } else if (viewModel.entry() != null) {
                    ExtensionCatalogEntry entry = viewModel.entry();
                    resultsList.setToolTipText("<html><b>" + escapeHtml(defaultString(entry.displayName(), "Extensión")) + "</b><br>"
                            + escapeHtml(defaultString(entry.author(), "Autor desconocido"))
                            + " · " + escapeHtml(formatDownloads(entry.downloads(), entry.providerId()))
                            + "<br>" + escapeHtml(defaultString(viewModel.descriptionPreview(), "Sin descripción.")) + "</html>");
                }
            }
        });
        resultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                int previous = resultsListHoverIndex;
                resultsListHoverIndex = -1;
                repaintListRows(resultsList, previous, -1);
            }
        });
    }

    private void handleResultsListRelease(MouseEvent event) {
        if (!SwingUtilities.isLeftMouseButton(event)) {
            return;
        }
        int index = resultIndexAtPoint(event.getPoint());
        if (index < 0 && resultsListPressIndex >= 0 && isRelaxedClick(resultsListPressPoint, event.getPoint())) {
            index = resultsListPressIndex;
        }
        if (index < 0 || index >= resultsModel.size()) {
            return;
        }
        if (resultsListPressIndex >= 0
                && resultsListPressIndex != index
                && !isRelaxedClick(resultsListPressPoint, event.getPoint())) {
            return;
        }
        MarketplaceEntryViewModel viewModel = resultsModel.get(index);
        if (viewModel == null) {
            return;
        }
        if (viewModel.loadMoreRow()) {
            loadMoreResults();
            return;
        }
        if (viewModel.entry() == null
                || !isRelaxedClick(resultsListPressPoint, event.getPoint())
                || !isTrailingHitZone(event.getX(), resultsList.getWidth(), RESULT_ACTION_HIT_WIDTH)) {
            return;
        }
        triggerResultRowAction(index, viewModel);
    }

    private void triggerResultRowAction(int index, MarketplaceEntryViewModel viewModel) {
        ExtensionCatalogEntry entry = viewModel == null ? null : viewModel.entry();
        if (entry == null) {
            return;
        }
        if (viewModel.installResolution() != null && viewModel.installResolution().alreadyInstalled()) {
            selectedEntryViewModel = viewModel;
            selectedEntry = entry;
            selectedInstallResolution = viewModel.installResolution();
            uninstallSelectedExtension();
        } else if (isQueued(entry)) {
            removeQueuedEntry(entry);
        } else if (viewModel.compatibilityStatus() != ExtensionCompatibilityStatus.COMPATIBLE) {
            resultsList.setSelectedIndex(index);
            loadDetails(viewModel);
        } else {
            enqueueEntryAsync(entry, null);
        }
    }

    private int resultIndexAtPoint(Point point) {
        return listIndexAtPoint(resultsList, point);
    }

    private static int listIndexAtPoint(JList<?> list, Point point) {
        if (list == null || point == null) {
            return -1;
        }
        int index = list.locationToIndex(point);
        if (index < 0) {
            return -1;
        }
        Rectangle bounds = list.getCellBounds(index, index);
        return bounds != null && bounds.contains(point) ? index : -1;
    }

    private void clearResultsListPress() {
        resultsListPressPoint = null;
        resultsListPressIndex = -1;
    }

    static boolean isRelaxedClick(Point pressPoint, Point releasePoint) {
        if (pressPoint == null || releasePoint == null) {
            return false;
        }
        int dx = pressPoint.x - releasePoint.x;
        int dy = pressPoint.y - releasePoint.y;
        return dx * dx + dy * dy <= RELAXED_CLICK_TOLERANCE_PX * RELAXED_CLICK_TOLERANCE_PX;
    }

    static boolean isTrailingHitZone(int x, int componentWidth, int hitWidth) {
        return componentWidth > 0 && hitWidth > 0 && x >= componentWidth - hitWidth && x <= componentWidth;
    }

    private void repaintListRows(JList<?> list, int firstIndex, int secondIndex) {
        if (list == null) {
            return;
        }
        Rectangle repaintBounds = null;
        if (firstIndex >= 0 && firstIndex < list.getModel().getSize()) {
            repaintBounds = list.getCellBounds(firstIndex, firstIndex);
        }
        if (secondIndex >= 0 && secondIndex < list.getModel().getSize()) {
            Rectangle secondBounds = list.getCellBounds(secondIndex, secondIndex);
            repaintBounds = repaintBounds == null ? secondBounds : repaintBounds.union(secondBounds);
        }
        if (repaintBounds != null) {
            list.repaint(repaintBounds);
        }
    }

    private void configureQueue() {
        pendingQueueList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resolvedQueueList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pendingQueueList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        resolvedQueueList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pendingQueueList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                rememberQueueListPress(pendingQueueList, e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleQueueListRelease(pendingQueueList, e);
                clearQueueListPress();
            }
        });
        resolvedQueueList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                rememberQueueListPress(resolvedQueueList, e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleQueueListRelease(resolvedQueueList, e);
                clearQueueListPress();
            }
        });
    }

    private void configureDetails() {
        versionCombo.setRenderer(new VersionRenderer());
        versionCombo.setEnabled(false);
        detailDescriptionContentPanel.setOpaque(false);
        detailDescriptionContentPanel.setLayout(new BoxLayout(detailDescriptionContentPanel, BoxLayout.Y_AXIS));
        ExtensionDetailsLayout.configureFullWidth(detailDescriptionContentPanel);
        ExtensionDetailsLayout.configureDescriptionArea(detailDescriptionArea);
        ExtensionDetailsLayout.configureFullWidth(detailDescriptionArea);
        detailProjectUrlLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        detailProjectUrlLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    openDetailProjectUrl();
                }
            }
        });
        detailDescriptionArea.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                detailDescriptionArea.revalidate();
                detailDescriptionArea.repaint();
            }
        });
        detailDescriptionArea.setCursor(Cursor.getDefaultCursor());
        detailDescriptionArea.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                detailDescriptionArea.setCursor(urlAtDetailPosition(e) == null
                        ? Cursor.getDefaultCursor()
                        : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
        });
        detailDescriptionArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    openUrlAtDetailPosition(e);
                }
            }
        });
        versionCombo.addActionListener(e -> {
            if (suppressVersionSelectionEvents || selectedEntry == null || versionCombo.getSelectedItem() == null) {
                return;
            }
            previewInstallPlan(selectedEntry, ((VersionOption) versionCombo.getSelectedItem()).versionId());
        });
    }

    private void loadProviderOptions() {
        providerModel.removeAllElements();
        providerLabelsById.clear();
        if (!hasExtensionEcosystem()) {
            providerModel.addElement(ProviderFilterOption.provider(null, "No disponible"));
            providerCombo.setSelectedIndex(0);
            return;
        }
        try {
            List<ExtensionCatalogProviderDescriptor> providers = gestorServidores.obtenerRepositoríosExtensiones().stream()
                    .filter(this::providerAllowedForServer)
                    .sorted(Comparator
                            .comparingInt(this::providerDefaultRank)
                            .thenComparing(ExtensionCatalogProviderDescriptor::displayName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            for (ExtensionCatalogProviderDescriptor provider : providers) {
                providerLabelsById.put(normalized(provider.providerId()), provider.displayName());
                providerModel.addElement(ProviderFilterOption.provider(provider.providerId(), provider.displayName()));
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.FINE, "No se ha podido cargar la lista de proveedores del marketplace.", ex);
        }
        if (providerModel.getSize() == 0) {
            providerModel.addElement(ProviderFilterOption.provider(null, "No disponible"));
        }
        providerCombo.setSelectedIndex(0);
    }

    private int providerDefaultRank(ExtensionCatalogProviderDescriptor provider) {
        String providerId = provider == null ? null : normalized(provider.providerId());
        if ("hangar".equals(providerId)) {
            return 0;
        }
        return 1;
    }

    private void loadPlatformOptions() {
        loaderModel.removeAllElements();
        ServerPlatform serverPlatform = server == null || server.getPlatform() == null ? ServerPlatform.UNKNOWN : server.getPlatform();
        if (!hasExtensionEcosystem()) {
            loaderModel.addElement(new PlatformFilterOption("No disponible", ServerPlatform.UNKNOWN, true));
            loaderCombo.setSelectedIndex(0);
            return;
        }
        loaderModel.addElement(new PlatformFilterOption("Según servidor (" + labelForPlatform(serverPlatform) + ")", serverPlatform, true));
        loaderModel.addElement(new PlatformFilterOption("Cualquiera", ServerPlatform.UNKNOWN, false));

        List<ServerPlatform> options = switch (serverEcosystem()) {
            case PLUGINS -> List.of(ServerPlatform.PAPER, ServerPlatform.SPIGOT, ServerPlatform.BUKKIT, ServerPlatform.PURPUR, ServerPlatform.PUFFERFISH);
            case MODS -> List.of(ServerPlatform.FORGE, ServerPlatform.NEOFORGE, ServerPlatform.FABRIC, ServerPlatform.QUILT);
            default -> List.of();
        };

        for (ServerPlatform option : options) {
            if (option == serverPlatform) {
                continue;
            }
            loaderModel.addElement(new PlatformFilterOption(labelForPlatform(option), option, false));
        }
        loaderCombo.setSelectedIndex(0);
    }

    private void loadSideOptions() {
        sideModel.removeAllElements();
        sideModel.addElement(SideFilterOption.any());
        sideModel.addElement(new SideFilterOption("Cliente", SideFilterKind.CLIENT));
        sideModel.addElement(new SideFilterOption("Cliente + servidor", SideFilterKind.BOTH));
        sideModel.addElement(new SideFilterOption("Servidor", SideFilterKind.SERVER));
        sideCombo.setSelectedIndex(0);
    }

    private void runSearch(boolean force) {
        long requestId = ++searchRequestSequence;
        if (!hasExtensionEcosystem()) {
            searchState = new SearchViewState(
                    ViewState.EMPTY,
                    "Las extensiones requieren un servidor de mods o plugins. Convierte o crea un servidor compatible para usar el marketplace.",
                    requestId
            );
            replaceResultsModel(List.of());
            clearDetails();
            updateFilterState();
            updateCatalogStatusLabel();
            return;
        }
        MarketplaceSearchSpec searchSpec = buildSearchSpec();
        if (!force
                && searchSpec.equals(lastExecutedSearchSpec)
                && (searchState.state() == ViewState.READY || searchState.state() == ViewState.EMPTY)) {
            return;
        }
        searchState = new SearchViewState(ViewState.LOADING, "Buscando " + extensionPluralLower() + "...", requestId);
        iconState = new IconViewState(ViewState.IDLE, "Sin iconos pendientes");
        clearDetails();
        updateFilterState();
        updateCatalogStatusLabel();

        List<MarketplaceEntryViewModel> previousResults = new ArrayList<>();
        for (int i = 0; i < resultsModel.size(); i++) {
            MarketplaceEntryViewModel viewModel = resultsModel.getElementAt(i);
            if (viewModel != null && !viewModel.loadMoreRow()) {
                previousResults.add(viewModel);
            }
        }
        String previousSelectionKey = currentSelectedResultKey();
        Map<String, String> queueStateSnapshot = captureQueueStateSnapshot();

        new MarketplaceSearchWorker(requestId, searchSpec, previousResults, previousSelectionKey, queueStateSnapshot).execute();
    }

    private void loadMoreResults() {
        if (searchState.state() == ViewState.LOADING || resultEntryCount() < searchLimit) {
            return;
        }
        if (searchLimit >= MAX_SEARCH_RESULTS) {
            return;
        }
        searchLimit = Math.min(MAX_SEARCH_RESULTS, searchLimit + selectedResultLimit());
        runSearch(true);
    }

    private int resultEntryCount() {
        int count = 0;
        for (int i = 0; i < resultsModel.size(); i++) {
            MarketplaceEntryViewModel viewModel = resultsModel.get(i);
            if (viewModel != null && !viewModel.loadMoreRow()) {
                count++;
            }
        }
        return count;
    }

    private void restoreResults(List<MarketplaceEntryViewModel> entries) {
        replaceResultsModel(entries);
        restoreResultSelection(currentSelectedResultKey(), true);
    }

    private void replaceResultsModel(List<MarketplaceEntryViewModel> entries) {
        suppressResultSelectionEvents = true;
        try {
            resultsModel.clear();
            if (entries == null || entries.isEmpty()) {
                return;
            }
            List<MarketplaceEntryViewModel> modelEntries = new ArrayList<>(entries);
            if (shouldShowLoadMoreRow(entries.size())) {
                modelEntries.add(MarketplaceEntryViewModel.loadMoreRow(catalogStatusLabel.getText()));
            }
            resultsModel.addAll(modelEntries);
        } finally {
            suppressResultSelectionEvents = false;
        }
    }

    private boolean shouldShowLoadMoreRow(int entryCount) {
        return entryCount > 0 && entryCount >= searchLimit && searchLimit < MAX_SEARCH_RESULTS;
    }

    private List<MarketplaceEntryViewModel> applyLocalSort(List<MarketplaceEntryViewModel> entries, SearchSortOption sortOption) {
        if (entries == null || entries.isEmpty() || sortOption == null || !sortOption.localNameSort()) {
            return entries;
        }
        return entries.stream()
                .sorted(Comparator.comparing(viewModel -> defaultString(viewModel.entry().displayName(), ""), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void loadDetails(MarketplaceEntryViewModel viewModel) {
        ExtensionCatalogEntry entry = viewModel == null ? null : viewModel.entry();
        if (entry == null) {
            clearDetails();
            return;
        }
        long requestId = ++detailRequestSequence;
        previewRequestSequence++;
        selectedEntryViewModel = viewModel;
        selectedEntry = entry;
        selectedDetails = null;
        selectedDownloadPlan = null;
        selectedInstallResolution = viewModel.installResolution();
        selectedEntryCompatibility = compatibilityFrom(viewModel);
        versionCombo.setEnabled(false);
        versionModel.removeAllElements();
        detailState = new DetailViewState(ViewState.LOADING, "Cargando detalles y compatibilidad...", entry, requestId);
        previewState = new PreviewViewState(ViewState.IDLE, "-", null, previewRequestSequence);

        detailTitleLabel.setText(entry.displayName());
        setDetailIcon(entry.iconUrl());
        detailSubtitleLabel.setText(buildDetailSubtitle(entry.author(), entry.providerId()));
        detailProviderNameLabel.setText(defaultString(describeProvider(entry.providerId()), entry.providerId()));
        detailProviderLabel.setText(viewModel.platformsSummary());
        detailTypeLabel.setText(packageLabel(entry.extensionType()));
        detailDependenciesLabel.setText("Pendiente de resolver");
        setDetailProjectUrl(entry.projectUrl());
        detailDownloadsLabel.setText(formatDownloads(entry.downloads(), entry.providerId()));
        detailLicenseLabel.setText("Sin licencia declarada");
        detailVersionLabel.setText(entry.version() == null ? "-" : entry.version());
        detailFileLabel.setText("Pendiente de resolver");
        detailBaseDescription = entry.description() == null ? "Sin descripción." : entry.description();
        setDetailDescriptionText(detailBaseDescription);
        refreshSelectionActionState();

        ExtensionCatalogQuery query = buildDetailsQuery(entry);
        new MarketplaceDetailsWorker(entry, query, requestId).execute();
    }

    private void populateDetails(ExtensionCatalogEntry entry, ExtensionCatalogDetails details) {
        if (!sameEntry(selectedEntry, entry)) {
            return;
        }
        detailTitleLabel.setText(entry.displayName());
        setDetailIcon(entry.iconUrl());
        detailSubtitleLabel.setText(buildDetailSubtitle(entry.author(), entry.providerId()));
        detailProviderNameLabel.setText(defaultString(describeProvider(entry.providerId()), entry.providerId()));
        detailProviderLabel.setText(summarizePlatforms(entry));
        ExtensionCatalogEntry detailEntry = details == null ? entry : details.entry();
        detailTypeLabel.setText(packageLabel(detailEntry == null ? entry.extensionType() : detailEntry.extensionType()));
        detailDependenciesLabel.setText(summarizeDependencies(selectedDownloadPlan == null ? null : selectedDownloadPlan.dependencies()));
        setDetailProjectUrl(details == null || details.websiteUrl() == null
                ? entry.projectUrl()
                : details.websiteUrl());
        detailDownloadsLabel.setText(formatDownloads(detailEntry == null ? entry.downloads() : detailEntry.downloads(), entry.providerId()));
        detailLicenseLabel.setText(details == null
                ? "Sin licencia declarada"
                : defaultString(details.licenseName(), "Sin licencia declarada"));
        detailBaseDescription = buildDetailsDescription(entry, details);
        setDetailDescriptionText(detailBaseDescription);
        selectedDownloadPlan = enrichDownloadPlanDescription(entry, selectedDownloadPlan);
        selectedEntryCompatibility = assessEntryCompatibility(detailEntry == null ? entry : detailEntry);
        persistCatalogDetailsOnInstalledExtension(entry, details);

        suppressVersionSelectionEvents = true;
        versionModel.removeAllElements();
        try {
            List<ExtensionCatalogVersion> versions = details == null || details.versions() == null
                    ? List.of()
                    : details.versions().stream()
                    .sorted(Comparator.comparingLong(ExtensionCatalogVersion::publishedAtEpochMillis).reversed())
                    .toList();

            if (versions.isEmpty()) {
                VersionOption fallback = new VersionOption(
                        null,
                        "Sin build compatible",
                        "El proveedor no devuelve versiones para este servidor",
                        false
                );
                versionModel.addElement(fallback);
                versionCombo.setEnabled(false);
                versionCombo.setSelectedIndex(0);
                setVersionSelectionVisible(true);
                selectedDownloadPlan = null;
                selectedInstallResolution = evaluateInstallResolution(entry);
                previewState = new PreviewViewState(ViewState.BLOCKED, "No hay build compatible para este servidor.", null, previewRequestSequence);
                detailVersionLabel.setText("-");
                detailFileLabel.setText("-");
                detailDependenciesLabel.setText("Sin dependencias resueltas");
                refreshSelectionActionState();
                return;
            }

            for (ExtensionCatalogVersion version : versions) {
                MarketplaceCompatibilityAssessment versionCompatibility = assessVersionCompatibility(version);
                versionModel.addElement(new VersionOption(
                        version.versionId(),
                        defaultString(version.versionNumber(), defaultString(version.displayName(), "Versión")),
                        describeVersionMeta(version),
                        versionCompatibility.status() == ExtensionCompatibilityStatus.COMPATIBLE
                ));
            }
            versionCombo.setEnabled(true);
            versionCombo.setSelectedIndex(0);
            setVersionSelectionVisible(true);
            detailSubtitleLabel.setText(buildDetailSubtitle(entry.author(), entry.providerId()));
            previewInstallPlan(entry, ((VersionOption) versionCombo.getSelectedItem()).versionId());
        } finally {
            suppressVersionSelectionEvents = false;
        }
    }

    private void setVersionSelectionVisible(boolean visible) {
        if (versionSelectionPanel != null) {
            versionSelectionPanel.setVisible(visible);
        }
    }

    private void previewInstallPlan(ExtensionCatalogEntry entry, String versionId) {
        long requestId = ++previewRequestSequence;
        previewState = new PreviewViewState(ViewState.LOADING, "Resolviendo descarga compatible...", versionId, requestId);
        selectedDownloadPlan = null;
        refreshSelectionActionState();
        new PreviewInstallPlanWorker(entry, versionId, requestId).execute();
    }

    private void enqueueSelection() {
        if (selectedEntry == null || selectedDownloadPlan == null || !selectedDownloadPlan.ready()) {
            return;
        }
        enqueueEntryWithPlanAsync(selectedEntry, selectedDownloadPlan, false, null);
    }

    private void toggleSelectedQueueState() {
        if (selectedEntry == null) {
            return;
        }
        if (selectedInstallResolution != null && selectedInstallResolution.alreadyInstalled()) {
            uninstallSelectedExtension();
            return;
        }
        if (isQueued(selectedEntry)) {
            removeQueuedEntry(selectedEntry);
            return;
        }
        if (selectedDownloadPlan != null && selectedDownloadPlan.ready()) {
            enqueueSelection();
            return;
        }
        enqueueEntryAsync(selectedEntry, null);
    }

    private boolean isQueued(ExtensionCatalogEntry entry) {
        if (entry == null) {
            return false;
        }
        for (int i = 0; i < queueModel.size(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if ((item.state == QueueState.RESOLVING || item.state == QueueState.PENDING || item.state == QueueState.DOWNLOADING)
                    && item.matchesProject(entry.providerId(), entry.projectId())) {
                return true;
            }
        }
        return false;
    }

    private void removeQueuedEntry(ExtensionCatalogEntry entry) {
        if (entry == null) {
            return;
        }
        for (int i = queueModel.size() - 1; i >= 0; i--) {
            DownloadQueueItem item = queueModel.get(i);
            if ((item.state == QueueState.RESOLVING || item.state == QueueState.PENDING || item.state == QueueState.FAILED)
                    && item.matchesProject(entry.providerId(), entry.projectId())) {
                queueModel.remove(i);
            }
        }
        refreshQueueControls();
        refreshQueuedResultDecorations();
        refreshSelectionActionState();
    }

    private void rememberQueueListPress(JList<DownloadQueueItem> list, MouseEvent event) {
        if (!SwingUtilities.isLeftMouseButton(event)) {
            clearQueueListPress();
            return;
        }
        queueListPressSource = list;
        queueListPressPoint = event.getPoint();
        queueListPressIndex = listIndexAtPoint(list, event.getPoint());
    }

    private void handleQueueListRelease(JList<DownloadQueueItem> list, MouseEvent event) {
        if (!SwingUtilities.isLeftMouseButton(event)) {
            return;
        }
        int index = listIndexAtPoint(list, event.getPoint());
        if (index < 0
                && queueListPressSource == list
                && queueListPressIndex >= 0
                && isRelaxedClick(queueListPressPoint, event.getPoint())) {
            index = queueListPressIndex;
        }
        if (index < 0) {
            return;
        }
        if (queueListPressSource == list
                && queueListPressIndex >= 0
                && queueListPressIndex != index
                && !isRelaxedClick(queueListPressPoint, event.getPoint())) {
            return;
        }
        DownloadQueueItem item = list.getModel().getElementAt(index);
        Rectangle bounds = list.getCellBounds(index, index);
        if (item == null || bounds == null) {
            return;
        }
        int removeZoneStart = bounds.x + bounds.width - 72;
        int removeZoneEnd = bounds.x + bounds.width - 38;
        if (isRelaxedClick(queueListPressPoint, event.getPoint())
                && event.getX() >= removeZoneStart
                && event.getX() <= removeZoneEnd) {
            removeQueueItem(item);
            return;
        }
        showQueueItemDetails(item);
    }

    private void clearQueueListPress() {
        queueListPressSource = null;
        queueListPressPoint = null;
        queueListPressIndex = -1;
    }

    private void removeQueueItem(DownloadQueueItem item) {
        if (item == null) {
            return;
        }
        if (item.state == QueueState.DOWNLOADING) {
            showUserError("No se puede retirar una descarga mientras se está instalando.");
            return;
        }
        queueModel.removeElement(item);
        if (selectedEntry != null && item.matchesProject(selectedEntry.providerId(), selectedEntry.projectId())) {
            refreshSelectionActionState();
        }
        refreshQueueControls();
        refreshQueuedResultDecorations();
        refreshSelectionActionState();
    }

    private void showQueueItemDetails(DownloadQueueItem item) {
        if (item == null) {
            return;
        }
        String key = entryKey(item.providerId, item.projectId);
        if (key != null) {
            for (int i = 0; i < resultsModel.size(); i++) {
                MarketplaceEntryViewModel current = resultsModel.get(i);
                if (current != null && !current.loadMoreRow() && key.equals(current.key())) {
                    resultsList.setSelectedIndex(i);
                    resultsList.ensureIndexIsVisible(i);
                    loadDetails(current);
                    return;
                }
            }
        }
        MarketplaceEntryViewModel fallback = buildQueueBackedViewModel(item);
        loadDetails(fallback);
    }

    private void enqueueEntryAsync(ExtensionCatalogEntry entry, Runnable afterQueued) {
        if (entry == null) {
            return;
        }
        if (sameEntry(selectedEntry, entry) && selectedDownloadPlan != null && selectedDownloadPlan.ready()) {
            enqueueEntryWithPlanAsync(entry, selectedDownloadPlan, false, afterQueued);
            refreshSelectionActionState();
            return;
        }
        DownloadQueueItem preparationItem = beginQueuePreparation(entry, null, false);
        new EnqueuePlanWorker(entry, null, afterQueued, preparationItem).execute();
    }

    private void enqueueEntryWithPlanAsync(ExtensionCatalogEntry entry,
                                           ExtensionDownloadPlan plan,
                                           boolean immediate,
                                           Runnable afterQueued) {
        if (entry == null || plan == null || !plan.ready()) {
            return;
        }
        DownloadQueueItem preparationItem = beginQueuePreparation(entry, plan, immediate);
        new DependencyResolutionWorker(entry, plan, immediate, afterQueued, preparationItem).execute();
    }

    private void enqueueEntryWithResolvedDependencies(ExtensionCatalogEntry entry,
                                                      ExtensionDownloadPlan plan,
                                                      DependencyResolutionResult dependencies,
                                                      boolean immediate,
                                                      Runnable afterQueued,
                                                      DownloadQueueItem preparationItem) {
        if (preparationItem != null && !queueContains(preparationItem)) {
            return;
        }
        QueueAdmission admission = evaluateQueueAdmission(entry, plan, preparationItem);
        if (!admission.allowed()) {
            failQueuePreparation(preparationItem, admission.message());
            if (preparationItem == null) {
                showUserError(admission.message());
            }
            return;
        }
        DependencyResolutionResult resolution = dependencies == null
                ? DependencyResolutionResult.empty()
                : dependencies;
        boolean hasUnresolvedRequiredDependencies = !resolution.unresolvedRequired().isEmpty();
        if (hasUnresolvedRequiredDependencies) {
            showUnresolvedRequiredDependencies(entry, resolution);
        }
        DependencyPromptChoice dependencyChoice = confirmAddMissingDependencies(entry, resolution);
        if (dependencyChoice == DependencyPromptChoice.CANCEL) {
            removePreparationItem(preparationItem);
            return;
        }
        int preparationIndex = preparationItem == null ? -1 : queueModel.indexOf(preparationItem);
        int insertIndex = immediate ? 0 : preparationIndex >= 0 ? preparationIndex : queueModel.size();
        boolean includeOptional = dependencyChoice == DependencyPromptChoice.ADD_ALL;
        Set<String> requiredDependencyKeys = new LinkedHashSet<>(resolution.rootRequiredDependencyKeys());
        for (ResolvedDependency dependency : resolution.resolvedDependencies()) {
            if (dependency.optionalBranch() && !includeOptional) {
                continue;
            }
            DownloadQueueItem existingDependency = findQueuedDependency(dependency.dependency());
            if (existingDependency != null) {
                if (existingDependency.state == QueueState.FAILED) {
                    existingDependency.state = QueueState.PENDING;
                    existingDependency.message = dependency.requiredByParent() ? "Dependencia necesaria" : "Dependencia opcional";
                    existingDependency.downloadPlan = dependency.plan();
                }
                existingDependency.requiredDependencyKeys = List.copyOf(dependency.requiredDependencyKeys());
                if (immediate) {
                    moveQueueItemToIndex(existingDependency, insertIndex++);
                }
                continue;
            }
            DownloadQueueItem dependencyItem = createQueueItem(
                    dependency.dependency(),
                    dependency.plan(),
                    dependency.parentKey(),
                    dependency.requiredByParent() ? "Dependencia necesaria" : "Dependencia opcional"
            );
            dependencyItem.requiredDependencyKeys = List.copyOf(dependency.requiredDependencyKeys());
            if (immediate) {
                queueModel.add(Math.min(insertIndex++, queueModel.size()), dependencyItem);
            } else {
                queueModel.addElement(dependencyItem);
            }
        }
        DownloadQueueItem existingOrPreparation = admission.existingItem() != null
                ? admission.existingItem()
                : preparationItem;
        if (existingOrPreparation != null) {
            existingOrPreparation.downloadPlan = plan;
            existingOrPreparation.state = QueueState.PENDING;
            existingOrPreparation.message = hasUnresolvedRequiredDependencies
                    ? "Pendiente con aviso de dependencias"
                    : immediate ? "Instalación inmediata" : "Pendiente de descarga";
            existingOrPreparation.requiredDependencyKeys = List.copyOf(requiredDependencyKeys);
            if (immediate) {
                moveQueueItemToIndex(existingOrPreparation, insertIndex);
            }
            repaintQueueLists();
            setQueueFeedback("La descarga ya estaba registrada.", entry.displayName() + " queda lista para instalarse desde la cola.");
            if (admission.existingItem() != preparationItem) {
                removePreparationItem(preparationItem);
            }
        } else {
            DownloadQueueItem requested = createQueueItem(entry, plan, immediate ? "Instalación inmediata" : "Pendiente de descarga");
            if (hasUnresolvedRequiredDependencies) {
                requested.message = "Pendiente con aviso de dependencias";
            }
            requested.requiredDependencyKeys = List.copyOf(requiredDependencyKeys);
            if (immediate) {
                queueModel.add(Math.min(insertIndex, queueModel.size()), requested);
            } else {
                queueModel.addElement(requested);
            }
            setQueueFeedback(immediate ? "Instalación prioritaria." : "Agregada a la cola.",
                    entry.displayName() + (immediate ? " se ha colocado al frente de la cola." : " espera su turno para instalarse."));
        }
        if (hasUnresolvedRequiredDependencies) {
            setQueueFeedback("Añadida con aviso.",
                    entry.displayName() + " queda lista para descargarse, pero revisa sus dependencias si no arranca.");
        } else {
            setQueueFeedback("Añadida a la cola.", entry.displayName() + " queda lista para descargarse.");
        }
        refreshQueueControls();
        refreshQueuedResultDecorations();
        refreshSelectionActionState();
        if (afterQueued != null) {
            afterQueued.run();
        }
        if (immediate && queueState.state() != ViewState.LOADING) {
            processQueue();
        }
    }

    private List<ExtensionDependency> missingRequiredDependencies(ExtensionDownloadPlan plan) {
        if (plan == null || plan.dependencies() == null || plan.dependencies().isEmpty()) {
            return List.of();
        }
        List<ExtensionDependency> missing = new ArrayList<>();
        for (ExtensionDependency dependency : plan.dependencies()) {
            if (dependency == null || !dependency.required() || dependency.projectId() == null || dependency.projectId().isBlank()) {
                continue;
            }
            if (!isDependencyInstalled(dependency) && !isDependencyQueued(dependency)) {
                missing.add(dependency);
            }
        }
        return missing;
    }

    private DownloadQueueItem beginQueuePreparation(ExtensionCatalogEntry entry,
                                                    ExtensionDownloadPlan plan,
                                                    boolean immediate) {
        if (entry == null) {
            return null;
        }
        DownloadQueueItem existing = findActiveQueueItem(entry.providerId(), entry.projectId());
        if (existing != null) {
            if (existing.state == QueueState.RESOLVING && plan != null && plan.ready()) {
                existing.downloadPlan = plan;
            }
            return existing;
        }
        DownloadQueueItem item = createQueuePreparationItem(entry, plan, immediate);
        if (immediate) {
            queueModel.add(0, item);
        } else {
            queueModel.addElement(item);
        }
        setQueueFeedback("Preparando descarga...", entry.displayName() + " se está resolviendo junto a sus dependencias.");
        refreshQueueControls();
        refreshQueuedResultDecorations();
        refreshSelectionActionState();
        return item;
    }

    private DownloadQueueItem createQueuePreparationItem(ExtensionCatalogEntry entry,
                                                         ExtensionDownloadPlan plan,
                                                         boolean immediate) {
        return new DownloadQueueItem(
                entry.providerId(),
                entry.projectId(),
                plan == null ? null : plan.versionId(),
                plan,
                defaultString(plan == null ? null : plan.iconUrl(), entry.iconUrl()),
                defaultString(plan == null ? null : plan.displayName(), entry.displayName()),
                defaultString(plan == null ? null : plan.versionNumber(), defaultString(entry.version(), "Pendiente")),
                QueueState.RESOLVING,
                immediate ? "Resolviendo para instalación inmediata..." : "Resolviendo dependencias...",
                null,
                List.of()
        );
    }

    private DownloadQueueItem findActiveQueueItem(String providerId, String projectId) {
        for (int i = 0; i < queueModel.size(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if (item.matchesProject(providerId, projectId)
                    && (item.state == QueueState.RESOLVING
                    || item.state == QueueState.PENDING
                    || item.state == QueueState.DOWNLOADING)) {
                return item;
            }
        }
        return null;
    }

    private boolean queueContains(DownloadQueueItem item) {
        return item != null && queueModel.indexOf(item) >= 0;
    }

    private void removePreparationItem(DownloadQueueItem item) {
        if (item != null && item.state == QueueState.RESOLVING) {
            queueModel.removeElement(item);
            refreshQueueControls();
            refreshQueuedResultDecorations();
            refreshSelectionActionState();
        }
    }

    private void failQueuePreparation(DownloadQueueItem item, String message) {
        if (item == null || !queueContains(item)) {
            return;
        }
        item.state = QueueState.FAILED;
        item.message = defaultString(message, "No se pudo preparar la descarga.");
        setQueueFeedback("No se pudo preparar.", item.message);
        refreshQueueControls();
        refreshQueuedResultDecorations();
        refreshSelectionActionState();
    }

    private boolean isDependencyInstalled(ExtensionDependency dependency) {
        if (server == null || server.getExtensions() == null || dependency == null) {
            return false;
        }
        for (ServerExtension extension : server.getExtensions()) {
            if (dependencyMatchesInstalledExtension(dependency, extension)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDependencyQueued(ExtensionDependency dependency) {
        if (dependency == null) {
            return false;
        }
        for (int i = 0; i < queueModel.size(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if ((item.state == QueueState.RESOLVING || item.state == QueueState.PENDING || item.state == QueueState.DOWNLOADING || item.state == QueueState.COMPLETED)
                    && dependencyMatchesCandidate(dependency, item.providerId, item.projectId, item.displayName, item.displayName)) {
                return true;
            }
        }
        return false;
    }

    private DependencyPromptChoice confirmAddMissingDependencies(ExtensionCatalogEntry entry, DependencyResolutionResult dependencies) {
        if (dependencies == null || !dependencies.hasPromptableDependencies()) {
            return DependencyPromptChoice.ADD_REQUIRED_ONLY;
        }
        StringBuilder message = new StringBuilder();
        boolean hasRequired = dependencies.hasRequiredPrompts();
        boolean hasOptional = dependencies.hasOptionalPrompts();
        if (hasRequired) {
            message.append("El ").append(packageLabel(entry == null ? null : entry.extensionType()).toLowerCase(Locale.ROOT))
                    .append(" ").append(defaultString(entry.displayName(), "seleccionado"))
                    .append(" necesita otros paquetes para funcionar.\n\n");
            appendDependencyPromptGroup(message, "Necesarias", dependencies.resolvedRequired(), dependencies.unresolvedRequired());
        }
        if (hasOptional) {
            if (message.length() > 0) {
                message.append('\n');
            }
            message.append("También hay complementos opcionales que pueden mejorar la experiencia, pero no son obligatorios.\n\n");
            appendDependencyPromptGroup(message, "Opcionales", dependencies.resolvedOptional(), dependencies.unresolvedOptional());
        }
        Object[] options = {"Añadir", "Cancelar"};
        String title;
        int messageType;
        if (hasRequired && hasOptional) {
            options = new Object[]{"A\u00f1adir todo", "Solo necesarias"};
            title = "Dependencias y complementos opcionales";
            messageType = JOptionPane.WARNING_MESSAGE;
        } else if (hasRequired) {
            options = new Object[]{"A\u00f1adir necesarias"};
            title = "Dependencias necesarias";
            messageType = JOptionPane.WARNING_MESSAGE;
        } else {
            options = new Object[]{"A\u00f1adir tambi\u00e9n", "Omitir opcionales"};
            title = "Complementos opcionales";
            messageType = JOptionPane.INFORMATION_MESSAGE;
        }
        int choice = JOptionPane.showOptionDialog(
                this,
                message.toString(),
                title,
                JOptionPane.DEFAULT_OPTION,
                messageType,
                null,
                options,
                options[0]
        );
        if (hasRequired && hasOptional) {
            return choice == 0 ? DependencyPromptChoice.ADD_ALL
                    : DependencyPromptChoice.ADD_REQUIRED_ONLY;
        }
        if (hasRequired) {
            return DependencyPromptChoice.ADD_REQUIRED_ONLY;
        }
        return choice == 0 ? DependencyPromptChoice.ADD_ALL : DependencyPromptChoice.ADD_REQUIRED_ONLY;
    }

    private void appendDependencyPromptGroup(StringBuilder message,
                                             String label,
                                             List<ResolvedDependency> resolved,
                                             List<DependencyNotice> unresolved) {
        message.append(label).append(":\n");
        for (ResolvedDependency dependency : resolved) {
            message.append("- ").append(dependencyDisplayName(dependency.dependency()))
                    .append(" para ").append(defaultString(dependency.parentDisplayName(), "la extensión seleccionada"))
                    .append(findQueuedDependency(dependency.dependency()) == null
                            ? " (se agregará a la cola)\n"
                            : " (ya está en cola)\n");
        }
        for (DependencyNotice dependency : unresolved) {
            message.append("- ").append(dependencyDisplayName(dependency.dependency()))
                    .append(" para ").append(defaultString(dependency.parentDisplayName(), "la extensión seleccionada"))
                    .append(" (no se pudo resolver automáticamente)\n");
        }
    }

    private void showUnresolvedRequiredDependencies(ExtensionCatalogEntry entry, DependencyResolutionResult dependencies) {
        StringBuilder message = new StringBuilder();
        message.append("No se han podido resolver todas las dependencias obligatorias de ")
                .append(defaultString(entry == null ? null : entry.displayName(), "la extensión seleccionada"))
                .append(".\n\nLa descarga continuará, pero revisa o instala manualmente estos paquetes si la extensión no arranca:\n\n");
        for (DependencyNotice dependency : dependencies.unresolvedRequired()) {
            message.append("- ").append(dependencyDisplayName(dependency.dependency()))
                    .append(" para ").append(defaultString(dependency.parentDisplayName(), "la extensión seleccionada"))
                    .append('\n');
        }
        JOptionPane.showMessageDialog(
                this,
                message.toString(),
                "Dependencias obligatorias no resueltas",
                JOptionPane.WARNING_MESSAGE
        );
    }

    private DependencyResolutionResult resolveDependenciesForPlan(ExtensionDownloadPlan parentPlan) {
        if (parentPlan == null || parentPlan.dependencies() == null || parentPlan.dependencies().isEmpty()) {
            return DependencyResolutionResult.empty();
        }
        DependencyResolutionBuilder builder = new DependencyResolutionBuilder();
        Set<String> rootStack = new LinkedHashSet<>();
        String rootKey = planResolutionKey(parentPlan);
        rootStack.addAll(planResolutionKeys(parentPlan));
        List<String> rootRequiredKeys = resolveDependenciesForPlan(
                parentPlan,
                defaultString(parentPlan.displayName(), parentPlan.projectId()),
                rootKey,
                false,
                rootStack,
                builder
        );
        return builder.build(rootRequiredKeys);
    }

    private List<String> resolveDependenciesForPlan(ExtensionDownloadPlan parentPlan,
                                                    String parentDisplayName,
                                                    String parentKey,
                                                    boolean optionalBranch,
                                                    Set<String> resolvingStack,
                                                    DependencyResolutionBuilder builder) {
        if (parentPlan == null || parentPlan.dependencies() == null || parentPlan.dependencies().isEmpty()) {
            return List.of();
        }
        List<String> requiredKeys = new ArrayList<>();
        List<ExtensionDependency> orderedDependencies = parentPlan.dependencies().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ExtensionDependency::required).reversed())
                .toList();

        for (ExtensionDependency dependency : orderedDependencies) {
            if (dependency == null) {
                continue;
            }
            boolean dependencyOptionalBranch = optionalBranch || !dependency.required();
            DependencyNotice notice = new DependencyNotice(dependency, parentDisplayName, parentKey, dependencyOptionalBranch);
            if (isDependencyInstalled(dependency)) {
                builder.alreadySatisfied.add(notice);
                continue;
            }
            String declaredRecursionKey = dependencyDeclaredResolutionKey(dependency, parentPlan);
            if (declaredRecursionKey != null && resolvingStack.contains(declaredRecursionKey)) {
                continue;
            }
            ResolvedDependency existingResolved = builder.resolved(declaredRecursionKey);
            if (existingResolved != null) {
                builder.addResolved(declaredRecursionKey, new ResolvedDependency(
                        dependency,
                        existingResolved.plan(),
                        parentDisplayName,
                        parentKey,
                        dependency.required(),
                        dependencyOptionalBranch,
                        List.of()
                ));
                String existingQueueKey = dependencyQueueKey(dependency, existingResolved.plan());
                if (dependency.required() && existingQueueKey != null) {
                    requiredKeys.add(existingQueueKey);
                }
                continue;
            }
            DownloadQueueItem queuedDependency = findQueuedDependency(dependency);
            if (queuedDependency != null && queuedDependency.state != QueueState.FAILED) {
                builder.alreadySatisfied.add(notice);
                String queuedKey = queuedDependency.queueKey();
                if (dependency.required() && queuedKey != null) {
                    requiredKeys.add(queuedKey);
                }
                ExtensionDownloadPlan queuedPlan = queuedDependency.downloadPlan == null || !queuedDependency.downloadPlan.ready()
                        ? resolveDependencyDownloadPlan(dependency, parentPlan)
                        : queuedDependency.downloadPlan;
                if (queuedPlan == null || !queuedPlan.ready()) {
                    continue;
                }
                String recursionKey = dependencyResolutionKey(dependency, queuedPlan);
                if (recursionKey != null && resolvingStack.contains(recursionKey)) {
                    continue;
                }
                if (builder.hasResolved(recursionKey)) {
                    builder.addResolved(recursionKey, new ResolvedDependency(
                            dependency,
                            queuedPlan,
                            parentDisplayName,
                            parentKey,
                            dependency.required(),
                            dependencyOptionalBranch,
                            List.of()
                    ));
                    continue;
                }
                Set<String> childStack = new LinkedHashSet<>(resolvingStack);
                childStack.addAll(planResolutionKeys(queuedPlan));
                List<String> childRequiredKeys = resolveDependenciesForPlan(
                        queuedPlan,
                        dependencyDisplayName(dependency, queuedPlan),
                        queuedKey,
                        dependencyOptionalBranch,
                        childStack,
                        builder
                );
                builder.addResolved(recursionKey, new ResolvedDependency(
                        dependency,
                        queuedPlan,
                        parentDisplayName,
                        parentKey,
                        dependency.required(),
                        dependencyOptionalBranch,
                        childRequiredKeys
                ));
                continue;
            }
            ExtensionDownloadPlan dependencyPlan = resolveDependencyDownloadPlan(dependency, parentPlan);
            if (dependencyPlan == null || !dependencyPlan.ready() || dependencyBlockedForInstall(dependencyPlan)) {
                if (dependency.required() && !dependencyOptionalBranch) {
                    builder.unresolvedRequired.add(notice);
                } else {
                    builder.unresolvedOptional.add(notice);
                }
                continue;
            }
            String queueKey = dependencyQueueKey(dependency, dependencyPlan);
            String recursionKey = dependencyResolutionKey(dependency, dependencyPlan);
            if (recursionKey != null && resolvingStack.contains(recursionKey)) {
                continue;
            }
            if (builder.hasResolved(recursionKey)) {
                builder.addResolved(recursionKey, new ResolvedDependency(
                        dependency,
                        dependencyPlan,
                        parentDisplayName,
                        parentKey,
                        dependency.required(),
                        dependencyOptionalBranch,
                        List.of()
                ));
                if (dependency.required() && queueKey != null) {
                    requiredKeys.add(queueKey);
                }
                continue;
            }
            Set<String> childStack = new LinkedHashSet<>(resolvingStack);
            childStack.addAll(planResolutionKeys(dependencyPlan));
            List<String> childRequiredKeys = resolveDependenciesForPlan(
                    dependencyPlan,
                    dependencyDisplayName(dependency, dependencyPlan),
                    queueKey,
                    dependencyOptionalBranch,
                    childStack,
                    builder
            );
            builder.addResolved(recursionKey, new ResolvedDependency(
                    dependency,
                    dependencyPlan,
                    parentDisplayName,
                    parentKey,
                    dependency.required(),
                    dependencyOptionalBranch,
                    childRequiredKeys
            ));
            if (dependency.required() && queueKey != null) {
                requiredKeys.add(queueKey);
            }
        }
        return requiredKeys.stream().filter(Objects::nonNull).distinct().toList();
    }

    private boolean canResolveDependency(ExtensionDependency dependency, ExtensionDownloadPlan parentPlan) {
        return dependency != null
                && ((dependency.projectId() != null && !dependency.projectId().isBlank())
                || (dependency.versionId() != null && !dependency.versionId().isBlank()))
                && defaultString(dependency.providerId(), parentPlan == null ? null : parentPlan.providerId()) != null;
    }

    private ExtensionDownloadPlan resolveDependencyDownloadPlan(ExtensionDependency dependency, ExtensionDownloadPlan parentPlan) {
        if (!canResolveDependency(dependency, parentPlan)) {
            return null;
        }
        String providerId = defaultString(dependency.providerId(), parentPlan.providerId());
        String projectId = defaultString(dependency.projectId(), dependency.displayName());
        try {
            return resolveDownloadPlanCached(providerId, projectId, dependency.versionId());
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "No se pudo resolver la dependencia " + projectId + " desde " + providerId, ex);
            return null;
        }
    }

    private boolean dependencyBlockedForInstall(ExtensionDownloadPlan dependencyPlan) {
        ExtensionInstallResolution resolution = evaluateInstallResolution(dependencyPlan);
        return resolution != null && resolution.blocksInstall() && !resolution.alreadyInstalled();
    }

    private DownloadQueueItem findQueuedDependency(ExtensionDependency dependency) {
        if (dependency == null) {
            return null;
        }
        for (int i = 0; i < queueModel.size(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if ((item.state == QueueState.RESOLVING || item.state == QueueState.PENDING || item.state == QueueState.DOWNLOADING || item.state == QueueState.FAILED)
                    && dependencyMatchesCandidate(dependency, item.providerId, item.projectId, item.displayName, item.displayName)) {
                return item;
            }
        }
        return null;
    }

    private String dependencyQueueKey(ExtensionDependency dependency, ExtensionDownloadPlan plan) {
        String provider = defaultString(plan == null ? null : plan.providerId(), dependency == null ? null : dependency.providerId());
        String project = defaultString(plan == null ? null : plan.projectId(), dependency == null ? null : dependency.projectId());
        return entryKey(provider, project);
    }

    private String dependencyResolutionKey(ExtensionDependency dependency, ExtensionDownloadPlan plan) {
        String planKey = planResolutionKey(plan);
        return defaultString(planKey, dependencyDeclaredResolutionKey(dependency, plan));
    }

    private String dependencyDeclaredResolutionKey(ExtensionDependency dependency, ExtensionDownloadPlan parentPlan) {
        if (dependency == null) {
            return null;
        }
        String provider = defaultString(dependency.providerId(), parentPlan == null ? null : parentPlan.providerId());
        return dependencyResolutionCycleKey(provider, dependency.projectId(), dependency.versionId(), dependency.displayName());
    }

    private String planResolutionKey(ExtensionDownloadPlan plan) {
        if (plan == null) {
            return null;
        }
        return dependencyResolutionCycleKey(plan.providerId(), plan.projectId(), plan.versionId(), plan.displayName());
    }

    private Set<String> planResolutionKeys(ExtensionDownloadPlan plan) {
        if (plan == null) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        addIfNotNull(keys, dependencyResolutionCycleKey(plan.providerId(), plan.projectId(), null, plan.displayName()));
        addIfNotNull(keys, dependencyResolutionCycleKey(plan.providerId(), null, plan.versionId(), null));
        return keys;
    }

    private void addIfNotNull(Set<String> target, String value) {
        if (target != null && value != null) {
            target.add(value);
        }
    }

    static String dependencyResolutionCycleKey(String providerId, String projectIdOrName) {
        return dependencyResolutionCycleKey(providerId, projectIdOrName, null, null);
    }

    static String dependencyResolutionCycleKey(String providerId,
                                               String projectId,
                                               String versionId,
                                               String displayName) {
        String project = normalizeIdentifier(defaultStringStatic(projectId, displayName));
        String provider = normalizeIdentifier(providerId);
        if (project != null) {
            return (provider == null ? "local" : provider) + "::" + project;
        }
        String version = normalizeIdentifier(versionId);
        return version == null ? null : (provider == null ? "local" : provider) + "::version::" + version;
    }

    private String dependencyDisplayName(ExtensionDependency dependency) {
        return dependencyDisplayName(dependency, null);
    }

    private String dependencyDisplayName(ExtensionDependency dependency, ExtensionDownloadPlan plan) {
        return defaultString(
                plan == null ? null : plan.displayName(),
                defaultString(
                        dependency == null ? null : dependency.displayName(),
                        defaultString(dependency == null ? null : dependency.projectId(), "dependencia")
                )
        );
    }

    private void uninstallSelectedExtension() {
        if (selectedInstallResolution == null || selectedInstallResolution.installedExtension() == null) {
            return;
        }
        ServerExtension installed = selectedInstallResolution.installedExtension();
        queueButton.setEnabled(false);
        new UninstallExtensionWorker(installed).execute();
    }

    private void installSelectionNow() {
        if (selectedEntry == null || selectedDownloadPlan == null || !selectedDownloadPlan.ready()) {
            return;
        }
        enqueueEntryWithPlanAsync(selectedEntry, selectedDownloadPlan, true, null);
    }

    private void processQueue() {
        if (queueProcessingActive || queueState.state() == ViewState.LOADING) {
            return;
        }
        List<DownloadQueueItem> pendingItems = pendingQueueItemsSnapshot();
        if (pendingItems.isEmpty()) {
            refreshQueueControls();
            return;
        }
        queueProcessingActive = true;
        new QueueProcessingWorker(pendingItems).execute();
    }

    private void clearFinishedQueueItems() {
        int removed = 0;
        List<DownloadQueueItem> retained = new ArrayList<>();
        for (int i = 0; i < queueModel.size(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if (item.state == QueueState.RESOLVING || item.state == QueueState.PENDING || item.state == QueueState.DOWNLOADING || item.state == QueueState.FAILED) {
                retained.add(item);
            } else {
                removed++;
            }
        }
        queueModel.clear();
        for (DownloadQueueItem item : retained) {
            queueModel.addElement(item);
        }
        if (removed > 0) {
            setQueueFeedback("Cola limpiada.", removed == 1
                    ? "Se ha retirado una descarga resuelta."
                    : "Se han retirado " + removed + " descargas resueltas.");
        }
        refreshQueueControls();
        refreshQueuedResultDecorations();
        refreshSelectionActionState();
    }

    private void refreshInstalledExtensions() {
        if (syncState.state() == ViewState.LOADING) {
            return;
        }
        long requestId = ++installedSyncSequence;
        syncState = new QueueViewState(ViewState.LOADING, "Sincronizando " + extensionPluralLower() + " instalados...");
        clearQueueFeedback();
        queueStatusLabel.setText("Sincronizando " + extensionPluralLower() + " instalados...");
        refreshQueueControls();
        new InstalledSyncWorker(requestId).execute();
    }

    private void refreshQueueControls() {
        updateCatalogLoadingIndicator();
        boolean hasItems = queueModel.getSize() > 0;
        boolean hasPending = nextPendingQueueItem() != null;
        boolean hasResolvedItems = hasResolvedQueueItems();
        processQueueButton.setEnabled(hasExtensionEcosystem()
                && hasPending
                && queueState.state() != ViewState.LOADING
                && syncState.state() != ViewState.LOADING);
        clearFinishedButton.setEnabled(hasResolvedItems);
        rebuildQueueDisplayModels();

        if (syncState.state() == ViewState.LOADING) {
            queueSummaryLabel.setText("Sincronizando biblioteca");
            queueStatusLabel.setText(syncState.message());
            return;
        }
        if (queueState.state() == ViewState.LOADING) {
            queueSummaryLabel.setText("Instalación en curso");
            queueStatusLabel.setText(queueState.message());
            return;
        }
        if (!hasItems) {
            queueState = new QueueViewState(ViewState.EMPTY, "La cola está vacía.");
            clearQueueFeedback();
            queueSummaryLabel.setText("");
            queueStatusLabel.setText("");
            return;
        }
        long completed = 0L;
        long failed = 0L;
        long pending = 0L;
        long resolving = 0L;
        for (int i = 0; i < queueModel.size(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if (item.state == QueueState.COMPLETED) {
                completed++;
            } else if (item.state == QueueState.FAILED) {
                failed++;
            } else if (item.state == QueueState.RESOLVING) {
                resolving++;
            } else if (item.state == QueueState.PENDING) {
                pending++;
            }
        }
        queueState = new QueueViewState(ViewState.READY,
                "Preparando: " + resolving + "  |  Pendientes: " + pending + "  |  Completadas: " + completed + "  |  Fallidas: " + failed);
        queueSummaryLabel.setText(defaultString(queueFeedbackHeadline, buildQueueHeadline(resolving, pending, completed, failed)));
        queueStatusLabel.setText(defaultString(queueFeedbackMessage, buildQueueSecondaryMessage(resolving, pending, completed, failed)));
    }

    private void rebuildQueueDisplayModels() {
        List<DownloadQueueItem> pending = new ArrayList<>();
        List<DownloadQueueItem> resolved = new ArrayList<>();
        for (int i = 0; i < queueModel.size(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if (item.state == QueueState.RESOLVING || item.state == QueueState.PENDING || item.state == QueueState.DOWNLOADING || item.state == QueueState.FAILED) {
                pending.add(item);
            } else {
                resolved.add(item);
            }
        }
        pendingQueueModel.clear();
        resolvedQueueModel.clear();
        pendingQueueModel.addAll(pending);
        resolvedQueueModel.addAll(resolved);
        pendingQueueTitleLabel.setText("Por descargar: " + pendingQueueModel.size());
        resolvedQueueTitleLabel.setText("Descargadas: " + resolvedQueueModel.size());
    }

    private void repaintQueueLists() {
        pendingQueueList.repaint();
        resolvedQueueList.repaint();
    }

    private DownloadQueueItem nextPendingQueueItem() {
        for (int i = 0; i < queueModel.getSize(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if (item.state == QueueState.PENDING) {
                return item;
            }
        }
        return null;
    }

    private List<DownloadQueueItem> pendingQueueItemsSnapshot() {
        List<DownloadQueueItem> pending = new ArrayList<>();
        for (int i = 0; i < queueModel.getSize(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if (item.state == QueueState.PENDING) {
                pending.add(item);
            }
        }
        return pending;
    }

    private boolean hasResolvedQueueItems() {
        for (int i = 0; i < queueModel.getSize(); i++) {
            QueueState state = queueModel.get(i).state;
            if (state == QueueState.COMPLETED) {
                return true;
            }
        }
        return false;
    }

    private boolean hasResolvingQueueItems() {
        for (int i = 0; i < queueModel.getSize(); i++) {
            if (queueModel.get(i).state == QueueState.RESOLVING) {
                return true;
            }
        }
        return false;
    }

    private void setQueueFeedback(String headline, String message) {
        queueFeedbackHeadline = headline;
        queueFeedbackMessage = message;
    }

    private void clearQueueFeedback() {
        queueFeedbackHeadline = null;
        queueFeedbackMessage = null;
    }

    private void notifyInstallCompletedSafely() {
        invalidateInstallResolutionCache();
        if (onInstallCompleted == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                onInstallCompleted.run();
            } catch (RuntimeException ex) {
                LOGGER.log(Level.WARNING, "Fallo al refrescar la vista de extensiones tras completar la instalación.", ex);
            }
        });
    }

    private String buildQueueHeadline(long resolving, long pending, long completed, long failed) {
        if (resolving > 0L) {
            return resolving == 1L ? "Preparando 1 descarga" : "Preparando " + resolving + " descargas";
        }
        if (failed > 0L && pending == 0L) {
            return failed == 1L ? "Hay 1 instalación con incidencia" : "Hay " + failed + " instalaciones con incidencia";
        }
        if (pending > 0L) {
            return pending == 1L ? "1 instalación pendiente" : pending + " instalaciones pendientes";
        }
        if (completed > 0L) {
            return completed == 1L ? "1 instalación completada" : completed + " instalaciones completadas";
        }
        return "Cola actualizada";
    }

    private ExtensionDownloadPlan resolveQueueDownloadPlan(DownloadQueueItem item) throws Exception {
        if (server == null) {
            throw new IllegalStateException("No hay un servidor activo para procesar la cola.");
        }
        if (item == null) {
            throw new IllegalArgumentException("La cola contiene un elemento inválido.");
        }
        if (item.providerId == null || item.providerId.isBlank() || item.projectId == null || item.projectId.isBlank()) {
            throw new IllegalArgumentException("Faltan datos del paquete para resolver la descarga.");
        }
        if (item.downloadPlan != null && item.downloadPlan.ready()) {
            return item.downloadPlan;
        }
        ExtensionDownloadPlan plan = resolveDownloadPlanCached(item.providerId, item.projectId, item.versionId);
        if (plan == null || !plan.ready()) {
            throw new IllegalStateException("No se ha encontrado una descarga compatible para este paquete.");
        }
        item.downloadPlan = plan;
        return plan;
    }

    private String dependencyWarningMessage(DownloadQueueItem item, Set<String> completedKeys, Set<String> failedKeys) {
        if (item == null || item.requiredDependencyKeys == null || item.requiredDependencyKeys.isEmpty()) {
            return null;
        }
        for (String dependencyKey : item.requiredDependencyKeys) {
            if (dependencyKey == null || dependencyKey.isBlank()) {
                continue;
            }
            if (failedKeys != null && failedKeys.contains(dependencyKey)) {
                return "Aviso: una dependencia necesaria falló, pero " + item.displayName + " se intentará instalar de todos modos.";
            }
            if (completedKeys != null && completedKeys.contains(dependencyKey)) {
                continue;
            }
            if (isDependencyKeyInstalled(dependencyKey)) {
                continue;
            }
            return "Aviso: falta una dependencia necesaria, pero " + item.displayName + " se intentará instalar de todos modos.";
        }
        return null;
    }

    private boolean isDependencyKeyInstalled(String dependencyKey) {
        if (dependencyKey == null || server == null || server.getExtensions() == null) {
            return false;
        }
        for (ServerExtension extension : server.getExtensions()) {
            if (extension == null || extension.getSource() == null) {
                continue;
            }
            if (dependencyKey.equals(entryKey(extension.getSource().getProvider(), extension.getSource().getProjectId()))) {
                return true;
            }
        }
        return false;
    }

    private String buildQueueSecondaryMessage(long resolving, long pending, long completed, long failed) {
        if (resolving > 0L) {
            return "Puedes seguir usando el marketplace mientras se resuelven dependencias y complementos.";
        }
        if (failed > 0L && pending > 0L) {
            return "Se mantiene la cola activa. Revisa las instalaciones fallidas cuando termine el resto.";
        }
        if (pending > 0L) {
            return "La cola está lista para instalarse en orden y sin duplicados.";
        }
        if (failed > 0L) {
            return "Puedes reintentar una descarga fallida volviendo a agregar esa extensión desde el marketplace.";
        }
        if (completed > 0L) {
            return "La cola no tiene pendientes. Puedes limpiar las instalaciones ya resueltas cuando quieras.";
        }
        return "Sin actividad reciente en la cola.";
    }

    private String friendlyQueueFailureMessage(String rawMessage) {
        String message = defaultString(rawMessage, "No se pudo completar la descarga o instalación.");
        if (message.contains("ya está instalada") || message.contains("ya está instalada")) {
            return "La extensión ya estaba instalada en el servidor.";
        }
        if (message.contains("No se ha podido resolver una descarga compatible")) {
            return "No se encontró una descarga compatible con este servidor.";
        }
        if (message.contains("URL de descarga válida") || message.contains("URL de descarga válida")) {
            return "El proveedor no ha devuelto una descarga válida.";
        }
        if (message.contains("formato ZIP/JAR") || message.contains("corrupto") || message.contains("no contiene entradas")) {
            return "La descarga recibida no parece un archivo .jar válido.";
        }
        if (message.contains("interrumpida")) {
            return "La descarga se interrumpió antes de completarse.";
        }
        if (message.contains("HTTP") || message.contains("consultar")) {
            return "No se ha podido contactar con el proveedor remoto en este momento.";
        }
        return message;
    }

    private void updateFilterState() {
        boolean enabled = hasExtensionEcosystem();
        providerCombo.setEnabled(enabled);
        loaderCombo.setEnabled(enabled);
        searchVersionCombo.setEnabled(enabled);
        searchField.setEnabled(enabled);
        sideCombo.setEnabled(enabled);
        sortCombo.setEnabled(enabled);
        resultLimitCombo.setEnabled(enabled);
        queueButton.setEnabled(enabled && queueButton.isEnabled());
        processQueueButton.setEnabled(enabled && processQueueButton.isEnabled());
    }

    private void updateCatalogStatusLabel() {
        updateCatalogLoadingIndicator();
        if (searchState.state() == ViewState.READY && resultEntryCount() > 0) {
            catalogStatusLabel.setText(resultEntryCount() + " resultados");
            return;
        }
        if (searchState.state() == ViewState.ERROR && !resultsModel.isEmpty()) {
            catalogStatusLabel.setText(searchState.message() + "  |  Puedes seguir revisando los resultados ya cargados.");
            return;
        }
        catalogStatusLabel.setText(searchState.message());
    }

    private void updateCatalogLoadingIndicator() {
        int pendingIcons = ExtensionIconLoader.pendingLoadCount();
        boolean imageLoading = pendingIcons > 0;
        boolean queueLoading = queueState.state() == ViewState.LOADING || hasResolvingQueueItems();
        boolean loading = searchState.state() == ViewState.LOADING || imageLoading || queueLoading;
        catalogLoadingIconLabel.setVisible(loading);
        catalogLoadingIconLabel.setIcon(catalogLoadingIcon);
        catalogLoadingIconLabel.setToolTipText(imageLoading
                ? pendingIcons + " iconos cargando"
                : "Actualizando");
        if (loading) {
            if (!catalogLoadingTimer.isRunning()) {
                catalogLoadingTimer.start();
            }
        } else {
            catalogLoadingTimer.stop();
            catalogLoadingIconAngle = 0d;
            catalogLoadingIcon.setAngleRadians(0d);
            queueDownloadingIcon.setAngleRadians(0d);
            catalogLoadingIconLabel.repaint();
        }
    }

    private void rotateCatalogLoadingIcon() {
        catalogLoadingIconAngle += Math.toRadians(6);
        catalogLoadingIcon.setAngleRadians(catalogLoadingIconAngle);
        queueDownloadingIcon.setAngleRadians(catalogLoadingIconAngle);
        repaintQueueLists();
        if (ExtensionIconLoader.pendingLoadCount() <= 0
                && searchState.state() != ViewState.LOADING
                && queueState.state() != ViewState.LOADING
                && !hasResolvingQueueItems()) {
            iconState = new IconViewState(ViewState.READY, "Iconos cargados");
            updateCatalogLoadingIndicator();
            updateCatalogStatusLabel();
            return;
        }
        catalogLoadingIconLabel.repaint();
    }

    private void refreshSelectionActionState() {
        ActionAvailability availability = evaluateActionAvailability();
        installNowButton.setEnabled(availability.canInstallNow());
        queueButton.setEnabled(availability.canQueue());
        updatePrimaryActionLabels(availability);
        updateDetailVisualState(availability.detailMessage());
    }

    private void updatePrimaryActionLabels(ActionAvailability availability) {
        ExtensionInstallResolution resolution = selectedInstallResolution;
        if (selectedEntry == null) {
            queueButton.setEnabled(false);
            queueButton.setIcon(SvgIconFactory.create("doraicons/plus.svg", 48, 48, AppTheme::getForeground));
            queueButton.setToolTipText("Añadir a la cola");
            return;
        }
        boolean installed = resolution != null && resolution.alreadyInstalled();
        boolean queued = isQueued(selectedEntry);
        String icon = installed || queued ? "doraicons/trash-unselected.svg" : "doraicons/plus.svg";
        queueButton.setIcon(SvgIconFactory.create(icon, 48, 48, AppTheme::getForeground));
        queueButton.setToolTipText(installed ? "Desinstalar" : queued ? "Quitar de la cola" : "Añadir a la cola");
        queueButton.setEnabled(installed || queued || (availability != null && availability.canQueue()));
    }

    private ActionAvailability evaluateActionAvailability() {
        if (!hasExtensionEcosystem()) {
            return new ActionAvailability(false, false, "Las extensiones requieren un servidor de mods o plugins.");
        }
        if (selectedEntry == null) {
            return new ActionAvailability(false, false, detailState.message());
        }
        if (syncState.state() == ViewState.LOADING) {
            return new ActionAvailability(false, false, "Se está sincronizando la biblioteca instalada del servidor.");
        }
        if (detailState.state() == ViewState.LOADING) {
            return new ActionAvailability(false, false, detailState.message());
        }
        if (previewState.state() == ViewState.LOADING) {
            return new ActionAvailability(false, false, previewState.message());
        }

        MarketplaceCompatibilityAssessment compatibility = selectedEntryCompatibility != null
                ? selectedEntryCompatibility
                : assessEntryCompatibility(selectedEntry);
        if (compatibility.status() == ExtensionCompatibilityStatus.INCOMPATIBLE) {
            return new ActionAvailability(false, false, compatibility.summary());
        }
        ExtensionInstallResolution resolution = selectedInstallResolution;
        if (resolution != null && resolution.blocksInstall()) {
            return new ActionAvailability(false, false, defaultString(resolution.message(), previewState.message()));
        }

        QueueAdmission queueAdmission = evaluateQueueAdmission(selectedEntry, selectedDownloadPlan);
        if (!queueAdmission.allowed()) {
            boolean exactQueued = queueAdmission.existingItem() != null
                    && queueAdmission.existingItem().matchesExactVersion(selectedEntry.providerId(), selectedEntry.projectId(),
                    selectedDownloadPlan == null ? null : selectedDownloadPlan.versionId());
            return new ActionAvailability(false, false, exactQueued
                    ? "Esta versión ya está en cola."
                    : queueAdmission.message());
        }
        if (selectedDownloadPlan == null || !selectedDownloadPlan.ready()) {
            return new ActionAvailability(false, false, defaultString(previewState.message(), compatibility.summary()));
        }
        return new ActionAvailability(true, true, defaultString(previewState.message(), compatibility.summary()));
    }

    private QueueAdmission evaluateQueueAdmission(ExtensionCatalogEntry entry, ExtensionDownloadPlan downloadPlan) {
        return evaluateQueueAdmission(entry, downloadPlan, null);
    }

    private QueueAdmission evaluateQueueAdmission(ExtensionCatalogEntry entry,
                                                  ExtensionDownloadPlan downloadPlan,
                                                  DownloadQueueItem itemToIgnore) {
        if (entry == null) {
            return new QueueAdmission(false, null, "Selecciona una extensión.");
        }
        if (downloadPlan == null || !downloadPlan.ready()) {
            return new QueueAdmission(false, null, "Todavía no hay una descarga compatible preparada.");
        }
        ExtensionInstallResolution resolution = selectedInstallResolution != null
                ? selectedInstallResolution
                : evaluateInstallResolution(downloadPlan);
        if (resolution != null && resolution.blocksInstall()) {
            return new QueueAdmission(false, null, resolution.message());
        }

        DownloadQueueItem exactFailed = null;
        for (int i = 0; i < queueModel.getSize(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if (item == itemToIgnore) {
                continue;
            }
            if (!item.matchesProject(entry.providerId(), entry.projectId())) {
                continue;
            }
            if (item.state == QueueState.FAILED && item.matchesExactVersion(entry.providerId(), entry.projectId(), downloadPlan.versionId())) {
                exactFailed = item;
                continue;
            }
            if (item.state == QueueState.RESOLVING || item.state == QueueState.PENDING || item.state == QueueState.DOWNLOADING) {
                String message = item.matchesExactVersion(entry.providerId(), entry.projectId(), downloadPlan.versionId())
                        ? "Esta versión ya está en cola."
                        : "Ya hay otra versión de esta extensión en cola.";
                return new QueueAdmission(false, item, message);
            }
        }
        return new QueueAdmission(true, exactFailed == null ? itemToIgnore : exactFailed, null);
    }

    private DownloadQueueItem createQueueItem(ExtensionCatalogEntry entry,
                                              ExtensionDownloadPlan downloadPlan,
                                              String message) {
        ExtensionDownloadPlan enrichedPlan = enrichDownloadPlanDescription(entry, downloadPlan);
        return new DownloadQueueItem(
                entry.providerId(),
                entry.projectId(),
                enrichedPlan.versionId(),
                enrichedPlan,
                entry.iconUrl(),
                entry.displayName(),
                defaultString(enrichedPlan.versionNumber(), defaultString(entry.version(), "versión")),
                QueueState.PENDING,
                message,
                null,
                List.of()
        );
    }

    private ExtensionDownloadPlan enrichDownloadPlanDescription(ExtensionCatalogEntry entry, ExtensionDownloadPlan plan) {
        if (entry == null || plan == null) {
            return plan;
        }
        String fullDescription = sameEntry(selectedEntry, entry) && isMeaningfulValue(detailBaseDescription)
                ? detailBaseDescription
                : null;
        if (!isMeaningfulValue(fullDescription)
                || fullDescription.equals(plan.description())
                || isMeaningfulValue(plan.description()) && plan.description().length() >= fullDescription.length()) {
            return plan;
        }
        return new ExtensionDownloadPlan(
                plan.providerId(),
                plan.projectId(),
                plan.versionId(),
                plan.displayName(),
                plan.author(),
                fullDescription,
                plan.versionNumber(),
                plan.iconUrl(),
                plan.fileName(),
                plan.downloadUrl(),
                plan.projectUrl(),
                plan.issuesUrl(),
                plan.websiteUrl(),
                plan.licenseName(),
                plan.downloads(),
                plan.clientSide(),
                plan.serverSide(),
                plan.categories(),
                plan.sourceType(),
                plan.extensionType(),
                plan.platform(),
                plan.minecraftVersionConstraint(),
                plan.ready(),
                plan.message(),
                plan.dependencies()
        );
    }

    private void persistCatalogDetailsOnInstalledExtension(ExtensionCatalogEntry entry, ExtensionCatalogDetails details) {
        if (entry == null || selectedInstallResolution == null || selectedInstallResolution.installedExtension() == null) {
            return;
        }
        ServerExtension installed = selectedInstallResolution.installedExtension();
        boolean changed = false;
        String fullDescription = buildDetailsDescription(entry, details);
        if (isMeaningfulValue(fullDescription)
                && (!isMeaningfulValue(installed.getDescription())
                || installed.getDescription().trim().length() < fullDescription.trim().length())) {
            installed.setDescription(fullDescription.trim());
            changed = true;
        }

        ExtensionSource source = installed.getSource();
        if (source == null) {
            source = new ExtensionSource();
            installed.setSource(source);
            changed = true;
        }
        ExtensionCatalogEntry detailEntry = details == null || details.entry() == null ? entry : details.entry();
        changed |= updateSourceText(source, "provider", entry.providerId());
        changed |= updateSourceText(source, "projectId", entry.projectId());
        changed |= updateSourceText(source, "versionId", entry.versionId());
        changed |= updateSourceText(source, "projectUrl", entry.projectUrl());
        changed |= updateSourceText(source, "websiteUrl", details == null ? null : details.websiteUrl());
        changed |= updateSourceText(source, "issuesUrl", details == null ? null : details.issuesUrl());
        changed |= updateSourceText(source, "licenseName", details == null ? null : details.licenseName());
        changed |= updateSourceText(source, "author", entry.author());
        changed |= updateSourceText(source, "iconUrl", entry.iconUrl());

        ExtensionLocalMetadata metadata = installed.getLocalMetadata();
        if (metadata == null) {
            metadata = new ExtensionLocalMetadata();
            installed.setLocalMetadata(metadata);
            changed = true;
        }
        changed |= updateMetadataText(metadata, "websiteUrl", details == null ? null : details.websiteUrl());
        changed |= updateMetadataText(metadata, "issuesUrl", details == null ? null : details.issuesUrl());
        changed |= updateMetadataText(metadata, "licenseName", details == null ? null : details.licenseName());
        changed |= updateMetadataText(metadata, "clientSide", detailEntry.clientSide());
        changed |= updateMetadataText(metadata, "serverSide", detailEntry.serverSide());
        Long currentDownloadCount = metadata.getDownloadCount();
        if (detailEntry.downloads() > 0L
                && (currentDownloadCount == null || currentDownloadCount.longValue() != detailEntry.downloads())) {
            metadata.setDownloadCount(detailEntry.downloads());
            changed = true;
        }
        if (detailEntry.compatiblePlatforms() != null && !detailEntry.compatiblePlatforms().isEmpty()) {
            List<String> loaders = detailEntry.compatiblePlatforms().stream()
                    .filter(platform -> platform != null && platform != ServerPlatform.UNKNOWN)
                    .map(Enum::name)
                    .distinct()
                    .sorted()
                    .toList();
            if (!loaders.isEmpty() && !loaders.equals(metadata.getSupportedLoaders())) {
                metadata.setSupportedLoaders(new ArrayList<>(loaders));
                changed = true;
            }
        }
        if (detailEntry.compatibleMinecraftVersions() != null && !detailEntry.compatibleMinecraftVersions().isEmpty()) {
            List<String> versions = detailEntry.compatibleMinecraftVersions().stream()
                    .filter(this::isMeaningfulValue)
                    .distinct()
                    .sorted()
                    .toList();
            if (!versions.isEmpty() && !versions.equals(metadata.getSupportedMinecraftVersions())) {
                metadata.setSupportedMinecraftVersions(new ArrayList<>(versions));
                changed = true;
            }
        }
        if (details != null && details.categories() != null && !details.categories().isEmpty()) {
            List<String> categories = details.categories().stream()
                    .filter(this::isMeaningfulValue)
                    .distinct()
                    .sorted()
                    .toList();
            if (!categories.isEmpty() && !categories.equals(metadata.getCategories())) {
                metadata.setCategories(new ArrayList<>(categories));
                changed = true;
            }
        }
        if (changed) {
            gestorServidores.guardarMetadatosExtensionInstalada(server, installed);
        }
    }

    private boolean updateSourceText(ExtensionSource source, String field, String value) {
        if (source == null || !isMeaningfulValue(value)) {
            return false;
        }
        String normalized = value.trim();
        switch (field) {
            case "provider" -> {
                if (sameText(source.getProvider(), normalized)) return false;
                source.setProvider(normalized);
            }
            case "projectId" -> {
                if (sameText(source.getProjectId(), normalized)) return false;
                source.setProjectId(normalized);
            }
            case "versionId" -> {
                if (sameText(source.getVersionId(), normalized)) return false;
                source.setVersionId(normalized);
            }
            case "projectUrl" -> {
                if (sameText(source.getProjectUrl(), normalized)) return false;
                source.setProjectUrl(normalized);
            }
            case "websiteUrl" -> {
                if (sameText(source.getWebsiteUrl(), normalized)) return false;
                source.setWebsiteUrl(normalized);
            }
            case "issuesUrl" -> {
                if (sameText(source.getIssuesUrl(), normalized)) return false;
                source.setIssuesUrl(normalized);
            }
            case "licenseName" -> {
                if (sameText(source.getLicenseName(), normalized)) return false;
                source.setLicenseName(normalized);
            }
            case "author" -> {
                if (sameText(source.getAuthor(), normalized)) return false;
                source.setAuthor(normalized);
            }
            case "iconUrl" -> {
                if (sameText(source.getIconUrl(), normalized)) return false;
                source.setIconUrl(normalized);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean updateMetadataText(ExtensionLocalMetadata metadata, String field, String value) {
        if (metadata == null || !isMeaningfulValue(value)) {
            return false;
        }
        String normalized = value.trim();
        switch (field) {
            case "websiteUrl" -> {
                if (sameText(metadata.getWebsiteUrl(), normalized)) return false;
                metadata.setWebsiteUrl(normalized);
            }
            case "issuesUrl" -> {
                if (sameText(metadata.getIssuesUrl(), normalized)) return false;
                metadata.setIssuesUrl(normalized);
            }
            case "licenseName" -> {
                if (sameText(metadata.getLicenseName(), normalized)) return false;
                metadata.setLicenseName(normalized);
            }
            case "clientSide" -> {
                if (sameText(metadata.getClientSide(), normalized)) return false;
                metadata.setClientSide(normalized);
            }
            case "serverSide" -> {
                if (sameText(metadata.getServerSide(), normalized)) return false;
                metadata.setServerSide(normalized);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean sameText(String current, String candidate) {
        return current != null && candidate != null && current.trim().equals(candidate.trim());
    }

    private DownloadQueueItem createQueueItem(ExtensionDependency dependency,
                                              String message) {
        return createQueueItem(dependency, null, null, message);
    }

    private DownloadQueueItem createQueueItem(ExtensionDependency dependency,
                                              ExtensionDownloadPlan plan,
                                              String dependencyOfKey,
                                              String message) {
        ExtensionDownloadPlan resolvedPlan = plan;
        return new DownloadQueueItem(
                defaultString(resolvedPlan == null ? null : resolvedPlan.providerId(), dependency.providerId()),
                defaultString(resolvedPlan == null ? null : resolvedPlan.projectId(), dependency.projectId()),
                defaultString(resolvedPlan == null ? null : resolvedPlan.versionId(), dependency.versionId()),
                resolvedPlan,
                defaultString(resolvedPlan == null ? null : resolvedPlan.iconUrl(), resolveDependencyIconUrl(dependency)),
                defaultString(resolvedPlan == null ? null : resolvedPlan.displayName(),
                        defaultString(dependency.displayName(), dependency.projectId())),
                defaultString(resolvedPlan == null ? null : resolvedPlan.versionNumber(), defaultString(dependency.versionId(), "versión")),
                QueueState.PENDING,
                message,
                dependencyOfKey,
                List.of()
        );
    }

    private String resolveDependencyIconUrl(ExtensionDependency dependency) {
        if (dependency == null) {
            return null;
        }
        String key = entryKey(dependency.providerId(), dependency.projectId());
        if (key != null) {
            for (int i = 0; i < resultsModel.size(); i++) {
                MarketplaceEntryViewModel current = resultsModel.get(i);
                if (current != null && !current.loadMoreRow() && key.equals(current.key()) && current.entry() != null) {
                    return current.entry().iconUrl();
                }
            }
        }
        if (server != null && server.getExtensions() != null) {
            for (ServerExtension extension : server.getExtensions()) {
                if (extension == null || extension.getSource() == null) {
                    continue;
                }
                if (dependency.projectId() != null
                        && dependency.providerId() != null
                        && dependency.projectId().equalsIgnoreCase(defaultString(extension.getSource().getProjectId(), ""))
                        && dependency.providerId().equalsIgnoreCase(defaultString(extension.getSource().getProvider(), ""))) {
                    return extension.getSource().getIconUrl();
                }
            }
        }
        return null;
    }

    private void moveQueueItemToFront(DownloadQueueItem target) {
        if (target == null) {
            return;
        }
        moveQueueItemToIndex(target, 0);
    }

    private void moveQueueItemToIndex(DownloadQueueItem target, int targetIndex) {
        if (target == null) {
            return;
        }
        int index = queueModel.indexOf(target);
        if (index < 0) {
            return;
        }
        queueModel.remove(index);
        int boundedIndex = Math.max(0, Math.min(targetIndex, queueModel.size()));
        queueModel.add(boundedIndex, target);
    }

    private boolean sameEntry(ExtensionCatalogEntry left, ExtensionCatalogEntry right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return normalized(left.providerId()) != null
                && normalized(left.providerId()).equalsIgnoreCase(defaultString(right.providerId(), ""))
                && normalized(left.projectId()) != null
                && normalized(left.projectId()).equalsIgnoreCase(defaultString(right.projectId(), ""));
    }

    private void showUserError(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        setQueueFeedback("Acción no disponible.", message);
        refreshQueueControls();
        JOptionPane.showMessageDialog(
                this,
                message,
                "Marketplace",
                JOptionPane.WARNING_MESSAGE
        );
    }

    private String describeQueuedState(ExtensionCatalogEntry entry) {
        if (entry == null) {
            return null;
        }
        for (int i = 0; i < queueModel.size(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if (!item.matchesProject(entry.providerId(), entry.projectId())) {
                continue;
            }
            return switch (item.state) {
                case RESOLVING -> "Preparando";
                case PENDING -> "En cola";
                case DOWNLOADING -> "Instalando";
                case FAILED -> "Error en cola";
                case COMPLETED -> "Instalada";
            };
        }
        return null;
    }

    private MarketplaceEntryViewModel buildEntryViewModel(ExtensionCatalogEntry entry) {
        return buildEntryViewModel(entry, captureQueueStateSnapshot());
    }

    private MarketplaceEntryViewModel buildQueueBackedViewModel(DownloadQueueItem item) {
        if (item == null) {
            return null;
        }
        ExtensionCatalogEntry entry = new ExtensionCatalogEntry(
                item.providerId,
                item.projectId,
                item.versionId,
                defaultString(item.displayName, "Extensión"),
                null,
                item.versionLabel,
                item.message,
                null,
                resolveServerExtensionType(),
                Set.of(server == null || server.getPlatform() == null ? ServerPlatform.UNKNOWN : server.getPlatform()),
                Set.of(defaultString(server == null ? null : server.getVersion(), "")),
                item.iconUrl,
                null,
                null,
                0L
        );
        return buildEntryViewModel(entry, captureQueueStateSnapshot());
    }

    private MarketplaceEntryViewModel buildEntryViewModel(ExtensionCatalogEntry entry, Map<String, String> queueStateSnapshot) {
        if (entry == null) {
            return null;
        }
        MarketplaceCompatibilityAssessment compatibility = assessEntryCompatibility(entry);
        ExtensionInstallResolution resolution = evaluateInstallResolution(entry);
        return new MarketplaceEntryViewModel(
                entry,
                compatibility.status(),
                compatibility.summary(),
                compatibility.reasons(),
                resolution,
                queueStateSnapshot == null ? null : queueStateSnapshot.get(entryKey(entry)),
                describeProvider(entry.providerId()),
                summarizePlatforms(entry),
                summarizeVersions(entry),
                trimToLength(normalizeInlineText(defaultString(entry.description(), "Sin descripción.")), 210),
                false
        );
    }

    private MarketplaceCompatibilityAssessment compatibilityFrom(MarketplaceEntryViewModel viewModel) {
        if (viewModel == null) {
            return new MarketplaceCompatibilityAssessment(
                    ExtensionCompatibilityStatus.WARNING,
                    "Compatibilidad sin datos suficientes",
                    List.of("No hay información suficiente del proveedor para evaluar esta extensión.")
            );
        }
        return new MarketplaceCompatibilityAssessment(
                viewModel.compatibilityStatus(),
                defaultString(viewModel.compatibilitySummary(), "Compatibilidad sin validar"),
                viewModel.compatibilityReasons() == null ? List.of() : viewModel.compatibilityReasons()
        );
    }

    private String currentSelectedResultKey() {
        if (selectedEntryViewModel != null) {
            return selectedEntryViewModel.key();
        }
        return entryKey(selectedEntry);
    }

    private void restoreResultSelection(String preferredKey, boolean loadDetailsAfterSelection) {
        if (resultsModel.isEmpty()) {
            return;
        }
        int targetIndex = 0;
        if (preferredKey != null) {
            for (int i = 0; i < resultsModel.size(); i++) {
                MarketplaceEntryViewModel current = resultsModel.get(i);
                if (current != null && !current.loadMoreRow() && preferredKey.equals(current.key())) {
                    targetIndex = i;
                    break;
                }
            }
        } else if (resultsModel.get(targetIndex).loadMoreRow()) {
            return;
        }
        suppressResultSelectionEvents = true;
        try {
            resultsList.setSelectedIndex(targetIndex);
        } finally {
            suppressResultSelectionEvents = false;
        }
        MarketplaceEntryViewModel selected = resultsModel.get(targetIndex);
        if (selected == null || selected.loadMoreRow()) {
            return;
        }
        if (loadDetailsAfterSelection) {
            loadDetails(selected);
            return;
        }
        selectedEntryViewModel = selected;
        selectedEntry = selected.entry();
        selectedEntryCompatibility = compatibilityFrom(selected);
        String selectedKey = entryKey(selectedEntry);
        if (selectedInstallResolution != null && selectedKey != null && selectedKey.equals(selected.key())) {
            selectedInstallResolution = selected.installResolution();
        }
        refreshSelectionActionState();
    }

    private void refreshQueuedResultDecorations() {
        if (resultsModel.isEmpty()) {
            return;
        }
        String selectedKey = currentSelectedResultKey();
        Map<String, String> queueSnapshot = captureQueueStateSnapshot();
        Rectangle changedBounds = null;
        for (int i = 0; i < resultsModel.size(); i++) {
            MarketplaceEntryViewModel current = resultsModel.get(i);
            if (current == null || current.loadMoreRow()) {
                continue;
            }
            String newQueueState = queueSnapshot.get(current.key());
            if ((current.queueStateText() == null && newQueueState == null)
                    || (current.queueStateText() != null && current.queueStateText().equals(newQueueState))) {
                continue;
            }
            MarketplaceEntryViewModel updated = new MarketplaceEntryViewModel(
                    current.entry(),
                    current.compatibilityStatus(),
                    current.compatibilitySummary(),
                    current.compatibilityReasons(),
                    current.installResolution(),
                    newQueueState,
                    current.providerLabel(),
                    current.platformsSummary(),
                    current.versionsSummary(),
                    current.descriptionPreview(),
                    false
            );
            resultsModel.set(i, updated);
            Rectangle rowBounds = resultsList.getCellBounds(i, i);
            if (rowBounds != null) {
                changedBounds = changedBounds == null ? rowBounds : changedBounds.union(rowBounds);
            }
            if (selectedKey != null && selectedKey.equals(updated.key())) {
                selectedEntryViewModel = updated;
            }
        }
        if (changedBounds != null) {
            resultsList.repaint(changedBounds);
        }
    }

    private void refreshResultDecorationsAsync() {
        if (resultsModel.isEmpty()) {
            return;
        }
        List<ExtensionCatalogEntry> entries = new ArrayList<>();
        for (int i = 0; i < resultsModel.size(); i++) {
            MarketplaceEntryViewModel current = resultsModel.get(i);
            if (current != null && !current.loadMoreRow()) {
                entries.add(current.entry());
            }
        }
        String selectedKey = currentSelectedResultKey();
        Map<String, String> queueStateSnapshot = captureQueueStateSnapshot();
        long requestId = ++resultDecorationSequence;
        if (resultDecorationWorker != null && !resultDecorationWorker.isDone()) {
            resultDecorationWorker.cancel(false);
        }
        resultDecorationWorker = new ResultDecorationRefreshWorker(requestId, entries, queueStateSnapshot, selectedKey);
        resultDecorationWorker.execute();
    }

    private Map<String, String> captureQueueStateSnapshot() {
        Map<String, String> snapshot = new HashMap<>();
        for (int i = 0; i < queueModel.size(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            String key = entryKey(item.providerId, item.projectId);
            if (key == null) {
                continue;
            }
            snapshot.put(key, switch (item.state) {
                case RESOLVING -> "Preparando";
                case PENDING -> "En cola";
                case DOWNLOADING -> "Instalando";
                case FAILED -> "Error en cola";
                case COMPLETED -> "Instalada";
            });
        }
        return snapshot;
    }

    private String entryKey(ExtensionCatalogEntry entry) {
        if (entry == null) {
            return null;
        }
        return entryKey(entry.providerId(), entry.projectId());
    }

    private String entryKey(String providerId, String projectId) {
        String provider = normalized(providerId);
        String project = normalized(projectId);
        if (provider == null || project == null) {
            return null;
        }
        return provider.toLowerCase(Locale.ROOT) + "::" + project.toLowerCase(Locale.ROOT);
    }

    private static String queueEntryKey(String providerId, String projectId) {
        if (providerId == null || providerId.isBlank() || projectId == null || projectId.isBlank()) {
            return null;
        }
        return providerId.trim().toLowerCase(Locale.ROOT) + "::" + projectId.trim().toLowerCase(Locale.ROOT);
    }

    private ExtensionInstallResolution evaluateInstallResolution(ExtensionCatalogEntry entry) {
        if (entry == null) {
            return gestorServidores.evaluarInstalacionExterna(server, (ExtensionCatalogEntry) null);
        }
        String key = "entry|" + defaultString(entry.providerId(), "")
                + "|" + defaultString(entry.projectId(), "")
                + "|" + defaultString(entry.versionId(), "")
                + "|" + defaultString(entry.version(), "");
        return installResolutionCache.computeIfAbsent(key, ignored -> gestorServidores.evaluarInstalacionExterna(server, entry));
    }

    private ExtensionInstallResolution evaluateInstallResolution(ExtensionDownloadPlan downloadPlan) {
        if (downloadPlan == null) {
            return gestorServidores.evaluarInstalacionExterna(server, (ExtensionDownloadPlan) null);
        }
        String key = "plan|" + defaultString(downloadPlan.providerId(), "")
                + "|" + defaultString(downloadPlan.projectId(), "")
                + "|" + defaultString(downloadPlan.versionId(), "")
                + "|" + defaultString(downloadPlan.versionNumber(), "")
                + "|" + defaultString(downloadPlan.fileName(), "");
        return installResolutionCache.computeIfAbsent(key, ignored -> gestorServidores.evaluarInstalacionExterna(server, downloadPlan));
    }

    private void invalidateInstallResolutionCache() {
        installResolutionCache.clear();
    }

    private ExtensionDownloadPlan resolveDownloadPlanCached(String providerId, String projectId, String versionId) throws IOException {
        String key = downloadPlanCacheKey(providerId, projectId, versionId);
        Optional<ExtensionDownloadPlan> cached = downloadPlanCache.get(key);
        if (cached != null) {
            return cached.orElse(null);
        }
        ExtensionDownloadPlan plan = gestorServidores.prepararDescargaExtensionExterna(providerId, projectId, versionId, server);
        downloadPlanCache.put(key, Optional.ofNullable(plan));
        return plan;
    }

    private Optional<ExtensionCatalogDetails> resolveDetailsCached(ExtensionCatalogEntry entry, ExtensionCatalogQuery query) throws IOException {
        if (entry == null) {
            return Optional.empty();
        }
        String key = detailsCacheKey(entry, query);
        Optional<ExtensionCatalogDetails> cached = detailsCache.get(key);
        if (cached != null) {
            return cached;
        }
        Optional<ExtensionCatalogDetails> details = Optional.ofNullable(gestorServidores.obtenerDetalleExtensionExterna(
                entry.providerId(),
                entry.projectId(),
                query
        ));
        detailsCache.put(key, details);
        return details;
    }

    private String downloadPlanCacheKey(String providerId, String projectId, String versionId) {
        return defaultString(providerId, "") + "|" + defaultString(projectId, "") + "|" + defaultString(versionId, "")
                + "|" + defaultString(server == null || server.getPlatform() == null ? null : server.getPlatform().name(), "")
                + "|" + defaultString(server == null || server.getLoader() == null ? null : server.getLoader().name(), "")
                + "|" + defaultString(server == null ? null : server.getVersion(), "");
    }

    private String detailsCacheKey(ExtensionCatalogEntry entry, ExtensionCatalogQuery query) {
        return defaultString(entry.providerId(), "") + "|" + defaultString(entry.projectId(), "")
                + "|" + defaultString(query == null ? null : query.minecraftVersion(), "")
                + "|" + defaultString(query == null || query.platform() == null ? null : query.platform().name(), "")
                + "|" + defaultString(query == null || query.extensionType() == null ? null : query.extensionType().name(), "");
    }

    private void clearDetails() {
        detailRequestSequence++;
        previewRequestSequence++;
        selectedEntryViewModel = null;
        selectedEntry = null;
        selectedEntryCompatibility = null;
        selectedDetails = null;
        selectedDownloadPlan = null;
        selectedInstallResolution = null;
        detailState = new DetailViewState(ViewState.IDLE, "-", null, detailRequestSequence);
        previewState = new PreviewViewState(ViewState.IDLE, "-", null, previewRequestSequence);
        detailTitleLabel.setText("Selecciona " + articleForCurrentExtension() + " " + extensionSingularLower());
        detailSubtitleLabel.setText("-");
        setDetailIcon(null);
        detailProviderNameLabel.setText("-");
        detailProviderLabel.setText("-");
        setDetailProjectUrl(null);
        detailDownloadsLabel.setText("-");
        detailLicenseLabel.setText("Sin licencia declarada");
        detailTypeLabel.setText("-");
        detailDependenciesLabel.setText("-");
        detailBaseDescription = hasExtensionEcosystem()
                ? "Selecciona un resultado para ver sus detalles antes de instalar."
                : "Las extensiones requieren un servidor de mods o plugins. Este servidor no tiene un ecosistema compatible para el marketplace.";
        setDetailDescriptionText(detailBaseDescription);
        detailVersionLabel.setText("-");
        detailFileLabel.setText("-");
        versionModel.removeAllElements();
        versionCombo.setEnabled(false);
        setVersionSelectionVisible(false);
        refreshSelectionActionState();
    }

    private JPanel buildField(String title, JComponent component) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(6));
        panel.add(component);
        return panel;
    }

    private JComponent buildSearchBar() {
        JPanel panel = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                setBackground(searchBarBackground());
                super.paintComponent(g);
            }
        };
        panel.setOpaque(true);
        panel.setBackground(searchBarBackground());
        panel.setBorder(AppTheme.createRoundedBorder(new Insets(4, 8, 4, 8), AppTheme.withAlpha(Color.WHITE, 70), 1f));

        searchField.setMinimumSize(new Dimension(240, 30));
        searchField.setPreferredSize(new Dimension(310, 30));
        searchField.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        searchField.setOpaque(false);
        searchField.setForeground(searchBarForeground());
        searchField.setCaretColor(searchBarForeground());
        panel.add(searchField, BorderLayout.CENTER);

        JButton searchButton = new JButton();
        AppTheme.applyHeaderIconButtonStyle(searchButton);
        searchButton.setIcon(SvgIconFactory.create("doraicons/magnifier.svg", 24, 24, this::searchBarForeground));
        searchButton.setToolTipText("Buscar");
        searchButton.setPreferredSize(new Dimension(30, 30));
        searchButton.addActionListener(e -> runSearch(true));
        panel.add(searchButton, BorderLayout.EAST);
        return panel;
    }

    private Color searchBarBackground() {
        return AppTheme.isLightTheme()
                ? AppTheme.darken(AppTheme.getPanelBackground(), 0.16f)
                : AppTheme.tint(AppTheme.getPanelBackground(), Color.WHITE, 0.12f);
    }

    private Color searchBarForeground() {
        return AppTheme.isLightTheme()
                ? AppTheme.withAlpha(AppTheme.getForeground(), 235)
                : Color.WHITE;
    }

    private void styleFilterCombo(JComboBox<?> comboBox) {
        if (comboBox == null) {
            return;
        }
        comboBox.setOpaque(true);
        comboBox.setBackground(AppTheme.getSurfaceBackground());
        comboBox.setForeground(AppTheme.getForeground());
        comboBox.setBorder(AppTheme.createRoundedBorder(new Insets(4, 8, 4, 8), AppTheme.withAlpha(AppTheme.getBorderColor(), 170), 1f));
    }

    private JComponent buildInlineFilter(String title, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder());
        panel.add(createFilterCaption(title), BorderLayout.WEST);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildToggleFilter(String title, JCheckBox checkBox) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder());
        panel.add(createFilterCaption(title));
        panel.add(checkBox);
        return panel;
    }

    private JLabel createFilterCaption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12.5f));
        label.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 185));
        return label;
    }

    private void setWrappingLabelText(JLabel label, String value, int width) {
        if (label == null) {
            return;
        }
        String cleaned = softWrapLongTokens(defaultString(value, "-"));
        label.setText("<html><body style='width:" + Math.max(120, width) + "px;margin:0;padding:0'>"
                + escapeHtml(cleaned).replace("\n", "<br>")
                + "</body></html>");
    }

    private JPanel createDetailMetaCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setOpaque(true);
        card.setBackground(AppTheme.withAlpha(AppTheme.getSurfaceBackground(), 215));
        card.setBorder(AppTheme.createRoundedBorder(new Insets(10, 10, 10, 10), AppTheme.withAlpha(AppTheme.getBorderColor(), 145), 1f));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12.5f));
        titleLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 175));

        valueLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 220));
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.PLAIN, 13.25f));
        valueLabel.setVerticalAlignment(SwingConstants.TOP);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void styleDetailStatusBadge() {
        ExtensionDetailsLayout.configureStatusBadge(detailStatusBadgeLabel);
        detailStatusBadgeLabel.setFont(detailStatusBadgeLabel.getFont().deriveFont(Font.BOLD, 11.75f));
    }

    private void updateDetailVisualState(String message) {
        ViewState state = resolveCurrentDetailVisualState();
        detailStatusBadgeLabel.setText(resolveDetailStatusBadgeText(state, message));

        Color foreground = switch (state) {
            case READY -> AppTheme.getSuccessColor();
            case ERROR -> AppTheme.getDangerColor();
            case BLOCKED -> readableWarningColor();
            case LOADING -> AppTheme.getMainAccent();
            default -> AppTheme.withAlpha(AppTheme.getForeground(), 210);
        };
        Color background = switch (state) {
            case READY -> AppTheme.withAlpha(AppTheme.getSuccessColor(), 34);
            case ERROR -> AppTheme.withAlpha(AppTheme.getDangerColor(), 30);
            case BLOCKED -> AppTheme.withAlpha(readableWarningColor(), 36);
            case LOADING -> AppTheme.withAlpha(AppTheme.getMainAccent(), 24);
            default -> AppTheme.withAlpha(AppTheme.getForeground(), 12);
        };
        ExtensionDetailsLayout.applyStatusBadgeStyle(
                detailStatusBadgeLabel,
                foreground,
                background,
                AppTheme.withAlpha(foreground, 70)
        );

        detailProjectUrlLabel.setText(formatProjectLink(detailProjectUrl));
        detailProjectUrlLabel.setToolTipText(isMeaningfulValue(detailProjectUrl) ? detailProjectUrl : null);
        detailProjectUrlLabel.setForeground(isMeaningfulValue(detailProjectUrl)
                ? AppTheme.getLinkForeground()
                : AppTheme.withAlpha(AppTheme.getForeground(), 160));
        detailDescriptionArea.setForeground(state == ViewState.ERROR
                ? AppTheme.withAlpha(AppTheme.getForeground(), 205)
                : AppTheme.getForeground());
        setDetailDescriptionText(isMeaningfulValue(detailBaseDescription)
                ? detailBaseDescription
                : defaultString(message, "Sin descripción disponible."));
    }

    private String resolveDetailStatusBadgeText(ViewState state, String message) {
        if (selectedInstallResolution != null
                && selectedInstallResolution.state() != ExtensionInstallResolutionState.AVAILABLE
                && isMeaningfulValue(selectedInstallResolution.message())) {
            return selectedInstallResolution.message();
        }
        if (isMeaningfulValue(message)) {
            return message;
        }
        return switch (state) {
            case LOADING -> "Cargando";
            case READY -> "Listo";
            case EMPTY -> "Sin contenido";
            case ERROR -> "Error";
            case BLOCKED -> "Revisar";
            case IDLE -> "Sin selección";
        };
    }

    private ViewState resolveCurrentDetailVisualState() {
        if (selectedEntry == null) {
            return ViewState.IDLE;
        }
        if (detailState.state() == ViewState.LOADING || previewState.state() == ViewState.LOADING) {
            return ViewState.LOADING;
        }
        if (detailState.state() == ViewState.ERROR || previewState.state() == ViewState.ERROR) {
            return ViewState.ERROR;
        }
        if (previewState.state() == ViewState.BLOCKED) {
            return ViewState.BLOCKED;
        }
        if (selectedInstallResolution != null && selectedInstallResolution.blocksInstall() && !isExactInstalledResolution(selectedInstallResolution)) {
            return ViewState.BLOCKED;
        }
        if (detailState.state() == ViewState.READY || previewState.state() == ViewState.READY) {
            return ViewState.READY;
        }
        return ViewState.IDLE;
    }

    private boolean isExactInstalledResolution(ExtensionInstallResolution resolution) {
        return resolution != null && resolution.exactVersionInstalled();
    }

    private String formatProjectLink(String url) {
        if (!isMeaningfulValue(url)) {
            return "Sin enlace disponible";
        }
        String normalizedUrl = url.trim();
        normalizedUrl = normalizedUrl.replaceFirst("^https?://", "");
        normalizedUrl = normalizedUrl.replaceFirst("/$", "");
        return trimToLength(normalizedUrl, 48);
    }

    private void setDetailProjectUrl(String url) {
        detailProjectUrl = isMeaningfulValue(url) ? url.trim() : null;
        detailProjectUrlLabel.setText(formatProjectLink(detailProjectUrl));
        detailProjectUrlLabel.setToolTipText(detailProjectUrl);
    }

    private void openDetailProjectUrl() {
        openUrl(detailProjectUrl);
    }

    private void openUrlAtDetailPosition(MouseEvent event) {
        String url = urlAtDetailPosition(event);
        if (url != null) {
            openUrl(url);
        }
    }

    private String urlAtDetailPosition(MouseEvent event) {
        int offset = detailDescriptionArea.viewToModel2D(event.getPoint());
        if (offset < 0) {
            return null;
        }
        for (LinkRange link : detailDescriptionLinks) {
            if (offset >= link.start() && offset <= link.end()) {
                return link.url();
            }
        }
        return null;
    }

    private void openUrl(String url) {
        if (!isMeaningfulValue(url) || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (RuntimeException | java.io.IOException ex) {
            LOGGER.log(Level.FINE, "No se ha podido abrir el enlace " + url, ex);
            showUserError("No se ha podido abrir el enlace.");
        }
    }

    private void setDetailDescriptionText(String text) {
        renderDetailDescription(defaultString(text, ""));
        detailDescriptionArea.setCaretPosition(0);
        scrollDetailToTop();
    }

    private void scrollDetailToTop() {
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
            detailDescriptionArea.setCaretPosition(0);
            detailDescriptionArea.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
            if (detailBodyScrollPane != null) {
                detailBodyScrollPane.getVerticalScrollBar().setValue(0);
                detailBodyScrollPane.getViewport().setViewPosition(new java.awt.Point(0, 0));
            }
        }));
    }

    private void renderDetailDescription(String rawText) {
        detailDescriptionLinks.clear();
        ExtensionDescriptionRenderer.renderInto(detailDescriptionContentPanel, rawText, this::openUrl);
    }

    private void appendDetailLine(StyledDocument document, String line) throws BadLocationException {
        String value = defaultString(line, "");
        SimpleAttributeSet attributes = baseDetailAttributes();
        Matcher heading = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+)$").matcher(value);
        if (heading.matches()) {
            value = heading.group(2).trim();
            StyleConstants.setBold(attributes, true);
            StyleConstants.setFontSize(attributes, heading.group(1).length() <= 2 ? 16 : 14);
        } else if (value.stripLeading().startsWith(">")) {
            value = value.stripLeading().replaceFirst("^>\\s?", "");
            StyleConstants.setItalic(attributes, true);
            StyleConstants.setForeground(attributes, AppTheme.getMutedForeground());
        } else if (value.contains("  |  ")) {
            StyleConstants.setFontFamily(attributes, Font.MONOSPACED);
        }
        appendInlineDetailText(document, value, attributes);
    }

    private void appendInlineDetailText(StyledDocument document,
                                        String text,
                                        SimpleAttributeSet baseAttributes) throws BadLocationException {
        Matcher linkMatcher = MARKDOWN_LINK_PATTERN.matcher(text);
        int cursor = 0;
        while (linkMatcher.find()) {
            appendBareUrls(document, text.substring(cursor, linkMatcher.start()), baseAttributes);
            appendDetailLink(document, defaultString(linkMatcher.group(1), linkMatcher.group(2)), linkMatcher.group(2), baseAttributes);
            cursor = linkMatcher.end();
        }
        appendBareUrls(document, text.substring(cursor), baseAttributes);
    }

    private void appendBareUrls(StyledDocument document,
                                String text,
                                SimpleAttributeSet baseAttributes) throws BadLocationException {
        Matcher urlMatcher = URL_PATTERN.matcher(text);
        int cursor = 0;
        while (urlMatcher.find()) {
            appendInlineWithoutLinks(document, text.substring(cursor, urlMatcher.start()), baseAttributes);
            appendDetailLink(document, urlMatcher.group(), urlMatcher.group(), baseAttributes);
            cursor = urlMatcher.end();
        }
        appendInlineWithoutLinks(document, text.substring(cursor), baseAttributes);
    }

    private void appendInlineWithoutLinks(StyledDocument document,
                                          String text,
                                          SimpleAttributeSet baseAttributes) throws BadLocationException {
        if (text == null || text.isEmpty()) {
            return;
        }
        String remaining = text;
        Pattern emphasisPattern = Pattern.compile("(\\*\\*|__)(.+?)\\1|(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)|(?<!_)_([^_\\n]+)_(?!_)|~~(.+?)~~|`([^`]+)`");
        Matcher matcher = emphasisPattern.matcher(remaining);
        int cursor = 0;
        while (matcher.find()) {
            insertDetailText(document, remaining.substring(cursor, matcher.start()), baseAttributes);
            SimpleAttributeSet styled = new SimpleAttributeSet(baseAttributes);
            String content;
            if (matcher.group(6) != null) {
                content = matcher.group(6);
                StyleConstants.setFontFamily(styled, Font.MONOSPACED);
                StyleConstants.setBackground(styled, AppTheme.withAlpha(AppTheme.getForeground(), 20));
            } else if (matcher.group(5) != null) {
                content = matcher.group(5);
                StyleConstants.setStrikeThrough(styled, true);
            } else if (matcher.group(3) != null || matcher.group(4) != null) {
                content = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
                StyleConstants.setItalic(styled, true);
            } else {
                content = matcher.group(2);
                StyleConstants.setBold(styled, true);
            }
            insertDetailText(document, content, styled);
            cursor = matcher.end();
        }
        insertDetailText(document, remaining.substring(cursor), baseAttributes);
    }

    private void appendDetailLink(StyledDocument document,
                                  String label,
                                  String url,
                                  SimpleAttributeSet baseAttributes) throws BadLocationException {
        String visible = isMeaningfulValue(label) ? label : url;
        int start = document.getLength();
        SimpleAttributeSet attributes = new SimpleAttributeSet(baseAttributes);
        StyleConstants.setForeground(attributes, AppTheme.getLinkForeground());
        StyleConstants.setUnderline(attributes, true);
        insertDetailText(document, visible, attributes);
        detailDescriptionLinks.add(new LinkRange(start, document.getLength(), url));
    }

    private void insertDetailText(StyledDocument document,
                                  String text,
                                  SimpleAttributeSet attributes) throws BadLocationException {
        if (text == null || text.isEmpty()) {
            return;
        }
        document.insertString(document.getLength(), softWrapLongTokens(decodeBasicHtmlEntities(text)), attributes);
    }

    private SimpleAttributeSet baseDetailAttributes() {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        Font font = detailDescriptionArea.getFont();
        StyleConstants.setFontFamily(attributes, font.getFamily());
        StyleConstants.setFontSize(attributes, Math.max(1, font.getSize()));
        StyleConstants.setForeground(attributes, detailDescriptionArea.getForeground());
        return attributes;
    }

    private String prepareRichDisplayText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = normalizeTableRows(removeUnsafeRichContent(decodeBasicHtmlEntities(text)))
                .replaceAll("(?is)<\\s*a\\s+[^>]*href\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>(.*?)</\\s*a\\s*>", "[$2]($1)")
                .replaceAll("(?is)\\[url=([^\\]]+)](.*?)\\[/url]", "[$2]($1)")
                .replaceAll("(?is)\\[url](https?://[^\\[]+)\\[/url]", "$1")
                .replaceAll("(?is)<\\s*img\\b[^>]*>", "")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "")
                .replaceAll("\\[]\\((https?://[^)\\s]+)(?:\\s+\"[^\"]*\")?\\)", "")
                .replaceAll("\\[]\\(([^)]+)\\)", "")
                .replaceAll("(?is)\\[img][^\\[]*\\[/img]", "")
                .replaceAll("(?is)\\[img[^\\]]*]", "");
        return finishDisplayText(stripPresentationMarkup(applyStructuralMarkup(cleaned)));
    }

    private String removeUnsafeRichContent(String text) {
        return defaultString(text, "")
                .replaceAll("(?is)<\\s*(script|style)[^>]*>.*?</\\s*\\1\\s*>", "");
    }

    private String normalizeTableRows(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = replaceTables(
                text,
                Pattern.compile("(?is)\\[table]\\s*(.*?)\\s*\\[/table]"),
                Pattern.compile("(?is)\\[tr]\\s*(.*?)\\s*\\[/tr]"),
                Pattern.compile("(?is)\\[(?:td|th)]\\s*(.*?)\\s*\\[/(?:td|th)]")
        );
        normalized = replaceTables(
                normalized,
                Pattern.compile("(?is)<\\s*table[^>]*>\\s*(.*?)\\s*</\\s*table\\s*>"),
                Pattern.compile("(?is)<\\s*tr[^>]*>\\s*(.*?)\\s*</\\s*tr\\s*>"),
                Pattern.compile("(?is)<\\s*(?:td|th)[^>]*>\\s*(.*?)\\s*</\\s*(?:td|th)\\s*>")
        );
        normalized = replaceTableRows(
                normalized,
                Pattern.compile("(?is)\\[tr]\\s*(.*?)\\s*\\[/tr]"),
                Pattern.compile("(?is)\\[(?:td|th)]\\s*(.*?)\\s*\\[/(?:td|th)]")
        );
        return replaceTableRows(
                normalized,
                Pattern.compile("(?is)<\\s*tr[^>]*>\\s*(.*?)\\s*</\\s*tr\\s*>"),
                Pattern.compile("(?is)<\\s*(?:td|th)[^>]*>\\s*(.*?)\\s*</\\s*(?:td|th)\\s*>")
        );
    }

    private String replaceTables(String text, Pattern tablePattern, Pattern rowPattern, Pattern cellPattern) {
        Matcher tableMatcher = tablePattern.matcher(text);
        StringBuffer output = new StringBuffer();
        while (tableMatcher.find()) {
            String replacement = formatTableRows(extractTableRows(tableMatcher.group(1), rowPattern, cellPattern));
            tableMatcher.appendReplacement(output, Matcher.quoteReplacement("\n" + replacement + "\n"));
        }
        tableMatcher.appendTail(output);
        return output.toString();
    }

    private String replaceTableRows(String text, Pattern rowPattern, Pattern cellPattern) {
        Matcher rowMatcher = rowPattern.matcher(text);
        StringBuffer output = new StringBuffer();
        while (rowMatcher.find()) {
            List<List<String>> rows = extractTableRows(rowMatcher.group(), rowPattern, cellPattern);
            String replacement = formatTableRows(rows);
            rowMatcher.appendReplacement(output, Matcher.quoteReplacement("\n" + replacement + "\n"));
        }
        rowMatcher.appendTail(output);
        return output.toString();
    }

    private List<List<String>> extractTableRows(String tableText, Pattern rowPattern, Pattern cellPattern) {
        List<List<String>> rows = new ArrayList<>();
        Matcher rowMatcher = rowPattern.matcher(defaultString(tableText, ""));
        while (rowMatcher.find()) {
            String row = rowMatcher.group(1);
            Matcher cellMatcher = cellPattern.matcher(row);
            List<String> cells = new ArrayList<>();
            while (cellMatcher.find()) {
                String cell = stripPresentationMarkup(applyStructuralMarkup(cellMatcher.group(1)))
                        .replaceAll("\\R+", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
                cells.add(cell);
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        return rows;
    }

    private String formatTableRows(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        if (columns <= 0) {
            return "";
        }
        int[] widths = new int[columns];
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], defaultString(row.get(i), "").length());
            }
        }
        List<String> formatted = new ArrayList<>();
        for (List<String> row : rows) {
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < columns; i++) {
                String cell = i < row.size() ? defaultString(row.get(i), "") : "";
                cells.add(padRight(cell, widths[i]));
            }
            formatted.add(String.join("  |  ", cells).stripTrailing());
        }
        return String.join("\n", formatted);
    }

    private String padRight(String value, int width) {
        String text = defaultString(value, "");
        if (text.length() >= width) {
            return text;
        }
        return text + " ".repeat(width - text.length());
    }

    private String applyStructuralMarkup(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
                .replaceAll("(?i)<\\s*br\\s*/?\\s*>", "\n")
                .replaceAll("(?i)</\\s*(p|div|section|article|blockquote|h[1-6])\\s*>", "\n\n")
                .replaceAll("(?i)<\\s*li[^>]*>", "\n- ")
                .replaceAll("(?i)</\\s*li\\s*>", "\n")
                .replaceAll("(?i)</\\s*(ul|ol)\\s*>", "\n")
                .replaceAll("(?i)<\\s*/?\\s*(thead|tbody|tfoot|table)[^>]*>", "\n")
                .replaceAll("(?i)<\\s*/?\\s*tr[^>]*>", "\n")
                .replaceAll("(?i)</\\s*(td|th)\\s*>", " | ")
                .replaceAll("(?i)<\\s*(td|th)[^>]*>", "")
                .replaceAll("(?i)\\[/?(?:table|thead|tbody|tfoot)]", "\n")
                .replaceAll("(?i)\\[/?tr]", "\n")
                .replaceAll("(?i)\\[/?(?:td|th)]", " | ")
                .replaceAll("(?i)\\[\\*]", "\n- ")
                .replaceAll("(?i)\\[/?list(?:=[^\\]]*)?]", "\n")
                .replaceAll("(?i)\\[/?quote(?:=[^\\]]*)?]", "\n")
                .replaceAll("(?i)\\[(?:divider|separator|hr)]", "\n");
    }

    private String stripPresentationMarkup(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
                .replaceAll("(?is)<[^>]+>", "")
                .replaceAll("(?is)\\[/?(?:b|i|u|s|strike|center|left|right|code|pre|spoiler)]", "")
                .replaceAll("(?is)\\[/?(?:size|color|font|align|anchor|heading|sub|sup)=[^\\]]*]", "")
                .replaceAll("(?is)\\[/?(?:size|color|font|align|anchor|heading|sub|sup)]", "")
                .replaceAll("(?is)\\[/url]", "")
                .replaceAll("(?s)\\[[a-z][a-z0-9_-]*(?:=[^\\]]*)?]", "")
                .replaceAll("(?s)\\[/[a-z][a-z0-9_-]*]", "");
    }

    private String finishDisplayText(String text) {
        String cleaned = defaultString(text, "")
                .replaceAll("(?m)^\\s*[-*_]{3,}\\s*$", "")
                .replaceAll("(?m)^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$", "")
                .replaceAll("(?m)^\\s*\\|\\s*$", "")
                .replaceAll("[ \\t]*\\|[ \\t]*(\\n|$)", "$1")
                .replaceAll("(?m)^\\s*\\|\\s*", "");
        return decodeBasicHtmlEntities(cleaned)
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String formatDownloads(long downloads, String providerId) {
        if (downloads <= 0L) {
            return "Sin dato de descargas";
        }
        return compactNumber(downloads) + " descargas en " + describeProvider(providerId);
    }

    private String compactNumber(long value) {
        if (value >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000d).replace(".0", "");
        }
        if (value >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000d).replace(".0", "");
        }
        if (value >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000d).replace(".0", "");
        }
        return Long.toString(value);
    }

    private boolean isMeaningfulValue(String value) {
        return value != null
                && !value.isBlank()
                && !"-".equals(value.trim())
                && !"Sin enlace disponible".equalsIgnoreCase(value.trim());
    }

    private Component createSpacer() {
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        return spacer;
    }

    private ExtensionCatalogQuery buildSearchQuery(ProviderFilterOption providerFilter, SideFilterOption sideFilter) {
        String queryText = defaultString(normalized(searchField.getText()), "");
        boolean broadTypedSearch = queryText != null && !queryText.isBlank();
        ServerPlatform platform = broadTypedSearch ? ServerPlatform.UNKNOWN : selectedPlatformFilter();
        String version = broadTypedSearch || server == null || server.getVersion() == null
                ? ""
                : defaultString(normalized(server.getVersion()), "");
        int providerLimit = sideFilter == null || sideFilter.kind() == SideFilterKind.ANY
                ? searchLimit
                : Math.min(MAX_SEARCH_RESULTS, Math.max(searchLimit * 4, 100));
        return new ExtensionCatalogQuery(
                queryText,
                platform,
                resolveServerExtensionType(),
                version,
                Math.min(MAX_SEARCH_RESULTS, Math.max(1, providerLimit)),
                selectedSearchSort().providerSort(),
                toCatalogSideFilter(sideFilter),
                selectedProviderId(providerFilter)
        );
    }

    private ExtensionCatalogQuery buildDetailsQuery(ExtensionCatalogEntry entry) {
        String queryText = defaultString(normalized(searchField.getText()), "");
        ServerPlatform platform = server == null || server.getPlatform() == null
                ? ServerPlatform.UNKNOWN
                : server.getPlatform();
        String version = server == null || server.getVersion() == null
                ? ""
                : defaultString(normalized(server.getVersion()), "");
        return new ExtensionCatalogQuery(
                queryText,
                platform,
                resolveServerExtensionType(),
                version,
                20,
                selectedSearchSort().providerSort(),
                ExtensionSideFilter.ANY,
                entry == null ? NO_PROVIDER_ID : defaultString(entry.providerId(), NO_PROVIDER_ID)
        );
    }

    private String selectedProviderId(ProviderFilterOption providerFilter) {
        if (providerFilter == null || providerFilter.providerId() == null || providerFilter.providerId().isBlank()) {
            return NO_PROVIDER_ID;
        }
        return providerFilter.providerId();
    }

    private SearchSortOption selectedSearchSort() {
        Object selected = sortCombo.getSelectedItem();
        return selected instanceof SearchSortOption option
                ? option
                : new SearchSortOption("Descargas", "downloads", false);
    }

    private SideFilterOption selectedSideFilter() {
        Object selected = sideCombo.getSelectedItem();
        return selected instanceof SideFilterOption option ? option : SideFilterOption.any();
    }

    private ServerPlatform selectedPlatformFilter() {
        Object selected = loaderCombo.getSelectedItem();
        if (selected instanceof PlatformFilterOption option) {
            return option.platform() == null ? ServerPlatform.UNKNOWN : option.platform();
        }
        return server == null || server.getPlatform() == null ? ServerPlatform.UNKNOWN : server.getPlatform();
    }

    private ExtensionSideFilter toCatalogSideFilter(SideFilterOption option) {
        if (option == null || option.kind() == SideFilterKind.ANY) {
            return ExtensionSideFilter.ANY;
        }
        return switch (option.kind()) {
            case CLIENT -> ExtensionSideFilter.CLIENT;
            case BOTH -> ExtensionSideFilter.CLIENT_AND_SERVER;
            case SERVER -> ExtensionSideFilter.SERVER;
            case ANY -> ExtensionSideFilter.ANY;
        };
    }

    private MarketplaceSearchSpec buildSearchSpec() {
        ProviderFilterOption providerOption = (ProviderFilterOption) providerCombo.getSelectedItem();
        SideFilterOption sideFilter = selectedSideFilter();
        return new MarketplaceSearchSpec(
                buildSearchQuery(providerOption, sideFilter),
                providerOption,
                sideFilter,
                true,
                selectedSearchSort(),
                selectedResultLimit(),
                Math.min(MAX_SEARCH_RESULTS, Math.max(1, searchLimit))
        );
    }

    private ServerExtensionType resolveServerExtensionType() {
        return extensionTypeFor(server);
    }

    private ServerEcosystemType serverEcosystem() {
        return ecosystemOf(server);
    }

    private boolean hasExtensionEcosystem() {
        ServerEcosystemType ecosystem = serverEcosystem();
        return ecosystem == ServerEcosystemType.MODS || ecosystem == ServerEcosystemType.PLUGINS;
    }

    static ServerEcosystemType ecosystemOf(Server server) {
        if (server == null) {
            return ServerEcosystemType.UNKNOWN;
        }
        if (server.getEcosystemType() != null && server.getEcosystemType() != ServerEcosystemType.UNKNOWN) {
            return server.getEcosystemType();
        }
        ServerPlatform platform = server.getPlatform();
        if (platform != null && platform != ServerPlatform.UNKNOWN) {
            return platform.getDefaultEcosystemType();
        }
        return ServerEcosystemType.UNKNOWN;
    }

    static ServerExtensionType extensionTypeFor(Server server) {
        return switch (ecosystemOf(server)) {
            case MODS -> ServerExtensionType.MOD;
            case PLUGINS -> ServerExtensionType.PLUGIN;
            default -> ServerExtensionType.UNKNOWN;
        };
    }

    private static String marketplaceTitle(Server server) {
        return switch (ecosystemOf(server)) {
            case MODS -> "Catálogo de mods";
            case PLUGINS -> "Catálogo de plugins";
            default -> "Catálogo de extensiones";
        };
    }

    private String extensionSingularLower() {
        return switch (serverEcosystem()) {
            case MODS -> "mod";
            case PLUGINS -> "plugin";
            default -> "extensión";
        };
    }

    private String extensionPluralLower() {
        return switch (serverEcosystem()) {
            case MODS -> "mods";
            case PLUGINS -> "plugins";
            default -> "extensiones";
        };
    }

    private String searchPlaceholderText() {
        return "Buscar " + extensionPluralLower() + " por nombre, autor o función";
    }

    private String platformFilterLabel() {
        return serverEcosystem() == ServerEcosystemType.MODS ? "Loader" : "Plataforma";
    }

    private String articleForCurrentExtension() {
        return hasExtensionEcosystem() ? "un" : "una";
    }

    private boolean matchesProviderFilter(ExtensionCatalogEntry entry, ProviderFilterOption option) {
        if (entry == null || option == null || option.providerId() == null || option.providerId().isBlank()) {
            return false;
        }
        return option.providerId().equalsIgnoreCase(entry.providerId());
    }

    private boolean matchesSideFilter(ExtensionCatalogEntry entry, SideFilterOption option) {
        if (entry == null || option == null || option.kind() == SideFilterKind.ANY) {
            return true;
        }
        if (!hasDeclaredSideMetadata(entry)) {
            return false;
        }
        boolean client = supportsClientSide(entry);
        boolean serverSide = supportsServerSide(entry);
        return switch (option.kind()) {
            case CLIENT -> client && !serverSide;
            case BOTH -> client && serverSide;
            case SERVER -> serverSide && !client;
            case ANY -> true;
        };
    }

    private boolean supportsClientSide(ExtensionCatalogEntry entry) {
        return isSupportedSide(entry == null ? null : entry.clientSide());
    }

    private boolean supportsServerSide(ExtensionCatalogEntry entry) {
        return isSupportedSide(entry == null ? null : entry.serverSide());
    }

    private boolean isSupportedSide(String side) {
        return "required".equalsIgnoreCase(side) || "optional".equalsIgnoreCase(side);
    }

    private MarketplaceCompatibilityAssessment assessEntryCompatibility(ExtensionCatalogEntry entry) {
        if (entry == null) {
            return new MarketplaceCompatibilityAssessment(
                    ExtensionCompatibilityStatus.WARNING,
                    "Compatibilidad sin datos suficientes",
                    List.of("No hay información suficiente del proveedor para evaluar esta extensión.")
            );
        }
        String key = defaultString(entry.providerId(), "") + "|" + defaultString(entry.projectId(), "")
                + "|" + defaultString(entry.versionId(), "") + "|" + defaultString(entry.version(), "")
                + "|" + defaultString(entry.extensionType() == null ? null : entry.extensionType().name(), "")
                + "|" + metadataKey(entry.compatiblePlatforms())
                + "|" + metadataKey(entry.compatibleMinecraftVersions())
                + "|" + defaultString(server == null ? null : server.getVersion(), "")
                + "|" + defaultString(server == null || server.getPlatform() == null ? null : server.getPlatform().name(), "")
                + "|" + defaultString(serverEcosystem().name(), "");
        MarketplaceCompatibilityAssessment cached = compatibilityCache.get(key);
        if (cached != null) {
            return cached;
        }
        MarketplaceCompatibilityAssessment assessment = assessCompatibility(
                entry.extensionType(),
                entry.compatiblePlatforms(),
                entry.compatibleMinecraftVersions()
        );
        compatibilityCache.put(key, assessment);
        return assessment;
    }

    private String metadataKey(java.util.Set<?> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(","));
    }

    private MarketplaceCompatibilityAssessment assessVersionCompatibility(ExtensionCatalogVersion version) {
        if (version == null) {
            return new MarketplaceCompatibilityAssessment(
                    ExtensionCompatibilityStatus.WARNING,
                    "Versión sin datos suficientes",
                    List.of("No hay información suficiente para validar esta versión.")
            );
        }
        ServerExtensionType type = resolveServerExtensionType();
        return assessCompatibility(type, version.supportedPlatforms(), version.supportedMinecraftVersions());
    }

    private MarketplaceCompatibilityAssessment assessCompatibility(ServerExtensionType extensionType,
                                                                   java.util.Set<ServerPlatform> declaredPlatforms,
                                                                   java.util.Set<String> declaredVersions) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        ServerEcosystemType resolvedEcosystem = serverEcosystem();
        ServerPlatform serverPlatform = server == null || server.getPlatform() == null
                ? ServerPlatform.UNKNOWN
                : canonicalizePlatform(server.getPlatform());
        String serverVersion = normalized(server == null ? null : server.getVersion());

        if (resolvedEcosystem == ServerEcosystemType.MODS && extensionType == ServerExtensionType.PLUGIN) {
            blockers.add("Esta extensión es un plugin y el servidor actual acepta mods.");
        } else if (resolvedEcosystem == ServerEcosystemType.PLUGINS && extensionType == ServerExtensionType.MOD) {
            blockers.add("Esta extensión es un mod y el servidor actual acepta plugins.");
        } else if (extensionType == null || extensionType == ServerExtensionType.UNKNOWN) {
            warnings.add("El proveedor no declara el tipo de extensión.");
        }

        if (serverPlatform == ServerPlatform.UNKNOWN) {
            warnings.add("No se conoce la plataforma exacta del servidor.");
        } else if (declaredPlatforms == null || declaredPlatforms.isEmpty()) {
            warnings.add("El proveedor no declara loaders o plataformas compatibles.");
        } else {
            boolean platformMatch = declaredPlatforms.stream()
                    .filter(platform -> platform != null && platform != ServerPlatform.UNKNOWN)
                    .map(this::canonicalizePlatform)
                    .anyMatch(platform -> platform == serverPlatform);
            if (!platformMatch) {
                blockers.add("La extensión declara compatibilidad con " + joinPreview(
                        declaredPlatforms.stream().map(this::labelForPlatform).toList(), 4
                ) + ", pero el servidor actual usa " + labelForPlatform(serverPlatform) + ".");
            }
        }

        if (serverVersion == null) {
            warnings.add("No se conoce la versión de Minecraft del servidor.");
        } else if (declaredVersions == null || declaredVersions.isEmpty()) {
            warnings.add("El proveedor no declara versiones de Minecraft compatibles.");
        } else {
            VersionMatchType versionMatch = matchMinecraftVersion(serverVersion, declaredVersions);
            if (versionMatch == VersionMatchType.NONE) {
                blockers.add("La extensión no declara compatibilidad para Minecraft " + serverVersion + ".");
            } else if (versionMatch == VersionMatchType.FAMILY) {
                warnings.add("La compatibilidad solo coincide por familia de versión (" + serverVersionFamily(serverVersion) + ").");
            }
        }

        if (!blockers.isEmpty()) {
            return new MarketplaceCompatibilityAssessment(
                    ExtensionCompatibilityStatus.INCOMPATIBLE,
                    blockers.getFirst(),
                    List.copyOf(blockers)
            );
        }
        if (!warnings.isEmpty()) {
            return new MarketplaceCompatibilityAssessment(
                    ExtensionCompatibilityStatus.WARNING,
                    warnings.getFirst(),
                    List.copyOf(warnings)
            );
        }
        return new MarketplaceCompatibilityAssessment(
                ExtensionCompatibilityStatus.COMPATIBLE,
                "Compatible con este servidor.",
                List.of("Plataforma, tipo de extensión y versión de Minecraft coinciden con el servidor actual.")
        );
    }

    private String describeServerBaseline() {
        String platform = server == null || server.getPlatform() == null ? "?" : labelForPlatform(server.getPlatform());
        String version = normalized(server == null ? null : server.getVersion());
        return "Servidor: " + platform + "  |  Minecraft " + (version == null ? "sin versión" : version);
    }

    private boolean providerAllowedForServer(ExtensionCatalogProviderDescriptor provider) {
        if (provider == null || provider.providerId() == null) {
            return false;
        }
        if (provider.capabilities() == null || !provider.capabilities().contains(ExtensionCatalogCapability.SEARCH)) {
            return false;
        }
        ServerEcosystemType ecosystem = serverEcosystem();
        ServerExtensionType serverExtensionType = switch (ecosystem) {
            case MODS -> ServerExtensionType.MOD;
            case PLUGINS -> ServerExtensionType.PLUGIN;
            default -> ServerExtensionType.UNKNOWN;
        };
        if (serverExtensionType != ServerExtensionType.UNKNOWN
                && provider.supportedExtensionTypes() != null
                && !provider.supportedExtensionTypes().isEmpty()
                && !provider.supportedExtensionTypes().contains(serverExtensionType)) {
            return false;
        }
        if (provider.supportedPlatforms() != null
                && !provider.supportedPlatforms().isEmpty()
                && provider.supportedPlatforms().stream()
                .filter(Objects::nonNull)
                .noneMatch(platform -> platform.getDefaultEcosystemType() == ecosystem
                        || canonicalizePlatform(platform).getDefaultEcosystemType() == ecosystem)) {
            return false;
        }
        String providerId = provider.providerId().trim().toLowerCase(Locale.ROOT);
        if (ecosystem == ServerEcosystemType.MODS) {
            return !"hangar".equals(providerId);
        }
        if (ecosystem == ServerEcosystemType.PLUGINS) {
            return true;
        }
        return false;
    }

    private String describeProvider(String providerId) {
        String resolved = providerLabelsById.get(normalized(providerId));
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        return providerId == null ? "-" : providerId;
    }

    private String buildDetailsDescription(ExtensionCatalogEntry entry, ExtensionCatalogDetails details) {
        StringBuilder sb = new StringBuilder();
        if (details != null && details.summary() != null && !details.summary().isBlank()) {
            sb.append(details.summary().trim());
        } else if (entry.description() != null && !entry.description().isBlank()) {
            sb.append(entry.description().trim());
        } else {
            sb.append("Sin descripción disponible.");
        }

        return sb.toString();
    }

    private String getSearchVersionText() {
        Object editorValue = searchVersionCombo.getEditor().getItem();
        if (editorValue != null) {
            return editorValue.toString();
        }
        Object selected = searchVersionCombo.getSelectedItem();
        return selected == null ? null : selected.toString();
    }

    private String buildAvailableVersionsPreview(List<ExtensionCatalogVersion> versions) {
        if (versions == null || versions.isEmpty()) {
            return "Sin versiones declaradas";
        }
        List<String> compatible = versions.stream()
                .filter(version -> assessVersionCompatibility(version).status() == ExtensionCompatibilityStatus.COMPATIBLE)
                .map(version -> defaultString(version.versionNumber(), version.displayName()))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(3)
                .toList();
        if (!compatible.isEmpty()) {
            return "Compatible: " + String.join(", ", compatible);
        }
        return "Sin build compatible marcada";
    }

    private String describeVersionMeta(ExtensionCatalogVersion version) {
        StringBuilder sb = new StringBuilder();
        if (version.publishedAtEpochMillis() > 0L) {
            sb.append(VERSION_DATE_FORMAT.format(Instant.ofEpochMilli(version.publishedAtEpochMillis())));
        }
        if (version.supportedMinecraftVersions() != null && !version.supportedMinecraftVersions().isEmpty()) {
            if (sb.length() > 0) {
                sb.append("  |  ");
            }
            sb.append(String.join(", ", version.supportedMinecraftVersions()));
        }
        return sb.length() == 0 ? "Sin metadata adicional" : sb.toString();
    }

    private void restartDebounce() {
        debounceTimer.restart();
    }

    private int compatibilityRank(ExtensionCompatibilityStatus status) {
        return switch (status == null ? ExtensionCompatibilityStatus.WARNING : status) {
            case COMPATIBLE -> 0;
            case WARNING -> 1;
            case INCOMPATIBLE -> 2;
        };
    }

    private VersionMatchType matchMinecraftVersion(String serverVersion, java.util.Set<String> declaredVersions) {
        if (serverVersion == null || declaredVersions == null || declaredVersions.isEmpty()) {
            return VersionMatchType.NONE;
        }
        String normalizedServerVersion = serverVersion.trim();
        String serverFamily = serverVersionFamily(normalizedServerVersion);
        boolean familyMatch = false;
        for (String declared : declaredVersions) {
            if (declared == null || declared.isBlank()) {
                continue;
            }
            String normalizedDeclared = declared.trim();
            if (normalizedDeclared.equalsIgnoreCase(normalizedServerVersion)) {
                return VersionMatchType.EXACT;
            }
            if (serverFamily.equals(serverVersionFamily(normalizedDeclared))) {
                familyMatch = true;
            }
        }
        return familyMatch ? VersionMatchType.FAMILY : VersionMatchType.NONE;
    }

    private String serverVersionFamily(String version) {
        if (version == null || version.isBlank()) {
            return "";
        }
        String[] segments = version.trim().split("\\.");
        if (segments.length < 2) {
            return version.trim();
        }
        return segments[0] + "." + segments[1];
    }

    private String labelForPlatform(ServerPlatform platform) {
        if (platform == null) {
            return "Desconocido";
        }
        return switch (platform) {
            case NEOFORGE -> "NeoForge";
            case PUFFERFISH -> "Pufferfish";
            default -> {
                String lower = platform.name().toLowerCase(Locale.ROOT);
                yield Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
            }
        };
    }

    private ServerPlatform canonicalizePlatform(ServerPlatform platform) {
        if (platform == null) {
            return ServerPlatform.UNKNOWN;
        }
        return switch (platform) {
            case PURPUR, PUFFERFISH -> ServerPlatform.PAPER;
            default -> platform;
        };
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    private String appendRecoveryNote(String base, String note) {
        if (note == null || note.isBlank()) {
            return base;
        }
        String normalizedBase = defaultString(base, "");
        if (normalizedBase.contains(note)) {
            return normalizedBase;
        }
        if (normalizedBase.isBlank()) {
            return note;
        }
        return normalizedBase + "\n\n" + note;
    }

    private String buildDetailSubtitle(String author, String providerId) {
        String authorText = defaultString(author, "Autor desconocido");
        return authorText;
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private String friendlySearchError(Throwable error) {
        String root = rootMessage(error).toLowerCase(Locale.ROOT);
        if (root.contains("ningún proveedor") || root.contains("http") || root.contains("consult")) {
            return "No se ha podido consultar el marketplace ahora mismo.";
        }
        if (root.contains("interrump")) {
            return "La búsqueda se ha interrumpido antes de terminar.";
        }
        return "No se ha podido actualizar la búsqueda del marketplace.";
    }

    private String friendlyDetailError(Throwable error) {
        String root = rootMessage(error).toLowerCase(Locale.ROOT);
        if (root.contains("http") || root.contains("consult")) {
            return "No se ha podido cargar el detalle remoto en este momento.";
        }
        return "No se ha podido ampliar la información de esta extensión.";
    }

    private String friendlyDetailRecoveryHint(Throwable error) {
        String root = rootMessage(error).toLowerCase(Locale.ROOT);
        if (root.contains("http") || root.contains("consult")) {
            return "Puedes seguir revisando la ficha resumida y reintentar la carga volviendo a seleccionar la extensión o refrescando la búsqueda.";
        }
        return "Puedes seguir usando la información del listado y reintentar la carga de detalles más tarde.";
    }

    private String friendlyPreviewError(Throwable error) {
        String root = rootMessage(error).toLowerCase(Locale.ROOT);
        if (root.contains("http") || root.contains("consult")) {
            return "No se ha podido preparar la descarga desde el proveedor.";
        }
        if (root.contains("interrump")) {
            return "La preparación de la descarga se ha interrumpido.";
        }
        return "No se ha podido preparar la instalación de forma segura.";
    }

    private String friendlyPreviewRecoveryHint(Throwable error) {
        String root = rootMessage(error).toLowerCase(Locale.ROOT);
        if (root.contains("compatible")) {
            return "Prueba otra versión del listado o revisa una build declarada para otro loader o versión.";
        }
        if (root.contains("http") || root.contains("consult")) {
            return "La instalación queda bloqueada hasta poder contactar otra vez con el proveedor remoto.";
        }
        return "La acción queda bloqueada para evitar una instalación incompleta o incoherente.";
    }

    private String friendlySyncError(Throwable error) {
        String root = rootMessage(error).toLowerCase(Locale.ROOT);
        if (root.contains("http") || root.contains("consult")) {
            return "No se ha podido actualizar el estado remoto de las extensiones instaladas.";
        }
        if (root.contains("ningún proveedor")) {
            return "No se ha podido consultar ningún proveedor para sincronizar la biblioteca.";
        }
        return "No se ha podido sincronizar la biblioteca instalada del servidor.";
    }

    private String friendlySyncRecoveryHint(Throwable error) {
        String root = rootMessage(error).toLowerCase(Locale.ROOT);
        if (root.contains("http") || root.contains("consult") || root.contains("ningún proveedor")) {
            return "Se conserva la información local actual. Puedes reintentar la sincronización más tarde.";
        }
        return "La biblioteca local se mantiene como estaba antes del intento de sincronización.";
    }

    private final class SearchResultRenderer extends JPanel implements ListCellRenderer<MarketplaceEntryViewModel> {
        private final DefaultListCellRenderer fallback = new DefaultListCellRenderer();
        private final JLabel iconLabel = new JLabel();
        private final JPanel loadMorePanel = new JPanel(new BorderLayout(8, 0));
        private final JLabel loadMoreLabel = new JLabel("Cargar más");
        private final JPanel resultContentPanel = new JPanel(new BorderLayout(8, 0));
        private final JPanel resultTextPanel = new JPanel(new BorderLayout(0, 2));
        private final JPanel titleRow = new JPanel(new BorderLayout(4, 0));
        private final JPanel titleTextPanel = new JPanel();
        private final JLabel nameLabel = new JLabel();
        private final JLabel authorLabel = new JLabel();
        private final JLabel downloadsLabel = new JLabel();
        private final JLabel compatibilityLabel = new JLabel();
        private final JLabel sideLabel = new JLabel();
        private final JPanel statusPanel = new JPanel(new GridLayout(2, 1, 0, 0));
        private final JPanel eastPanel = new JPanel(new BorderLayout(0, 0));
        private final JLabel rowActionLabel = new JLabel();
        private final JLabel descriptionLabel = new JLabel();

        private SearchResultRenderer() {
            super(new BorderLayout(10, 0));
            setOpaque(true);

            iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
            iconLabel.setVerticalAlignment(SwingConstants.CENTER);
            iconLabel.setOpaque(true);
            iconLabel.setPreferredSize(new Dimension(52, 52));
            add(iconLabel, BorderLayout.WEST);

            loadMorePanel.setOpaque(false);
            loadMoreLabel.setFont(loadMoreLabel.getFont().deriveFont(Font.BOLD, 13.5f));
            loadMoreLabel.setHorizontalAlignment(SwingConstants.CENTER);
            loadMoreLabel.setIcon(SvgIconFactory.create("doraicons/plus.svg", 18, 18, AppTheme::getForeground));
            loadMoreLabel.setIconTextGap(8);
            loadMorePanel.add(loadMoreLabel, BorderLayout.CENTER);

            resultTextPanel.setOpaque(false);
            resultTextPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 15.5f));
            authorLabel.setFont(authorLabel.getFont().deriveFont(Font.PLAIN, 12.5f));
            authorLabel.setForeground(AppTheme.getMutedForeground());
            downloadsLabel.setFont(downloadsLabel.getFont().deriveFont(Font.PLAIN, 12.5f));
            downloadsLabel.setForeground(AppTheme.getMutedForeground());
            compatibilityLabel.setFont(compatibilityLabel.getFont().deriveFont(Font.BOLD, 11.5f));
            compatibilityLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            compatibilityLabel.setVerticalAlignment(SwingConstants.CENTER);
            compatibilityLabel.setOpaque(true);
            sideLabel.setFont(sideLabel.getFont().deriveFont(Font.BOLD, 10.8f));
            sideLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            sideLabel.setVerticalAlignment(SwingConstants.CENTER);
            sideLabel.setOpaque(true);
            statusPanel.setOpaque(false);
            statusPanel.add(compatibilityLabel);
            statusPanel.add(sideLabel);
            rowActionLabel.setHorizontalAlignment(SwingConstants.CENTER);
            rowActionLabel.setVerticalAlignment(SwingConstants.CENTER);
            rowActionLabel.setPreferredSize(new Dimension(56, 52));
            descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(13.25f));
            descriptionLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 210));
            descriptionLabel.setVerticalAlignment(SwingConstants.TOP);
            descriptionLabel.setHorizontalAlignment(SwingConstants.LEFT);

            titleRow.setOpaque(false);
            titleTextPanel.setOpaque(false);
            titleTextPanel.setLayout(new BoxLayout(titleTextPanel, BoxLayout.X_AXIS));
            titleTextPanel.add(nameLabel);
            titleTextPanel.add(Box.createHorizontalStrut(4));
            titleTextPanel.add(authorLabel);
            titleTextPanel.add(Box.createHorizontalStrut(8));
            titleTextPanel.add(downloadsLabel);
            titleRow.add(titleTextPanel, BorderLayout.CENTER);
            eastPanel.setOpaque(false);
            eastPanel.add(statusPanel, BorderLayout.CENTER);
            eastPanel.add(rowActionLabel, BorderLayout.EAST);

            resultContentPanel.setOpaque(false);
            resultTextPanel.add(titleRow, BorderLayout.NORTH);
            resultTextPanel.add(descriptionLabel, BorderLayout.CENTER);
            resultContentPanel.add(resultTextPanel, BorderLayout.CENTER);
            resultContentPanel.add(eastPanel, BorderLayout.EAST);
            add(resultContentPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends MarketplaceEntryViewModel> list,
                                                      MarketplaceEntryViewModel value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            if (value == null || value.entry() == null) {
                if (value != null && value.loadMoreRow()) {
                    removeAll();
                    add(loadMorePanel, BorderLayout.CENTER);
                    setBackground(isSelected ? AppTheme.getSoftSelectionBackground() : AppTheme.getPanelBackground());
                    setBorder(AppTheme.createRoundedBorder(
                            new Insets(10, 10, 10, 10),
                            isSelected ? AppTheme.getMainAccent() : AppTheme.getBorderColor(),
                            1f
                    ));
                    loadMoreLabel.setForeground(AppTheme.getForeground());
                    setToolTipText("Cargar más");
                    return this;
                }
                return fallback.getListCellRendererComponent(list, "", index, isSelected, cellHasFocus);
            }
            removeAll();
            add(iconLabel, BorderLayout.WEST);
            add(resultContentPanel, BorderLayout.CENTER);

            ExtensionCatalogEntry entry = value.entry();
            boolean installed = value.installResolution() != null && value.installResolution().alreadyInstalled();
            boolean incompatible = value.compatibilityStatus() == ExtensionCompatibilityStatus.INCOMPATIBLE;
            setBackground(resolveResultRowBackground(isSelected, installed, incompatible));
            setBorder(AppTheme.createRoundedBorder(
                    new Insets(0, 0, 0, 0),
                    resolveResultRowBorderColor(isSelected, installed, incompatible),
                    1f
            ));
            iconLabel.setBackground(resolveResultIconBackground(isSelected, installed, incompatible));
            iconLabel.setPreferredSize(new Dimension(52, 52));
            iconLabel.setIcon(ExtensionIconLoader.getIcon(entry.iconUrl(), 52, list::repaint));
            int rowWidth = Math.max(320, list.getWidth() - 24);
            MarketplaceCompatibilityAssessment compatibilityAssessment = new MarketplaceCompatibilityAssessment(
                    value.compatibilityStatus(),
                    value.compatibilitySummary(),
                    value.compatibilityReasons()
            );
            boolean showCompatibilityBadge = shouldShowCompatibilityBadge(compatibilityAssessment);
            String compatibilityText = showCompatibilityBadge ? compatibilityBadgeText(compatibilityAssessment) : "";
            String resolvedCompatibilityText = compatibilityText;
            String sideText = sideText(entry);
            int compatibilityWidth = compatibilityLabel.getFontMetrics(compatibilityLabel.getFont()).stringWidth(resolvedCompatibilityText) + 18;
            int sideWidth = sideLabel.getFontMetrics(sideLabel.getFont()).stringWidth(sideText) + 18;
            int badgeWidth = Math.min(190, Math.max(92, showCompatibilityBadge ? Math.max(compatibilityWidth, sideWidth) : sideWidth));
            int statusHeight = showCompatibilityBadge && isMeaningfulValue(sideText) ? 52 : 26;
            statusPanel.setLayout(new GridLayout(2, 1, 0, 0));
            statusPanel.setPreferredSize(new Dimension(badgeWidth, statusHeight));
            statusPanel.setMinimumSize(new Dimension(badgeWidth, statusHeight));
            statusPanel.setMaximumSize(new Dimension(badgeWidth, statusHeight));
            eastPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            boolean queued = value.queueStateText() != null;
            boolean showAction = installed || queued || index == resultsListHoverIndex;
            int actionWidth = showAction ? 56 : 0;
            int iconColumnWidth = iconLabel.getPreferredSize().width + 10;
            int textColumnWidth = Math.max(170, rowWidth - iconColumnWidth - badgeWidth - actionWidth - 20);
            int titleBudget = textColumnWidth;
            int downloadsNeeded = downloadsLabel.getFontMetrics(downloadsLabel.getFont()).stringWidth("|  " + compactNumber(entry.downloads()));
            int authorTarget = Math.max(80, Math.min(180, titleBudget / 3));
            int downloadsTarget = Math.max(48, Math.min(90, downloadsNeeded));
            int nameBudget = Math.max(92, titleBudget - authorTarget - downloadsTarget);
            nameLabel.setText(ellipsize(defaultString(entry.displayName(), "Extensión"), nameLabel.getFont(), nameBudget));
            String providerAndType = defaultString(value.providerLabel(), entry.providerId()) + " / " + packageLabel(entry.extensionType());
            authorLabel.setText("|  " + ellipsize(providerAndType, authorLabel.getFont(), authorTarget));
            authorLabel.setToolTipText(providerAndType);
            downloadsLabel.setText("|  " + ellipsize(compactNumber(entry.downloads()), downloadsLabel.getFont(), downloadsTarget));
            compatibilityLabel.setVisible(showCompatibilityBadge);
            compatibilityLabel.setText(ellipsize(resolvedCompatibilityText, compatibilityLabel.getFont(), badgeWidth - 18));
            compatibilityLabel.setToolTipText(resolvedCompatibilityText);
            Color compatibilityBackground = compatibilityBadgeBackground(compatibilityAssessment);
            Color compatibilityForeground = compatibilityBadgeForeground(compatibilityAssessment);
            compatibilityLabel.setForeground(compatibilityForeground);
            compatibilityLabel.setBackground(compatibilityBackground);
            compatibilityLabel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AppTheme.withAlpha(compatibilityForeground, 105), 1, true),
                    BorderFactory.createEmptyBorder(0, 8, 0, 8)
            ));
            configureSmallStatusLabel(sideLabel, sideText, badgeWidth - 18);
            sideLabel.setVisible(true);
            rowActionLabel.setVisible(showAction);
            rowActionLabel.setPreferredSize(new Dimension(56, 52));
            rowActionLabel.setIcon(SvgIconFactory.create(installed || queued ? "doraicons/trash-unselected.svg" : "doraicons/plus.svg", 48, 48, AppTheme::getForeground));
            eastPanel.setPreferredSize(new Dimension(badgeWidth + actionWidth, statusHeight));
            eastPanel.setMinimumSize(new Dimension(badgeWidth + actionWidth, statusHeight));
            int descriptionWidth = Math.max(170, textColumnWidth);
            String installStatus = value.installResolution() == null || !isMeaningfulValue(value.installResolution().message())
                    ? "Disponible"
                    : value.installResolution().message();
            String meta = defaultString(value.platformsSummary(), "Plataforma sin declarar") + " | " + installStatus;
            String description = meta + " - " + defaultString(value.descriptionPreview(), "Sin descripción.");
            descriptionLabel.setVisible(true);
            descriptionLabel.setText(ellipsize(description, descriptionLabel.getFont(), descriptionWidth));
            String tooltip = "<html><b>" + escapeHtml(defaultString(entry.displayName(), "Extensión")) + "</b><br>"
                    + escapeHtml(defaultString(entry.author(), "Autor desconocido"))
                    + " · " + escapeHtml(formatDownloads(entry.downloads(), entry.providerId()))
                    + "<br>" + escapeHtml(description) + "</html>";
            setToolTipText(tooltip);
            descriptionLabel.setToolTipText(tooltip);
            return this;
        }

    }

    private int resultsListHoverIndex = -1;

    private void configureSmallStatusLabel(JLabel label, String text, int maxTextWidth) {
        Color background = ExtensionStatusPresentation.neutralBadgeBackground();
        Color foreground = ExtensionStatusPresentation.foregroundFor(background);
        label.setText(ellipsize(defaultString(text, ""), label.getFont(), Math.max(32, maxTextWidth)));
        label.setForeground(foreground);
        label.setOpaque(true);
        label.setBackground(background);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.withAlpha(foreground, 60), 1, true),
                BorderFactory.createEmptyBorder(0, 8, 0, 8)
        ));
        label.setToolTipText(text);
    }

    private Color resolveResultRowBackground(boolean selected, boolean installed, boolean incompatible) {
        Color base = AppTheme.getPanelBackground();
        if (incompatible) {
            base = AppTheme.tint(base, AppTheme.getDangerColor(), AppTheme.isLightTheme() ? 0.10f : 0.18f);
        } else if (installed) {
            base = AppTheme.tint(base, AppTheme.getSuccessColor(), AppTheme.isLightTheme() ? 0.16f : 0.24f);
        }
        if (selected) {
            Color selectionTint = incompatible
                    ? AppTheme.getDangerColor()
                    : installed ? AppTheme.getSuccessColor() : AppTheme.getMainAccent();
            return AppTheme.tint(base, selectionTint, incompatible ? 0.14f : installed ? 0.12f : 0.10f);
        }
        return base;
    }

    private Color resolveResultRowBorderColor(boolean selected, boolean installed, boolean incompatible) {
        if (selected) {
            return incompatible
                    ? AppTheme.getDangerColor()
                    : installed ? AppTheme.getSuccessColor() : AppTheme.getMainAccent();
        }
        if (incompatible) {
            return AppTheme.withAlpha(AppTheme.getDangerColor(), AppTheme.isLightTheme() ? 140 : 170);
        }
        if (installed) {
            return AppTheme.withAlpha(AppTheme.getSuccessColor(), AppTheme.isLightTheme() ? 150 : 180);
        }
        return AppTheme.getBorderColor();
    }

    private Color resolveResultIconBackground(boolean selected, boolean installed, boolean incompatible) {
        if (incompatible) {
            return AppTheme.tint(
                    AppTheme.getPanelBackground(),
                    AppTheme.getDangerColor(),
                    AppTheme.isLightTheme() ? 0.16f : 0.24f
            );
        }
        if (installed) {
            return AppTheme.tint(
                    AppTheme.getPanelBackground(),
                    AppTheme.getSuccessColor(),
                    AppTheme.isLightTheme() ? 0.20f : 0.28f
            );
        }
        if (selected) {
            return AppTheme.withAlpha(AppTheme.getMainAccent(), 18);
        }
        return AppTheme.withAlpha(AppTheme.getForeground(), 12);
    }

    private String sideText(ExtensionCatalogEntry entry) {
        if (entry == null || !hasDeclaredSideMetadata(entry)) {
            return "";
        }
        boolean serverSide = supportsServerSide(entry);
        boolean client = supportsClientSide(entry);
        if (serverSide && client) {
            return "Cliente + servidor";
        }
        if (serverSide) {
            return "Servidor";
        }
        if (client) {
            return "Cliente";
        }
        return "Lado indefinido";
    }

    private final class VersionRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list,
                                                      Object value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            VersionOption option = (VersionOption) value;
            String text = option == null ? "-" : option.displayName() + "  |  " + option.meta();
            JLabel label = (JLabel) super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            if (option != null && option.compatible()) {
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            }
            return label;
        }
    }

    private final class QueueItemRenderer implements ListCellRenderer<DownloadQueueItem> {
        @Override
        public Component getListCellRendererComponent(JList<? extends DownloadQueueItem> list,
                                                      DownloadQueueItem value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            JPanel card = new JPanel(new BorderLayout(10, 0));
            card.setOpaque(true);
            QueueState currentState = value == null ? QueueState.PENDING : value.state;
            card.setBackground(resolveQueueRowBackground(currentState, isSelected));
            card.setBorder(AppTheme.createRoundedBorder(
                    new Insets(0, 0, 0, 0),
                    isSelected ? queueBorderColor(currentState) : AppTheme.withAlpha(queueBorderColor(currentState), 120),
                    1f
            ));

            JLabel icon = new JLabel();
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            icon.setVerticalAlignment(SwingConstants.CENTER);
            icon.setOpaque(true);
            icon.setBackground(AppTheme.withAlpha(AppTheme.getForeground(), 12));
            icon.setPreferredSize(new Dimension(34, 34));
            icon.setIcon(ExtensionIconLoader.getIcon(value == null ? null : value.iconUrl, 34, list::repaint));
            card.add(icon, BorderLayout.WEST);

            JPanel text = new JPanel(new BorderLayout(8, 0));
            text.setOpaque(false);
            text.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            JPanel textLines = new JPanel();
            textLines.setOpaque(false);
            textLines.setLayout(new BoxLayout(textLines, BoxLayout.Y_AXIS));
            int textBudget = Math.max(120, list.getWidth() - 170);
            JLabel title = new JLabel(ellipsize(value == null ? "Descarga" : value.displayName, list.getFont().deriveFont(Font.BOLD, 13f), textBudget));
            title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
            JLabel meta = new JLabel(ellipsize(value == null ? "-" : queueStateLabel(currentState) + "  |  " + value.versionLabel + "  |  " + value.message, list.getFont().deriveFont(12f), textBudget));
            meta.setFont(meta.getFont().deriveFont(12f));
            meta.setForeground(AppTheme.getMutedForeground());
            textLines.add(title);
            textLines.add(Box.createVerticalStrut(2));
            textLines.add(meta);

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            controls.setOpaque(false);
            JLabel remove = new JLabel();
            remove.setHorizontalAlignment(SwingConstants.CENTER);
            remove.setVerticalAlignment(SwingConstants.CENTER);
            remove.setPreferredSize(new Dimension(28, 28));
            remove.setOpaque(true);
            remove.setBackground(AppTheme.withAlpha(AppTheme.getForeground(), 16));
            remove.setBorder(AppTheme.createRoundedBorder(new Insets(0, 0, 0, 0), AppTheme.withAlpha(AppTheme.getBorderColor(), 120), 1f));
            remove.setIcon(SvgIconFactory.create(
                    "doraicons/trash-unselected.svg",
                    18,
                    18,
                    () -> currentState == QueueState.DOWNLOADING ? AppTheme.getMutedForeground() : AppTheme.getForeground()
            ));
            JLabel state = new JLabel(symbolFor(value == null ? QueueState.PENDING : value.state), SwingConstants.CENTER);
            state.setOpaque(true);
            Color stateBackground = colorFor(currentState);
            Color stateForeground = ExtensionStatusPresentation.foregroundFor(stateBackground);
            state.setForeground(stateForeground);
            state.setBackground(stateBackground);
            state.setFont(state.getFont().deriveFont(Font.BOLD, 10.5f));
            state.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            state.setPreferredSize(new Dimension(34, 34));
            if (currentState == QueueState.RESOLVING) {
                state.setText(null);
                state.setIcon(queueDownloadingIcon);
            } else if (currentState == QueueState.PENDING) {
                state.setText(null);
                state.setIcon(SvgIconFactory.create("doraicons/hourglass.svg", 18, 18, () -> stateForeground));
            } else if (currentState == QueueState.DOWNLOADING) {
                state.setText(null);
                state.setIcon(queueDownloadingIcon);
            } else if (currentState == QueueState.COMPLETED) {
                state.setText(null);
                state.setIcon(queueCompletedIcon);
            } else {
                state.setIcon(null);
            }
            controls.add(remove);
            controls.add(state);
            text.add(textLines, BorderLayout.CENTER);
            text.add(controls, BorderLayout.EAST);
            card.add(text, BorderLayout.CENTER);
            return card;
        }

        private String symbolFor(QueueState state) {
            return switch (state) {
                case RESOLVING -> "RS";
                case PENDING -> "EN";
                case DOWNLOADING -> "DL";
                case COMPLETED -> "OK";
                case FAILED -> "!";
            };
        }

        private String queueStateLabel(QueueState state) {
            return switch (state) {
                case RESOLVING -> "Preparando";
                case PENDING -> "En cola";
                case DOWNLOADING -> "Instalando";
                case COMPLETED -> "Completada";
                case FAILED -> "Con incidencia";
            };
        }

        private Color colorFor(QueueState state) {
            return switch (state) {
                case RESOLVING -> AppTheme.tint(AppTheme.getMainAccent(), Color.WHITE, 0.10f);
                case PENDING -> AppTheme.getMainAccent();
                case DOWNLOADING -> AppTheme.tint(AppTheme.getMainAccent(), Color.WHITE, 0.18f);
                case COMPLETED -> AppTheme.getSuccessColor();
                case FAILED -> readableWarningColor();
            };
        }

        private Color queueBorderColor(QueueState state) {
            return switch (state) {
                case FAILED -> readableWarningColor();
                case COMPLETED -> AppTheme.getSuccessColor();
                case RESOLVING, DOWNLOADING, PENDING -> AppTheme.getMainAccent();
            };
        }

        private Color resolveQueueRowBackground(QueueState state, boolean selected) {
            Color base = AppTheme.getPanelBackground();
            if (state == QueueState.FAILED) {
                base = AppTheme.tint(base, readableWarningColor(), AppTheme.isLightTheme() ? 0.12f : 0.18f);
            }
            return selected ? AppTheme.tint(base, queueBorderColor(state), 0.10f) : base;
        }
    }

    private JLabel createBadge(String text, Color background, Color foreground) {
        JLabel label = new JLabel(text);
        label.setOpaque(true);
        label.setBackground(background);
        Color readableForeground = ExtensionStatusPresentation.foregroundFor(background);
        label.setForeground(readableForeground);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11.5f));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.withAlpha(readableForeground, 72), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        return label;
    }

    private static final class DetailBodyPanel extends JPanel implements Scrollable {
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
            return Math.max(16, visibleRect.height - 32);
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

    private void appendResultBadges(JPanel badges, MarketplaceEntryViewModel viewModel) {
        if (badges == null || viewModel == null || viewModel.entry() == null) {
            return;
        }
        if (viewModel.queueStateText() != null) {
            badges.add(createBadge(
                    viewModel.queueStateText(),
                    AppTheme.withAlpha(AppTheme.getWarningColor(), 26),
                    AppTheme.getWarningColor()
            ));
        }
        badges.add(createBadge(
                packageLabel(viewModel.entry().extensionType()),
                AppTheme.withAlpha(AppTheme.getForeground(), 16),
                AppTheme.withAlpha(AppTheme.getForeground(), 210)
        ));
        MarketplaceCompatibilityAssessment compatibilityAssessment = new MarketplaceCompatibilityAssessment(
                viewModel.compatibilityStatus(),
                viewModel.compatibilitySummary(),
                viewModel.compatibilityReasons()
        );
        if (shouldShowCompatibilityBadge(compatibilityAssessment)) {
            badges.add(createBadge(
                    compatibilityBadgeText(compatibilityAssessment),
                    compatibilityBadgeBackground(compatibilityAssessment),
                    compatibilityBadgeForeground(compatibilityAssessment)
            ));
        }
    }

    private boolean hasDeclaredSideMetadata(ExtensionCatalogEntry entry) {
        return entry != null && (isMeaningfulValue(entry.clientSide()) || isMeaningfulValue(entry.serverSide()));
    }

    private String packageLabel(ServerExtensionType extensionType) {
        return (extensionType == null ? ServerExtensionType.UNKNOWN : extensionType).getDisplayName();
    }

    static boolean dependencyMatchesInstalledExtension(ExtensionDependency dependency, ServerExtension extension) {
        return ExtensionDependencyMatcher.matchesInstalledExtension(dependency, extension);
    }

    static boolean dependencyMatchesCandidate(ExtensionDependency dependency,
                                              String providerId,
                                              String projectId,
                                              String displayName,
                                              String localId) {
        if (dependency == null) {
            return false;
        }
        return ExtensionDependencyMatcher.matchesCandidate(dependency, providerId, projectId, displayName, localId);
    }

    static String normalizedDependencyKey(ExtensionDependency dependency) {
        return ExtensionDependencyMatcher.normalizedDependencyKey(dependency);
    }

    private static String normalizeIdentifier(String value) {
        return ExtensionDependencyMatcher.normalizeIdentifier(value);
    }

    private static String defaultStringStatic(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String summarizeDependencies(List<ExtensionDependency> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return "Sin dependencias declaradas";
        }
        List<String> required = dependencies.stream()
                .filter(Objects::nonNull)
                .filter(ExtensionDependency::required)
                .map(dependency -> defaultString(dependency.displayName(), dependency.projectId()))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(4)
                .toList();
        if (!required.isEmpty()) {
            long totalRequired = dependencies.stream()
                    .filter(Objects::nonNull)
                    .filter(ExtensionDependency::required)
                    .count();
            String suffix = totalRequired > required.size() ? " +" + (totalRequired - required.size()) : "";
            return "Requeridas: " + String.join(", ", required) + suffix;
        }
        long optionalCount = dependencies.stream().filter(Objects::nonNull).count();
        return optionalCount == 1L ? "1 dependencia opcional" : optionalCount + " dependencias opcionales";
    }

    private String escapeHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String ellipsize(String text, Font font, int maxWidth) {
        String value = defaultString(text, "");
        if (maxWidth <= 0 || value.isBlank() || value.length() <= 1) {
            return value;
        }
        return TextEllipsizer.rightStrict(value, getFontMetrics(font == null ? getFont() : font), maxWidth);
    }

    static String compatibilityBadgeText(MarketplaceCompatibilityAssessment assessment) {
        if (assessment == null || assessment.status() == null) {
            return "Compatibilidad sin validar";
        }
        return switch (assessment.status()) {
            case COMPATIBLE -> "Compatible";
            case WARNING -> "Revisar";
            case INCOMPATIBLE -> "No compatible";
        };
    }

    private String buildCompatibilityDisplayText(MarketplaceCompatibilityAssessment assessment) {
        if (assessment == null) {
            return "Compatibilidad sin validar";
        }
        if (assessment.reasons() == null || assessment.reasons().isEmpty()) {
            return assessment.summary();
        }
        return assessment.summary() + "  |  " + trimToLength(assessment.reasons().getFirst(), 72);
    }

    static boolean isMetadataPendingWarning(MarketplaceCompatibilityAssessment assessment) {
        if (assessment == null || assessment.status() != ExtensionCompatibilityStatus.WARNING) {
            return false;
        }
        List<String> reasons = assessment.reasons() == null ? List.of() : assessment.reasons();
        if (reasons.isEmpty()) {
            return false;
        }
        return reasons.stream()
                .filter(Objects::nonNull)
                .map(reason -> reason.trim().toLowerCase(Locale.ROOT))
                .allMatch(reason -> reason.startsWith("el proveedor no declara loaders")
                        || reason.startsWith("el proveedor no declara versiones de minecraft"));
    }

    static boolean shouldShowCompatibilityBadge(MarketplaceCompatibilityAssessment assessment) {
        return assessment != null
                && assessment.status() != null
                && !isMetadataPendingWarning(assessment);
    }

    private Color compatibilityBadgeForeground(MarketplaceCompatibilityAssessment assessment) {
        return isMetadataPendingWarning(assessment)
                ? ExtensionStatusPresentation.foregroundFor(ExtensionStatusPresentation.neutralBadgeBackground())
                : ExtensionStatusPresentation.foreground(assessment == null ? null : assessment.status());
    }

    private Color compatibilityBadgeBackground(MarketplaceCompatibilityAssessment assessment) {
        return isMetadataPendingWarning(assessment)
                ? ExtensionStatusPresentation.neutralBadgeBackground()
                : ExtensionStatusPresentation.background(assessment == null ? null : assessment.status());
    }

    private Color readableWarningColor() {
        return AppTheme.isLightTheme()
                ? AppTheme.darken(AppTheme.getWarningColor(), 0.34f)
                : AppTheme.getWarningColor();
    }

    private String summarizePlatforms(ExtensionCatalogEntry entry) {
        if (entry == null || entry.compatiblePlatforms() == null || entry.compatiblePlatforms().isEmpty()) {
            return "Loader: cualquiera";
        }
        List<String> labels = entry.compatiblePlatforms().stream()
                .map(this::labelForPlatform)
                .filter(label -> label != null && !label.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return "Loader: " + joinPreview(labels, 3);
    }

    private String summarizeVersions(ExtensionCatalogEntry entry) {
        if (entry == null || entry.compatibleMinecraftVersions() == null || entry.compatibleMinecraftVersions().isEmpty()) {
            return "MC: sin declarar";
        }
        return "MC: " + joinPreview(entry.compatibleMinecraftVersions(), 4);
    }

    private String joinPreview(Iterable<String> values, int limit) {
        if (values == null) {
            return "-";
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String trimmed = value.trim();
            if (!cleaned.contains(trimmed)) {
                cleaned.add(trimmed);
            }
        }
        if (cleaned.isEmpty()) {
            return "-";
        }
        if (cleaned.size() <= limit) {
            return String.join(", ", cleaned);
        }
        return String.join(", ", cleaned.subList(0, limit)) + " +" + (cleaned.size() - limit);
    }

    private String normalizeInlineText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return cleanDisplayText(text).replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanDisplayText(String text) {
        return ExtensionDescriptionRenderer.cleanPlainText(text);
    }

    private String softWrapLongTokens(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("/", "/\u200B")
                .replace(".", ".\u200B")
                .replace("-", "-\u200B")
                .replace("_", "_\u200B")
                .replace("?", "?\u200B")
                .replace("&", "&\u200B")
                .replace("=", "=\u200B");
    }

    private String decodeBasicHtmlEntities(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&#91;", "[")
                .replace("&#x5B;", "[")
                .replace("&#x5b;", "[")
                .replace("&lbrack;", "[")
                .replace("&#93;", "]")
                .replace("&#x5D;", "]")
                .replace("&#x5d;", "]")
                .replace("&rbrack;", "]");
    }

    private String trimToLength(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private final class ResultDecorationRefreshWorker extends SwingWorker<List<MarketplaceEntryViewModel>, Void> {
        private final long requestId;
        private final List<ExtensionCatalogEntry> entries;
        private final Map<String, String> queueStateSnapshot;
        private final String selectedKey;

        private ResultDecorationRefreshWorker(long requestId,
                                              List<ExtensionCatalogEntry> entries,
                                              Map<String, String> queueStateSnapshot,
                                              String selectedKey) {
            this.requestId = requestId;
            this.entries = entries == null ? List.of() : List.copyOf(entries);
            this.queueStateSnapshot = queueStateSnapshot == null ? Map.of() : Map.copyOf(queueStateSnapshot);
            this.selectedKey = selectedKey;
        }

        @Override
        protected List<MarketplaceEntryViewModel> doInBackground() {
            return entries.stream()
                    .map(entry -> buildEntryViewModel(entry, queueStateSnapshot))
                    .toList();
        }

        @Override
        protected void done() {
            if (isCancelled() || requestId != resultDecorationSequence || !isDisplayable()) {
                return;
            }
            try {
                List<MarketplaceEntryViewModel> refreshed = get();
                replaceResultsModel(refreshed);
                restoreResultSelection(selectedKey, false);
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "No se ha podido refrescar la decoracion del listado del marketplace.", ex);
            } finally {
                if (resultDecorationWorker == this) {
                    resultDecorationWorker = null;
                }
            }
        }
    }

    private final class MinecraftVersionsWorker extends SwingWorker<List<String>, Void> {
        private final String preferredVersion;

        private MinecraftVersionsWorker(String preferredVersion) {
            this.preferredVersion = preferredVersion;
        }

        @Override
        protected List<String> doInBackground() {
            try {
                return new MojangAPI().obtenerListaVersiones();
            } catch (RuntimeException ex) {
                LOGGER.log(Level.FINE, "No se han podido cargar versiones de Minecraft para el marketplace.", ex);
                return List.of();
            }
        }

        @Override
        protected void done() {
            if (!isDisplayable()) {
                return;
            }
            Object selected = searchVersionCombo.getEditor().getItem();
            try {
                for (String version : get()) {
                    if (version != null && !version.isBlank() && !comboContains(searchVersionModel, version)) {
                        searchVersionModel.addElement(version);
                    }
                }
                searchVersionCombo.setSelectedItem(selected == null || selected.toString().isBlank()
                        ? defaultString(preferredVersion, "")
                        : selected.toString());
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "No se han podido aplicar versiones de Minecraft al filtro del marketplace.", ex);
            }
        }
    }

    private final class MarketplaceSearchWorker extends SwingWorker<List<MarketplaceEntryViewModel>, Void> {
        private final long requestId;
        private final MarketplaceSearchSpec searchSpec;
        private final List<MarketplaceEntryViewModel> previousResults;
        private final String previousSelectionKey;
        private final Map<String, String> queueStateSnapshot;

        private MarketplaceSearchWorker(long requestId,
                                        MarketplaceSearchSpec searchSpec,
                                        List<MarketplaceEntryViewModel> previousResults,
                                        String previousSelectionKey,
                                        Map<String, String> queueStateSnapshot) {
            this.requestId = requestId;
            this.searchSpec = searchSpec;
            this.previousResults = previousResults == null ? List.of() : List.copyOf(previousResults);
            this.previousSelectionKey = previousSelectionKey;
            this.queueStateSnapshot = queueStateSnapshot == null ? Map.of() : Map.copyOf(queueStateSnapshot);
        }

        @Override
        protected List<MarketplaceEntryViewModel> doInBackground() throws Exception {
            List<ExtensionCatalogEntry> allResults = gestorServidores.buscarExtensionesExternas(searchSpec.query());
            return allResults.stream()
                    .filter(entry -> matchesProviderFilter(entry, searchSpec.provider()))
                    .filter(entry -> matchesSideFilter(entry, searchSpec.sideFilter()))
                    .map(entry -> buildEntryViewModel(entry, queueStateSnapshot))
                    .filter(viewModel -> shouldDisplaySearchResult(searchSpec, viewModel))
                    .toList();
        }

        @Override
        protected void done() {
            if (requestId != searchRequestSequence || !isDisplayable()) {
                return;
            }
            updateFilterState();
            try {
                List<MarketplaceEntryViewModel> results = get();
                results = applyLocalSort(results, searchSpec.sortOption());
                results = results.stream()
                        .limit(searchSpec.displayLimit())
                        .toList();
                replaceResultsModel(results);
                searchState = results.isEmpty()
                        ? new SearchViewState(ViewState.EMPTY, "No hay resultados con los filtros actuales.", requestId)
                        : new SearchViewState(ViewState.READY, results.size() + " resultados", requestId);
                lastExecutedSearchSpec = searchSpec;
                iconState = results.isEmpty()
                        ? new IconViewState(ViewState.EMPTY, "Sin iconos para cargar")
                        : new IconViewState(ViewState.LOADING, "Cargando iconos del listado");
                ExtensionIconLoader.prefetchIcons(
                        resultsList,
                        results.stream().map(viewModel -> viewModel.entry().iconUrl()).toList(),
                        52,
                        () -> {
                            resultsList.repaint(resultsList.getVisibleRect());
                            updateCatalogStatusLabel();
                        }
                );
                iconState = results.isEmpty()
                        ? new IconViewState(ViewState.EMPTY, "Sin iconos para cargar")
                        : new IconViewState(ViewState.READY, "Iconos cargando en segúndo plano");
                updateCatalogStatusLabel();
                if (!results.isEmpty()) {
                    restoreResultSelection(previousSelectionKey, true);
                } else {
                    clearDetails();
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Fallo al ejecutar la búsqueda del marketplace.", ex);
                restoreResults(previousResults);
                searchState = new SearchViewState(
                        ViewState.ERROR,
                        previousResults.isEmpty()
                                ? friendlySearchError(ex)
                                : friendlySearchError(ex) + " Se mantienen los resultados anteriores.",
                        requestId
                );
                updateCatalogStatusLabel();
                if (previousResults.isEmpty()) {
                    clearDetails();
                }
            }
        }
    }

    static boolean shouldDisplaySearchResult(MarketplaceSearchSpec searchSpec, MarketplaceEntryViewModel viewModel) {
        if (viewModel == null) {
            return false;
        }
        if (searchSpec == null || !searchSpec.compatibilityOnly() || searchSpec.hasSearchText()) {
            return true;
        }
        return viewModel.compatibilityStatus() != ExtensionCompatibilityStatus.INCOMPATIBLE;
    }

    private final class MarketplaceDetailsWorker extends SwingWorker<Optional<ExtensionCatalogDetails>, Void> {
        private final ExtensionCatalogEntry entry;
        private final ExtensionCatalogQuery query;
        private final long requestId;

        private MarketplaceDetailsWorker(ExtensionCatalogEntry entry, ExtensionCatalogQuery query, long requestId) {
            this.entry = entry;
            this.query = query;
            this.requestId = requestId;
        }

        @Override
        protected Optional<ExtensionCatalogDetails> doInBackground() throws Exception {
            return resolveDetailsCached(entry, query);
        }

        @Override
        protected void done() {
            if (requestId != detailRequestSequence || !sameEntry(selectedEntry, entry) || !isDisplayable()) {
                return;
            }
            try {
                selectedDetails = get().orElse(null);
                detailState = new DetailViewState(
                        ViewState.READY,
                        selectedDetails == null
                                ? "El proveedor no ha devuelto una ficha ampliada. Se muestra la información resumida."
                                : "Detalles cargados.",
                        entry,
                        requestId
                );
                populateDetails(entry, selectedDetails);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Fallo al cargar detalles del marketplace para " + entry.projectId(), ex);
                detailState = new DetailViewState(ViewState.ERROR, friendlyDetailError(ex), entry, requestId);
                detailBaseDescription = "La ficha resumida sigue disponible, pero no se ha podido cargar información ampliada.\n\n"
                        + friendlyDetailRecoveryHint(ex);
                setDetailDescriptionText(detailBaseDescription);
                refreshSelectionActionState();
            }
        }
    }

    private final class PreviewInstallPlanWorker extends SwingWorker<ExtensionDownloadPlan, Void> {
        private final ExtensionCatalogEntry entry;
        private final String versionId;
        private final long requestId;

        private PreviewInstallPlanWorker(ExtensionCatalogEntry entry, String versionId, long requestId) {
            this.entry = entry;
            this.versionId = versionId;
            this.requestId = requestId;
        }

        @Override
        protected ExtensionDownloadPlan doInBackground() throws Exception {
            return resolveDownloadPlanCached(entry.providerId(), entry.projectId(), versionId);
        }

        @Override
        protected void done() {
            if (requestId != previewRequestSequence || !sameEntry(selectedEntry, entry) || !isDisplayable()) {
                return;
            }
            try {
                selectedDownloadPlan = enrichDownloadPlanDescription(entry, get());
                selectedInstallResolution = selectedDownloadPlan == null
                        ? evaluateInstallResolution(entry)
                        : evaluateInstallResolution(selectedDownloadPlan);
                if (selectedDownloadPlan == null || !selectedDownloadPlan.ready()) {
                    previewState = new PreviewViewState(ViewState.BLOCKED, "No hay build compatible para este servidor.", versionId, requestId);
                    detailVersionLabel.setText("-");
                    detailFileLabel.setText("-");
                    refreshSelectionActionState();
                    return;
                }
                previewState = new PreviewViewState(
                        ViewState.READY,
                        defaultString(selectedDownloadPlan.message(), "Descarga lista para instalar."),
                        versionId,
                        requestId
                );
                detailTypeLabel.setText(packageLabel(selectedDownloadPlan.extensionType()));
                detailProviderLabel.setText(selectedDownloadPlan.platform() == null
                        || selectedDownloadPlan.platform() == ServerPlatform.UNKNOWN
                        ? summarizePlatforms(entry)
                        : labelForPlatform(selectedDownloadPlan.platform()));
                detailDependenciesLabel.setText(summarizeDependencies(selectedDownloadPlan.dependencies()));
                detailVersionLabel.setText(defaultString(selectedDownloadPlan.versionNumber(), "-"));
                detailFileLabel.setText(defaultString(selectedDownloadPlan.fileName(), "-"));
                refreshSelectionActionState();
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Fallo al preparar la instalación del marketplace para " + entry.projectId(), ex);
                previewState = new PreviewViewState(ViewState.ERROR, friendlyPreviewError(ex), versionId, requestId);
                detailVersionLabel.setText("-");
                detailFileLabel.setText("-");
                selectedInstallResolution = evaluateInstallResolution(entry);
                detailBaseDescription = appendRecoveryNote(detailBaseDescription, friendlyPreviewRecoveryHint(ex));
                refreshSelectionActionState();
            }
        }
    }

    private final class EnqueuePlanWorker extends SwingWorker<ExtensionDownloadPlan, Void> {
        private final ExtensionCatalogEntry entry;
        private final String versionId;
        private final Runnable afterQueued;
        private final DownloadQueueItem preparationItem;

        private EnqueuePlanWorker(ExtensionCatalogEntry entry,
                                  String versionId,
                                  Runnable afterQueued,
                                  DownloadQueueItem preparationItem) {
            this.entry = entry;
            this.versionId = versionId;
            this.afterQueued = afterQueued;
            this.preparationItem = preparationItem;
        }

        @Override
        protected ExtensionDownloadPlan doInBackground() throws Exception {
            return resolveDownloadPlanCached(entry.providerId(), entry.projectId(), versionId);
        }

        @Override
        protected void done() {
            try {
                if (preparationItem != null && !queueContains(preparationItem)) {
                    return;
                }
                ExtensionDownloadPlan plan = get();
                if (plan == null || !plan.ready()) {
                    failQueuePreparation(preparationItem, "No se ha podido preparar una descarga compatible.");
                    showUserError("No se ha podido preparar una descarga compatible.");
                    return;
                }
                if (selectedEntry != null && entryKey(selectedEntry).equals(entryKey(entry))) {
                    selectedDownloadPlan = plan;
                    selectedInstallResolution = evaluateInstallResolution(plan);
                }
                if (preparationItem != null) {
                    preparationItem.downloadPlan = plan;
                }
                new DependencyResolutionWorker(entry, plan, false, afterQueued, preparationItem).execute();
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "No se ha podido preparar la extensión para la cola.", ex);
                failQueuePreparation(preparationItem, friendlyPreviewError(ex));
                showUserError(friendlyPreviewError(ex));
            } finally {
                refreshSelectionActionState();
            }
        }
    }

    private final class DependencyResolutionWorker extends SwingWorker<DependencyResolutionResult, Void> {
        private final ExtensionCatalogEntry entry;
        private final ExtensionDownloadPlan plan;
        private final boolean immediate;
        private final Runnable afterQueued;
        private final DownloadQueueItem preparationItem;

        private DependencyResolutionWorker(ExtensionCatalogEntry entry,
                                           ExtensionDownloadPlan plan,
                                           boolean immediate,
                                           Runnable afterQueued,
                                           DownloadQueueItem preparationItem) {
            this.entry = entry;
            this.plan = plan;
            this.immediate = immediate;
            this.afterQueued = afterQueued;
            this.preparationItem = preparationItem;
        }

        @Override
        protected DependencyResolutionResult doInBackground() {
            return resolveDependenciesForPlan(plan);
        }

        @Override
        protected void done() {
            try {
                enqueueEntryWithResolvedDependencies(entry, plan, get(), immediate, afterQueued, preparationItem);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "No se han podido resolver las dependencias de la extensión.", ex);
                failQueuePreparation(preparationItem, "No se han podido resolver las dependencias necesarias: " + rootMessage(ex));
                showUserError("No se han podido resolver las dependencias necesarias: " + rootMessage(ex));
            } finally {
                refreshSelectionActionState();
            }
        }
    }

    private final class UninstallExtensionWorker extends SwingWorker<Boolean, Void> {
        private final ServerExtension installed;

        private UninstallExtensionWorker(ServerExtension installed) {
            this.installed = installed;
        }

        @Override
        protected Boolean doInBackground() throws Exception {
            return gestorServidores.eliminarExtensionLocal(server, installed);
        }

        @Override
        protected void done() {
            try {
                if (get()) {
                    invalidateInstallResolutionCache();
                    selectedInstallResolution = selectedEntry == null ? null : evaluateInstallResolution(selectedEntry);
                    refreshResultDecorationsAsync();
                    notifyInstallCompletedSafely();
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "No se ha podido desinstalar la extensión.", ex);
                showUserError("No se ha podido desinstalar la extensión.");
            } finally {
                refreshSelectionActionState();
            }
        }
    }

    private final class InstalledSyncWorker extends SwingWorker<Void, Void> {
        private final long requestId;

        private InstalledSyncWorker(long requestId) {
            this.requestId = requestId;
        }

        @Override
        protected Void doInBackground() throws Exception {
            gestorServidores.sincronizarExtensionesInstaladas(server);
            gestorServidores.buscarActualizacionesExtensiones(server);
            return null;
        }

        @Override
        protected void done() {
            if (requestId != installedSyncSequence || !isDisplayable()) {
                return;
            }
            try {
                get();
                invalidateInstallResolutionCache();
                syncState = new QueueViewState(ViewState.READY, capitalize(extensionPluralLower()) + " instalados sincronizados.");
                notifyInstallCompletedSafely();
                setQueueFeedback("Sincronización completada.", "La información local del marketplace vuelve a estar al día.");
                refreshResultDecorationsAsync();
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Fallo al sincronizar extensiones instaladas.", ex);
                syncState = new QueueViewState(ViewState.ERROR, friendlySyncError(ex));
                setQueueFeedback("No se pudo sincronizar.", friendlySyncRecoveryHint(ex));
            } finally {
                refreshQueueControls();
            }
        }
    }

    private final class QueueProcessingWorker extends SwingWorker<Void, QueueProgress> {
        private final List<DownloadQueueItem> pendingItems;
        private boolean installedAny;

        private QueueProcessingWorker(List<DownloadQueueItem> pendingItems) {
            this.pendingItems = pendingItems == null ? List.of() : List.copyOf(pendingItems);
        }

        @Override
        protected Void doInBackground() {
            Set<String> completedKeys = new LinkedHashSet<>();
            Set<String> failedKeys = new LinkedHashSet<>();
            for (DownloadQueueItem next : pendingItems) {
                if (next == null) {
                    continue;
                }
                publish(QueueProgress.starting(next));
                try {
                    String dependencyWarning = dependencyWarningMessage(next, completedKeys, failedKeys);
                    if (dependencyWarning != null) {
                        publish(QueueProgress.warning(next, next.downloadPlan, dependencyWarning));
                    }
                    ExtensionDownloadPlan plan = resolveQueueDownloadPlan(next);
                    gestorServidores.instalarExtensionExterna(server, plan);
                    invalidateInstallResolutionCache();
                    installedAny = true;
                    if (next.queueKey() != null) {
                        completedKeys.add(next.queueKey());
                    }
                    publish(QueueProgress.completed(next, plan, dependencyWarning));
                } catch (Exception ex) {
                    if (next.queueKey() != null) {
                        failedKeys.add(next.queueKey());
                    }
                    publish(QueueProgress.failed(next, next.downloadPlan, friendlyQueueFailureMessage(rootMessage(ex))));
                }
            }
            return null;
        }

        @Override
        protected void process(List<QueueProgress> chunks) {
            for (QueueProgress progress : chunks) {
                if (progress == null || progress.item() == null) {
                    continue;
                }
                DownloadQueueItem item = progress.item();
                if (progress.phase() == QueueProgressPhase.STARTING) {
                    queueState = new QueueViewState(ViewState.LOADING, "Procesando cola de descargas...");
                    clearQueueFeedback();
                    item.state = QueueState.DOWNLOADING;
                    item.message = "Descargando archivos y preparando instalación...";
                    setQueueFeedback("Instalando " + item.displayName + "...", "Se está descargando y validando la extensión seleccionada.");
                } else if (progress.phase() == QueueProgressPhase.WARNING) {
                    if (progress.plan() != null) {
                        item.downloadPlan = progress.plan();
                    }
                    item.message = progress.message();
                    setQueueFeedback("Instalando con aviso.", progress.message());
                } else if (progress.phase() == QueueProgressPhase.COMPLETED) {
                    if (progress.plan() != null) {
                        item.downloadPlan = progress.plan();
                    }
                    item.state = QueueState.COMPLETED;
                    item.message = progress.message() == null
                            ? "Instalada correctamente en este servidor"
                            : "Instalada correctamente. " + progress.message();
                    if (selectedEntry != null && item.matchesProject(selectedEntry.providerId(), selectedEntry.projectId())) {
                        selectedInstallResolution = selectedDownloadPlan != null
                                ? evaluateInstallResolution(selectedDownloadPlan)
                                : evaluateInstallResolution(selectedEntry);
                        previewState = new PreviewViewState(ViewState.BLOCKED, "Instalada correctamente en este servidor.", item.versionId, previewRequestSequence);
                    }
                    setQueueFeedback(progress.message() == null ? "Instalación completada." : "Instalación completada con aviso.",
                            item.displayName + " ya está disponible en el servidor.");
                } else if (progress.phase() == QueueProgressPhase.FAILED) {
                    if (progress.plan() != null) {
                        item.downloadPlan = progress.plan();
                    }
                    item.state = QueueState.FAILED;
                    item.message = progress.message();
                    if (selectedEntry != null && item.matchesProject(selectedEntry.providerId(), selectedEntry.projectId())) {
                        previewState = new PreviewViewState(ViewState.ERROR, "No se ha podido instalar: " + item.message, item.versionId, previewRequestSequence);
                    }
                    setQueueFeedback("No se pudo completar la instalación.", item.message);
                }
                repaintQueueLists();
                refreshQueueControls();
                refreshSelectionActionState();
            }
        }

        @Override
        protected void done() {
            try {
                get();
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "La cola de instalaciones se ha interrumpido.", ex);
                setQueueFeedback("No se pudo completar la cola.", friendlyQueueFailureMessage(rootMessage(ex)));
            } finally {
                queueProcessingActive = false;
                queueState = new QueueViewState(ViewState.READY, "");
                if (installedAny) {
                    if (selectedEntry != null) {
                        selectedInstallResolution = selectedDownloadPlan != null
                                ? evaluateInstallResolution(selectedDownloadPlan)
                                : evaluateInstallResolution(selectedEntry);
                    }
                    notifyInstallCompletedSafely();
                }
                repaintQueueLists();
                refreshQueueControls();
                refreshResultDecorationsAsync();
                refreshSelectionActionState();
            }
        }
    }

    @FunctionalInterface
    private interface DocumentChangeHandler {
        void onChange();
    }

    private static final class SimpleDocumentListener implements DocumentListener {
        private final DocumentChangeHandler handler;

        private SimpleDocumentListener(DocumentChangeHandler handler) {
            this.handler = handler;
        }

        static SimpleDocumentListener of(DocumentChangeHandler handler) {
            return new SimpleDocumentListener(handler);
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            handler.onChange();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            handler.onChange();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            handler.onChange();
        }
    }
}
