package vista;

import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.extras.components.FlatScrollPane;
import controlador.extensions.ExtensionCompatibilityReport;
import controlador.extensions.ExtensionCompatibilityStatus;
import controlador.GestorConfiguracion;
import controlador.GestorServidores;
import controlador.extensions.CurseForgeModpackService;
import controlador.extensions.ModrinthModpackService;
import controlador.extensions.InstalledExtensionStatus;
import modelo.Server;
import modelo.extensions.ExtensionLocalMetadata;
import modelo.extensions.ExtensionRemoteDependency;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ExtensionUpdateState;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PanelExtensiones extends JPanel {
    private static final String EMPTY_CARD = "empty";
    private static final String DETAILS_CARD = "details";

    private final GestorServidores gestorServidores;
    private final Server server;

    private final DefaultListModel<ServerExtension> extensionsModel = new DefaultListModel<>();
    private final JList<ServerExtension> extensionsList = new JList<>(extensionsModel);
    private final Map<String, InstalledExtensionStatus> statusCache = new HashMap<>();
    private final JLabel summaryLabel = new JLabel();
    private final JLabel directoriesLabel = new JLabel();
    private final JLabel detailsTitleLabel = new JLabel("Ninguna extensión seleccionada");
    private final JLabel detailsVersionLabel = new JLabel("-");
    private final JLabel detailsStatusBadgeLabel = new ExtensionDetailsLayout.StatusBadgeLabel("Sin selección");
    private final JLabel detailsSideLabel = new JLabel("-");
    private final JLabel detailsInstalledVersionLabel = new JLabel("-");
    private final JLabel detailsUpdateLabel = new JLabel("-");
    private final JLabel detailsSourceLabel = new JLabel("-");
    private final JLabel detailsRemoteVersionLabel = new JLabel("-");
    private final JLabel detailsFileLabel = new JLabel("-");
    private final JLabel detailsPathLabel = new JLabel("-");
    private final JLabel detailsLoadersLabel = new JLabel("-");
    private final JLabel detailsMinecraftLabel = new JLabel("-");
    private final JLabel detailsLicenseLabel = new JLabel("-");
    private final JLabel detailsLinksLabel = new JLabel("-");
    private final JLabel detailsMetadataFilesLabel = new JLabel("-");
    private final JLabel detailsProviderNameLabel = new JLabel("-");
    private final JLabel detailsDownloadsLabel = new JLabel("-");
    private final JTextPane descriptionArea = new JTextPane();
    private final JPanel detailsContentCards = new JPanel(new CardLayout());
    private final JLabel placeholderLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel heroIconLabel = new JLabel();
    private final JButton detailsActionButton = new FlatButton();
    private final JButton installButton = new FlatButton();
    private final JButton browseCatalogButton = new FlatButton();
    private final JButton exportPackButton = new FlatButton();
    private final JButton importPackButton = new FlatButton();
    private final JButton openDirectoryButton = new FlatButton();
    private final JButton refreshButton = new FlatButton();
    private final JButton viewModeButton = new FlatButton();
    private final CardPanel listCard;
    private final CardPanel detailsCard;
    private final JSplitPane splitPane;
    private ExtensionListViewMode viewMode = GestorConfiguracion.isExtensionesListaCompacta()
            ? ExtensionListViewMode.COMPACT
            : ExtensionListViewMode.DETAILED;
    private boolean loadingExtensions;
    private boolean mutatingExtensions;
    private boolean splitInitialized;
    private int extensionListHoverIndex = -1;

    PanelExtensiones(GestorServidores gestorServidores, Server server) {
        this.gestorServidores = gestorServidores;
        this.server = server;
        this.detailsTitleLabel.setText("Ningún " + extensionSingularLower() + " seleccionado");

        setLayout(new BorderLayout());
        setOpaque(false);

        listCard = new CardPanel(getSectionLabel());
        listCard.setBorder(BorderFactory.createEmptyBorder());
        detailsCard = new CardPanel("Detalles");
        detailsCard.setBorder(BorderFactory.createEmptyBorder());
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listCard, detailsCard);
        splitPane.setOpaque(false);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setResizeWeight(0.56d);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(10);
        listCard.setMinimumSize(new Dimension(0, 0));
        detailsCard.setMinimumSize(new Dimension(0, 0));
        add(splitPane, BorderLayout.CENTER);

        configurarPanelListado();
        configurarPanelDetalles();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                initializeSplitLayout();
            }

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                initializeSplitLayout();
                refreshLayout();
            }
        });
        SwingUtilities.invokeLater(this::initializeSplitLayout);
        recargarExtensiones();
    }

    void recargarExtensiones() {
        recargarExtensiones(true);
    }

    private void recargarExtensiones(boolean showLoadingState) {
        listCard.setTitle(getSectionLabel());

        List<Path> directories = gestorServidores.obtenerDirectoríosExtensiones(server);
        boolean supported = gestorServidores.admiteGestionDeExtensiones(server) && !directories.isEmpty();
        String selectedRelativePath = selectedRelativePath();
        statusCache.clear();

            if (!supported) {
            loadingExtensions = false;
            mutatingExtensions = false;
            updateActionState();
            extensionsModel.clear();
            summaryLabel.setText(unsupportedSummary());
            directoriesLabel.setText(unsupportedActionHint());
            mostrarPlaceholderDetalles("Sin gestión de " + extensionPluralLower());
            refreshLayout();
            return;
        }

        loadingExtensions = true;
        if (showLoadingState) {
            summaryLabel.setText("Sincronizando " + getSectionLabel().toLowerCase(Locale.ROOT) + "...");
            directoriesLabel.setText(managedDirectoryLabel() + ": " + formatDirectories(directories));
            mostrarPlaceholderDetalles("Cargando " + extensionPluralLower() + "...");
        }
        updateActionState();
        new ReloadExtensionsWorker(List.copyOf(directories), selectedRelativePath).execute();
    }

    private void configurarPanelListado() {
        refreshButton.setToolTipText("Refrescar " + extensionPluralLower());
        AppTheme.applyRefreshIconButtonStyle(refreshButton);
        refreshButton.addActionListener(e -> recargarExtensiones());
        listCard.getHeaderActionsPanel().add(refreshButton);

        AppTheme.applyHeaderIconButtonStyle(viewModeButton);
        viewModeButton.addActionListener(e -> toggleViewMode());
        updateViewModeButton();
        listCard.getHeaderActionsPanel().add(viewModeButton);

        AppTheme.configureRowIconActionButton(installButton, "Instalar " + extensionSingularLower() + " .jar", "doraicons/file.svg", 22, AppTheme::getForeground, false);
        installButton.addActionListener(e -> instalarExtensionManual());

        AppTheme.configureRowIconActionButton(browseCatalogButton, "Explorar catálogo de " + extensionPluralLower(), "doraicons/shop.svg", 44, AppTheme::getMainAccent, true);
        browseCatalogButton.addActionListener(e -> abrirMarketplaceExtensiones());

        AppTheme.configureRowIconActionButton(importPackButton, "Importar pack", "doraicons/download.svg", 22, AppTheme::getForeground, false);
        importPackButton.addActionListener(e -> importarModpack());

        AppTheme.configureRowIconActionButton(exportPackButton, "Exportar pack", "doraicons/upload.svg", 22, AppTheme::getForeground, false);
        exportPackButton.addActionListener(e -> exportarModpack());

        AppTheme.configureRowIconActionButton(openDirectoryButton, "Abrir carpeta de " + extensionPluralLower(), "doraicons/folder.svg", 22, AppTheme::getForeground, false);
        configureFolderHover(openDirectoryButton);
        openDirectoryButton.addActionListener(e -> abrirCarpetaExtensiones());

        listCard.getActionsPanel().add(openDirectoryButton);
        listCard.getActionsPanel().add(importPackButton);
        listCard.getActionsPanel().add(exportPackButton);
        listCard.getActionsPanel().add(installButton);
        listCard.getActionsPanel().add(browseCatalogButton);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        listCard.getContentPanel().add(content, BorderLayout.CENTER);

        JPanel infoPanel = new JPanel();
        infoPanel.setOpaque(false);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD, 15f));
        directoriesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        directoriesLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 180));
        infoPanel.add(summaryLabel);
        infoPanel.add(Box.createVerticalStrut(6));
        infoPanel.add(directoriesLabel);
        content.add(infoPanel, BorderLayout.NORTH);

        extensionsList.setCellRenderer(new ExtensionCellRenderer());
        extensionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        extensionsList.setVisibleRowCount(12);
        extensionsList.setFixedCellHeight(viewMode == ExtensionListViewMode.COMPACT ? 42 : -1);
        extensionsList.setOpaque(true);
        extensionsList.setBackground(AppTheme.getPanelBackground());
        extensionsList.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        extensionsList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        extensionsList.addListSelectionListener(this::onExtensionSelected);
        extensionsList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int index = extensionsList.locationToIndex(e.getPoint());
                if (index != extensionListHoverIndex) {
                    int previous = extensionListHoverIndex;
                    extensionListHoverIndex = index;
                    repaintExtensionRows(previous, index);
                }
            }
        });
        extensionsList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                if (extensionListHoverIndex >= 0) {
                    int previous = extensionListHoverIndex;
                    extensionListHoverIndex = -1;
                    repaintExtensionRows(previous, -1);
                }
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int index = extensionsList.locationToIndex(e.getPoint());
                if (index < 0 || index >= extensionsModel.size()) {
                    return;
                }
                Rectangle bounds = extensionsList.getCellBounds(index, index);
                int actionWidth = viewMode == ExtensionListViewMode.COMPACT ? 42 : 52;
                if (bounds != null && index == extensionListHoverIndex && e.getX() >= bounds.x + bounds.width - actionWidth) {
                    extensionsList.setSelectedIndex(index);
                    eliminarExtensionSeleccionada();
                }
            }
        });

        FlatScrollPane scroll = new FlatScrollPane();
        scroll.setBorder(null);
        scroll.setOpaque(true);
        scroll.setBackground(AppTheme.getPanelBackground());
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(AppTheme.getPanelBackground());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setViewportView(extensionsList);
        content.add(scroll, BorderLayout.CENTER);
    }

    private void configureFolderHover(AbstractButton button) {
        if (button == null) {
            return;
        }
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (!button.isEnabled()) {
                    return;
                }
                button.setIcon(SvgIconFactory.create("doraicons/folder-open.svg", 22, 22, AppTheme::getForeground));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setIcon(SvgIconFactory.create("doraicons/folder.svg", 22, 22, AppTheme::getForeground));
            }
        });
    }

    private void repaintExtensionRows(int firstIndex, int secondIndex) {
        Rectangle repaintBounds = null;
        if (firstIndex >= 0 && firstIndex < extensionsModel.getSize()) {
            repaintBounds = extensionsList.getCellBounds(firstIndex, firstIndex);
        }
        if (secondIndex >= 0 && secondIndex < extensionsModel.getSize()) {
            Rectangle secondBounds = extensionsList.getCellBounds(secondIndex, secondIndex);
            repaintBounds = repaintBounds == null ? secondBounds : repaintBounds.union(secondBounds);
        }
        if (repaintBounds != null) {
            extensionsList.repaint(repaintBounds);
        }
    }

    private void configurarPanelDetalles() {
        placeholderLabel.setFont(placeholderLabel.getFont().deriveFont(Font.BOLD, 20f));
        placeholderLabel.setForeground(AppTheme.getMutedForeground());

        JPanel emptyPanel = new JPanel(new GridBagLayout());
        emptyPanel.setOpaque(false);
        emptyPanel.add(placeholderLabel);

        heroIconLabel.setIcon(SvgIconFactory.create("doraicons/box-unselected.svg", 52, 52));
        ExtensionDetailsLayout.configureStatusBadge(detailsStatusBadgeLabel);
        AppTheme.applyHeaderIconButtonStyle(detailsActionButton);
        detailsActionButton.setPreferredSize(new Dimension(56, 56));
        detailsActionButton.setMinimumSize(new Dimension(56, 56));
        detailsActionButton.setToolTipText("Eliminar " + extensionSingularLower());
        detailsActionButton.addActionListener(e -> eliminarExtensionSeleccionada());
        JPanel detailsPanel = new ExtensionDetailsLayout(
                heroIconLabel,
                detailsTitleLabel,
                detailsVersionLabel,
                detailsStatusBadgeLabel,
                detailsActionButton,
                descriptionArea,
                buildDetailsSidePanel(),
                List.of(
                        buildCatalogParitySection(),
                        buildInstalledMetadataSection()
                ),
                List.of(),
                this::repaint
        );

        detailsContentCards.setOpaque(false);
        detailsContentCards.add(emptyPanel, EMPTY_CARD);
        detailsContentCards.add(detailsPanel, DETAILS_CARD);
        detailsCard.getContentPanel().add(detailsContentCards, BorderLayout.CENTER);
        mostrarPlaceholderDetalles("Selecciona " + articleForExtension() + " " + extensionSingularLower());
    }

    private JPanel buildDetailsSidePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel(getSideSectionTitle());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 225));
        panel.add(title, BorderLayout.NORTH);
        panel.add(detailsSideLabel, BorderLayout.CENTER);
        detailsSideLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 215));
        detailsSideLabel.setVerticalAlignment(SwingConstants.TOP);
        return panel;
    }

    private JPanel buildCatalogParitySection() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(ExtensionDetailsLayout.buildInfoRow("Plataforma", detailsSourceLabel));
        panel.add(Box.createVerticalStrut(8));
        panel.add(ExtensionDetailsLayout.buildInfoRowWithDivider("Proveedor", detailsProviderNameLabel, detailsDownloadsLabel));
        panel.add(Box.createVerticalStrut(8));
        panel.add(ExtensionDetailsLayout.buildInfoRow("Proyecto", detailsLinksLabel));
        panel.add(Box.createVerticalStrut(8));
        panel.add(ExtensionDetailsLayout.buildInfoRow("Licencia", detailsLicenseLabel));
        return panel;
    }

    private JPanel buildInstalledMetadataSection() {
        return ExtensionDetailsLayout.buildInfoGrid(List.of(
                ExtensionDetailsLayout.buildInfoRow("Versión instalada", detailsInstalledVersionLabel),
                ExtensionDetailsLayout.buildInfoRow("Versión remota", detailsRemoteVersionLabel),
                ExtensionDetailsLayout.buildInfoRow("Actualización", detailsUpdateLabel),
                ExtensionDetailsLayout.buildInfoRow("Archivo", detailsFileLabel),
                ExtensionDetailsLayout.buildInfoRow("Ruta", detailsPathLabel),
                ExtensionDetailsLayout.buildInfoRow(getMetadataPlatformLabel(), detailsLoadersLabel),
                ExtensionDetailsLayout.buildInfoRow("Minecraft", detailsMinecraftLabel),
                ExtensionDetailsLayout.buildInfoRow("Metadata local", detailsMetadataFilesLabel)
        ));
    }

    private void updateDetailsStatusBadge(ServerExtension extension, InstalledExtensionStatus status) {
        VisualStatus visualStatus = visualStatus(status);
        detailsStatusBadgeLabel.setText(visualStatus == VisualStatus.OK
                ? resolveExtensionName(extension) + " está correctamente instalada en este servidor."
                : status == null ? "Desconocido" : status.summary());
        detailsStatusBadgeLabel.setToolTipText(buildStatusTooltip(status));
        ExtensionDetailsLayout.applyStatusBadgeStyle(
                detailsStatusBadgeLabel,
                statusForeground(visualStatus),
                statusBackground(visualStatus),
                AppTheme.withAlpha(statusForeground(visualStatus), 70)
        );
    }

    private void toggleViewMode() {
        viewMode = viewMode == ExtensionListViewMode.DETAILED
                ? ExtensionListViewMode.COMPACT
                : ExtensionListViewMode.DETAILED;
        GestorConfiguracion.guardarExtensionesListaCompacta(viewMode == ExtensionListViewMode.COMPACT);
        updateViewModeButton();
        applyListViewMode();
    }

    private void updateViewModeButton() {
        boolean compact = viewMode == ExtensionListViewMode.COMPACT;
        SvgIconFactory.apply(
                viewModeButton,
                compact ? "doraicons/maximize.svg" : "doraicons/minimize.svg",
                18,
                18,
                AppTheme::getForeground
        );
        viewModeButton.setToolTipText(compact ? "Vista compacta activa" : "Vista detallada activa");
    }

    private void applyListViewMode() {
        extensionsList.setCellRenderer(new ExtensionCellRenderer());
        extensionsList.setFixedCellHeight(viewMode == ExtensionListViewMode.COMPACT ? 42 : -1);
        extensionsList.revalidate();
        extensionsList.repaint();
        refreshLayout();
    }

    private void initializeSplitLayout() {
        if (splitInitialized || splitPane.getWidth() <= 0) {
            return;
        }
        splitInitialized = true;
        splitPane.setDividerLocation(0.56d);
        splitPane.revalidate();
        splitPane.repaint();
    }

    private void refreshLayout() {
        revalidate();
        repaint();
        listCard.revalidate();
        listCard.repaint();
        detailsCard.revalidate();
        detailsCard.repaint();
        splitPane.revalidate();
        splitPane.repaint();
    }

    private void onExtensionSelected(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) {
            return;
        }
        ServerExtension extension = extensionsList.getSelectedValue();
        updateActionState();
        if (extension == null) {
            mostrarPlaceholderDetalles("Selecciona " + articleForExtension() + " " + extensionSingularLower());
            return;
        }
        mostrarDetalles(extension);
    }

    private void mostrarDetalles(ServerExtension extension) {
        if (extension == null) {
            mostrarPlaceholderDetalles("Selecciona " + articleForExtension() + " " + extensionSingularLower());
            return;
        }

        InstalledExtensionStatus status = assessInstalledStatus(extension);
        heroIconLabel.setIcon(resolveExtensionIcon(extension, 52));
        detailsTitleLabel.setText(resolveExtensionName(extension));
        detailsVersionLabel.setText("Versión " + resolveVersion(extension) + "  |  " + resolveAuthor(extension));
        detailsActionButton.setIcon(SvgIconFactory.create("doraicons/trash-unselected.svg", 48, 48, AppTheme::getForeground));
        detailsActionButton.setEnabled(!loadingExtensions && !mutatingExtensions);
        updateDetailsStatusBadge(extension, status);
        detailsSideLabel.setText("<html>" + escapeHtml(sideText(extension)) + "</html>");
        detailsInstalledVersionLabel.setText("<html>" + escapeHtml(resolveVersion(extension)) + "</html>");
        detailsUpdateLabel.setText(resolveUpdateState(extension));
        detailsSourceLabel.setText("<html>" + escapeHtml(resolvePlatformDetails(extension)) + "</html>");
        detailsProviderNameLabel.setText(describeSource(extension.getSource()));
        detailsDownloadsLabel.setText(resolveDownloads(extension));
        detailsRemoteVersionLabel.setText(resolveRemoteVersion(extension));
        detailsFileLabel.setText(resolveFileName(extension));
        detailsPathLabel.setText(resolveRelativePath(extension.getLocalMetadata()));
        detailsLoadersLabel.setText("<html>" + escapeHtml(joinMetadata(extension.getLocalMetadata() == null ? null : extension.getLocalMetadata().getSupportedLoaders(), "-")) + "</html>");
        detailsMinecraftLabel.setText("<html>" + escapeHtml(resolveMinecraftMetadata(extension)) + "</html>");
        detailsLicenseLabel.setText("<html>" + escapeHtml(resolveLicense(extension)) + "</html>");
        detailsLinksLabel.setText("<html>" + escapeHtml(resolveLinks(extension)) + "</html>");
        detailsMetadataFilesLabel.setText("<html>" + escapeHtml(joinMetadata(extension.getLocalMetadata() == null ? null : extension.getLocalMetadata().getEmbeddedMetadataFiles(), "-")) + "</html>");
        setDescriptionText(baseDescription(extension));

        ((CardLayout) detailsContentCards.getLayout()).show(detailsContentCards, DETAILS_CARD);
        refreshLayout();
    }

    private void mostrarPlaceholderDetalles(String text) {
        placeholderLabel.setText(text);
        ((CardLayout) detailsContentCards.getLayout()).show(detailsContentCards, EMPTY_CARD);
        refreshLayout();
    }

    private void instalarExtensionManual() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(titleForManualInstall());
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos JAR", "jar"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selected = chooser.getSelectedFile();
        if (selected == null) {
            return;
        }

        if (EventQueue.isDispatchThread()) {
            mutatingExtensions = true;
            summaryLabel.setText("Validando " + extensionSingularLower() + " manual...");
            directoriesLabel.setText(selected.getName());
            updateActionState();
            new ManualInstallWorker(selected.toPath(), false).execute();
            return;
        }

        try {
            ExtensionCompatibilityReport compatibility = gestorServidores.validarCompatibilidadExtension(server, selected.toPath());
            if (compatibility.incompatible()) {
                JOptionPane.showMessageDialog(
                        this,
                        compatibility.summary(),
                        "Instalación incompatible",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }
            if (compatibility.warning()) {
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        compatibility.summary() + "\n\n¿Quieres instalar " + articleForExtension() + " " + extensionSingularLower() + " de todas formas?",
                        "Compatibilidad dudosa",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            mutatingExtensions = true;
            summaryLabel.setText("Instalando " + extensionSingularLower() + " manual...");
            directoriesLabel.setText(selected.getName());
            updateActionState();
            new ManualInstallWorker(selected.toPath()).execute();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "No se ha podido validar " + articleForExtension() + " " + extensionSingularLower() + ": " + ex.getMessage(),
                    getSectionLabel(),
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void eliminarExtensionSeleccionada() {
        ServerExtension extension = extensionsList.getSelectedValue();
        if (extension == null) {
            return;
        }

        List<String> dependents = findDependentExtensions(extension);
        int confirm = JOptionPane.showConfirmDialog(
                this,
                buildRemoveConfirmationMessage(extension, dependents),
                "Eliminar " + extensionSingularLower(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        mutatingExtensions = true;
        summaryLabel.setText("Eliminando " + extensionSingularLower() + "...");
        directoriesLabel.setText(resolveExtensionName(extension));
        updateActionState();
        new RemoveExtensionWorker(extension).execute();
    }

    private String buildRemoveConfirmationMessage(ServerExtension extension, List<String> dependents) {
        String extensionName = resolveExtensionName(extension);
        if (dependents == null || dependents.isEmpty()) {
            return "¿Seguro que quieres eliminar '" + extensionName + "' de este servidor?";
        }
        return "Hay " + getSectionLabel().toLowerCase(Locale.ROOT) + " que necesitan " + extensionName + " para funcionar:\n\n- "
                + String.join("\n- ", dependents)
                + "\n\n¿Quieres eliminarlo igualmente?";
    }

    private List<String> findDependentExtensions(ServerExtension dependencyCandidate) {
        if (dependencyCandidate == null || server == null || server.getExtensions() == null) {
            return List.of();
        }
        List<String> dependents = new ArrayList<>();
        for (ServerExtension installed : server.getExtensions()) {
            if (installed == null || installed == dependencyCandidate || isSameInstalledExtension(installed, dependencyCandidate)) {
                continue;
            }
            if (dependsOn(installed, dependencyCandidate)) {
                dependents.add(resolveExtensionName(installed));
            }
        }
        return dependents.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private boolean dependsOn(ServerExtension installed, ServerExtension dependencyCandidate) {
        ExtensionLocalMetadata metadata = installed == null ? null : installed.getLocalMetadata();
        if (metadata == null) {
            return false;
        }
        if (metadata.getDependencies() != null) {
            for (ExtensionRemoteDependency dependency : metadata.getDependencies()) {
                if (matchesDependency(dependency, dependencyCandidate)) {
                    return true;
                }
            }
        }
        if (metadata.getLocalDependencyDescriptions() != null) {
            String dependencyName = normalizeDependencyText(resolveExtensionName(dependencyCandidate));
            String dependencyFile = normalizeDependencyText(resolveFileName(dependencyCandidate));
            for (String description : metadata.getLocalDependencyDescriptions()) {
                String normalized = normalizeDependencyText(description);
                if (normalized != null && (normalized.equals(dependencyName)
                        || dependencyName != null && normalized.contains(dependencyName)
                        || dependencyFile != null && normalized.contains(dependencyFile))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesDependency(ExtensionRemoteDependency dependency, ServerExtension dependencyCandidate) {
        if (dependency == null || dependencyCandidate == null) {
            return false;
        }
        ExtensionSource source = dependencyCandidate.getSource();
        if (source != null
                && isSameText(source.getProvider(), dependency.getProviderId())
                && isSameText(source.getProjectId(), dependency.getProjectId())) {
            return true;
        }
        return isSameText(resolveExtensionName(dependencyCandidate), dependency.getDisplayName())
                || isSameText(resolveExtensionName(dependencyCandidate), dependency.getProjectId());
    }

    private boolean isSameInstalledExtension(ServerExtension left, ServerExtension right) {
        if (left == null || right == null) {
            return false;
        }
        ExtensionLocalMetadata leftMetadata = left.getLocalMetadata();
        ExtensionLocalMetadata rightMetadata = right.getLocalMetadata();
        return isSameText(left.getId(), right.getId())
                || isSameText(leftMetadata == null ? null : leftMetadata.getRelativePath(), rightMetadata == null ? null : rightMetadata.getRelativePath())
                || isSameText(resolveFileName(left), resolveFileName(right));
    }

    private boolean isSameText(String left, String right) {
        return left != null && right != null && !left.isBlank() && !right.isBlank()
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private String normalizeDependencyText(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(".jar", "");
    }

    private void abrirCarpetaExtensiones() {
        List<Path> directories = gestorServidores.obtenerDirectoríosExtensiones(server);
        if (directories.isEmpty()) {
            return;
        }
        try {
            Desktop.getDesktop().open(directories.getFirst().toFile());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "No se ha podido abrir la carpeta de " + extensionPluralLower() + ": " + ex.getMessage(),
                    getSectionLabel(),
                    JOptionPane.ERROR_MESSAGE
            );
        } catch (UnsupportedOperationException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Este sistema no soporta abrir carpetas desde Java.",
                    getSectionLabel(),
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private ServerEcosystemType currentEcosystem() {
        return ecosystemOf(server);
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

    static ServerExtensionType extensionTypeForEcosystem(ServerEcosystemType ecosystemType) {
        return switch (ecosystemType == null ? ServerEcosystemType.UNKNOWN : ecosystemType) {
            case MODS -> ServerExtensionType.MOD;
            case PLUGINS -> ServerExtensionType.PLUGIN;
            default -> ServerExtensionType.UNKNOWN;
        };
    }

    static boolean supportsModpackActions(ServerEcosystemType ecosystemType) {
        return (ecosystemType == null ? ServerEcosystemType.UNKNOWN : ecosystemType) == ServerEcosystemType.MODS;
    }

    private String getSectionLabel() {
        return switch (currentEcosystem()) {
            case MODS -> "Mods";
            case PLUGINS -> "Plugins";
            default -> "Extensiones";
        };
    }

    private String getSideSectionTitle() {
        return switch (currentEcosystem()) {
            case MODS -> "Lado del mod";
            case PLUGINS -> "Entorno del plugin";
            default -> "Lado de la extensión";
        };
    }

    private String getMetadataPlatformLabel() {
        return switch (currentEcosystem()) {
            case MODS -> "Loaders";
            case PLUGINS -> "Plataformas";
            default -> "Compatibilidad";
        };
    }

    private String titleForManualInstall() {
        return switch (currentEcosystem()) {
            case MODS -> "Selecciona un mod (.jar)";
            case PLUGINS -> "Selecciona un plugin (.jar)";
            default -> "Selecciona una extensión (.jar)";
        };
    }

    private String extensionSingularLower() {
        return switch (currentEcosystem()) {
            case MODS -> "mod";
            case PLUGINS -> "plugin";
            default -> "extensión";
        };
    }

    private String extensionPluralLower() {
        return switch (currentEcosystem()) {
            case MODS -> "mods";
            case PLUGINS -> "plugins";
            default -> "extensiones";
        };
    }

    private String articleForExtension() {
        return switch (currentEcosystem()) {
            case MODS, PLUGINS -> "un";
            default -> "una";
        };
    }

    private String managedDirectoryLabel() {
        return switch (currentEcosystem()) {
            case MODS -> "Carpeta de mods";
            case PLUGINS -> "Carpeta de plugins";
            default -> "Carpeta de extensiones";
        };
    }

    private String unsupportedSummary() {
        return currentEcosystem() == ServerEcosystemType.NONE
                ? "Este servidor Vanilla no admite extensiones gestionadas."
                : "Este servidor no usa una plataforma con extensiones gestionables.";
    }

    private String unsupportedActionHint() {
        boolean convertible = gestorServidores != null && gestorServidores.puedeConvertirseAPlataformaCompatible(server);
        if (convertible) {
            return "Convierte el servidor a Forge, Fabric, Paper u otra plataforma compatible para gestionar extensiones.";
        }
        return "No hay carpeta de mods o plugins disponible para este servidor.";
    }

    private String formatDirectories(List<Path> directories) {
        return directories.stream()
                .map(path -> path == null ? "" : path.getFileName().toString())
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
    }

    private String resolveExtensionName(ServerExtension extension) {
        if (extension == null || extension.getDisplayName() == null || extension.getDisplayName().isBlank()) {
            return "Extensión";
        }
        return extension.getDisplayName();
    }

    private String resolveVersion(ServerExtension extension) {
        String version = firstUsableText(
                extension == null ? null : extension.getVersion(),
                extension == null || extension.getLocalMetadata() == null ? null : extension.getLocalMetadata().getInstalledVersion()
        );
        return version == null ? "sin versión" : version;
    }

    private String resolveAuthor(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        if (metadata != null && metadata.getAuthors() != null && !metadata.getAuthors().isEmpty()) {
            return String.join(", ", metadata.getAuthors().stream()
                    .filter(author -> author != null && !author.isBlank())
                    .limit(3)
                    .toList());
        }
        ExtensionSource source = extension == null ? null : extension.getSource();
        String author = source == null ? null : source.getAuthor();
        return author == null || author.isBlank() ? "Autor desconocido" : author;
    }

    private String firstUsableText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (isUsableMetadataText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isUsableMetadataText(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.startsWith("${") && normalized.endsWith("}")) {
            return false;
        }
        return !normalized.contains(".jarVersion");
    }

    private String baseDescription(ServerExtension extension) {
        if (extension == null || extension.getDescription() == null || extension.getDescription().isBlank()) {
            return "Sin descripción disponible para esta extensión.";
        }
        return extension.getDescription();
    }

    private String resolveFileName(ServerExtension extension) {
        if (extension == null || extension.getFileName() == null || extension.getFileName().isBlank()) {
            return "-";
        }
        return extension.getFileName();
    }

    private String resolveRelativePath(ExtensionLocalMetadata metadata) {
        if (metadata == null || metadata.getRelativePath() == null || metadata.getRelativePath().isBlank()) {
            return "-";
        }
        return metadata.getRelativePath();
    }

    private String resolveDownloads(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        Long downloads = metadata == null ? null : metadata.getDownloadCount();
        if (downloads == null || downloads <= 0L) {
            return "Descargas no disponibles";
        }
        return String.format(Locale.ROOT, "%,d descargas", downloads);
    }

    private String resolvePlatformDetails(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        String loaders = joinMetadata(metadata == null ? null : metadata.getSupportedLoaders(), null);
        ServerPlatform platform = extension == null || extension.getPlatform() == null
                ? ServerPlatform.UNKNOWN
                : extension.getPlatform();
        String platformText = platform == ServerPlatform.UNKNOWN ? null : platform.name();
        return firstUsableText(loaders, platformText, "-");
    }

    private void setDescriptionText(String text) {
        descriptionArea.setText(text == null || text.isBlank() ? "Sin descripción disponible." : text);
        descriptionArea.setCaretPosition(0);
    }

    private String resolveMinecraftMetadata(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        String versions = joinMetadata(metadata == null ? null : metadata.getSupportedMinecraftVersions(), null);
        return firstUsableText(versions, metadata == null ? null : metadata.getMinecraftVersionConstraint(), "-");
    }

    private String resolveLicense(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        ExtensionSource source = extension == null ? null : extension.getSource();
        return firstUsableText(
                metadata == null ? null : metadata.getLicenseName(),
                source == null ? null : source.getLicenseName(),
                "-"
        );
    }

    private String resolveLinks(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        ExtensionSource source = extension == null ? null : extension.getSource();
        List<String> links = new ArrayList<>();
        addLink(links, "Web", firstUsableText(metadata == null ? null : metadata.getWebsiteUrl(), source == null ? null : source.getWebsiteUrl()));
        addLink(links, "Proyecto", source == null ? null : source.getProjectUrl());
        addLink(links, "Issues", firstUsableText(metadata == null ? null : metadata.getIssuesUrl(), source == null ? null : source.getIssuesUrl()));
        return links.isEmpty() ? "-" : String.join(" | ", links);
    }

    private void addLink(List<String> links, String label, String url) {
        if (links == null || label == null || label.isBlank() || url == null || url.isBlank()) {
            return;
        }
        links.add(label + ": " + url);
    }

    private String joinMetadata(List<String> values, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        String joined = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(6)
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
        return joined == null || joined.isBlank() ? fallback : joined;
    }

    private String describeSource(ExtensionSource source) {
        if (source == null || source.getType() == null) {
            return "Desconocido";
        }
        return switch (source.getType()) {
            case MANUAL -> "Instalación manual";
            case LOCAL_FILE -> "Archivo local";
            case MODRINTH -> "Modrinth";
            case CURSEFORGE -> "CurseForge";
            case HANGAR -> "Hangar";
            case BUILTIN -> "Incluido";
            default -> "Desconocido";
        };
    }

    private void abrirMarketplaceExtensiones() {
        if (!gestorServidores.admiteGestionDeExtensiones(server)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Este servidor no admite una experiencia de catálogo para " + extensionPluralLower() + ".",
                    getSectionLabel(),
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        ExtensionMarketplaceDialog.showDialog(this, gestorServidores, server, this::recargarExtensiones);
    }

    private void exportarModpack() {
        if (!supportsModpackActions(currentEcosystem())) {
            JOptionPane.showMessageDialog(this, "La exportación de modpacks solo está disponible para servidores de mods.", "Mods", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Object selected = JOptionPane.showInputDialog(
                this,
                "Selecciona el contenido del modpack a exportar:",
                "Exportar modpack",
                JOptionPane.QUESTION_MESSAGE,
                null,
                new Object[]{"Servidor", "Cliente", "Completo"},
                "Completo"
        );
        if (selected == null) {
            return;
        }

        ModrinthModpackService.ExportMode mode = switch (selected.toString()) {
            case "Servidor" -> ModrinthModpackService.ExportMode.SERVER;
            case "Cliente" -> ModrinthModpackService.ExportMode.CLIENT;
            default -> ModrinthModpackService.ExportMode.COMPLETE;
        };

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exportar modpack Modrinth");
        chooser.setSelectedFile(new File(safeServerNameForFile() + ".mrpack"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Modpack Modrinth (.mrpack)", "mrpack"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path target = chooser.getSelectedFile().toPath();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mrpack")) {
            target = target.resolveSibling(target.getFileName() + ".mrpack");
        }

        mutatingExtensions = true;
        summaryLabel.setText("Exportando modpack...");
        directoriesLabel.setText(target.getFileName().toString());
        updateActionState();

        Path exportTarget = target;
        new ExportModpackWorker(exportTarget, mode).execute();
    }

    private void importarModpack() {
        if (!supportsModpackActions(currentEcosystem())) {
            JOptionPane.showMessageDialog(this, "La importación de modpacks solo está disponible para servidores de mods.", "Mods", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Importar modpack");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Modpacks Modrinth o CurseForge", "mrpack", "zip"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path sourceZip = chooser.getSelectedFile().toPath();
        boolean modrinthPack = sourceZip.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mrpack");
        ModpackPreflightWorker worker = new ModpackPreflightWorker(sourceZip, modrinthPack);
        JDialog loadingDialog = createModpackLoadingDialog(
                "Preparando modpack",
                "Preparando modpack...",
                sourceZip.getFileName().toString(),
                "Leyendo metadata y revisando los mods actuales."
        );
        worker.setLoadingDialog(loadingDialog);
        worker.execute();
        loadingDialog.setVisible(true);
    }

    private void startModpackImport(ModpackImportWizardResult wizardResult) {
        if (wizardResult == null) {
            return;
        }
        mutatingExtensions = true;
        summaryLabel.setText("Importando modpack...");
        directoriesLabel.setText(wizardResult.sourceZip().getFileName().toString());
        updateActionState();
        ImportModpackWorker worker = new ImportModpackWorker(wizardResult);
        JDialog loadingDialog = createModpackLoadingDialog(
                "Importando modpack",
                "Importando modpack...",
                wizardResult.sourceZip().getFileName().toString(),
                "Descargando y verificando archivos."
        );
        worker.setLoadingDialog(loadingDialog);
        worker.execute();
        loadingDialog.setVisible(true);
    }

    private ModpackImportWizardState loadModpackImportWizardState(Path sourceZip, boolean modrinthPack) throws IOException {
        ModpackImportWizardState state = new ModpackImportWizardState(sourceZip, modrinthPack);
        if (modrinthPack) {
            state.index = gestorServidores.leerIndiceModpackModrinth(sourceZip);
            state.importOptions = new ModrinthModpackService.ImportOptions(ModrinthModpackService.ImportMode.COMPLETE);
        } else {
            state.manifest = gestorServidores.leerManifestModpackCurseForge(sourceZip);
        }
        state.existingMods = gestorServidores.sincronizarExtensionesInstaladas(server).stream()
                .filter(extension -> extension != null)
                .filter(extension -> extension.getExtensionType() != ServerExtensionType.PLUGIN)
                .count();

        return state;
    }

    private ModpackImportWizardResult showModpackImportWizard(ModpackImportWizardState state) {
        JLabel reviewLabel = new JLabel();
        Runnable refreshReview = () -> reviewLabel.setText(buildModpackImportReviewHtml(state));
        List<ProcessWizardDialog.Step> steps = new ArrayList<>();
        steps.add(ProcessWizardDialog.Step.of(
                "content",
                "Contenido del modpack",
                createModpackContentStep(state, refreshReview)
        ));
        refreshReview.run();
        steps.add(ProcessWizardDialog.Step.of("review", "Revisar importación", createModpackReviewStep(reviewLabel)));

        boolean accepted = ProcessWizardDialog.show(
                SwingUtilities.getWindowAncestor(this),
                "Importar modpack",
                steps,
                new ProcessWizardDialog.Options(
                        new Dimension(720, 460),
                        "Importar modpack",
                        () -> true,
                        ignored -> refreshReview.run()
                )
        );
        if (!accepted) {
            return null;
        }
        return new ModpackImportWizardResult(
                state.sourceZip,
                state.modrinthPack,
                state.index,
                state.manifest,
                state.importOptions,
                state.conflictPolicy
        );
    }

    private JComponent createModpackContentStep(ModpackImportWizardState state, Runnable refreshReview) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel modpackContent = new JPanel(new BorderLayout(0, 12));
        int fileCount = modpackFileCount(state);
        JLabel intro = new JLabel("<html><b>" + escapeHtml(modpackName(state)) + "</b><br>"
                + fileCount + (fileCount == 1 ? " archivo detectado." : " archivos detectados.") + "</html>");
        modpackContent.add(intro, BorderLayout.NORTH);

        if (!state.modrinthPack) {
            JLabel note = new JLabel("<html>Formato CurseForge. Dora resolverá las descargas usando la API key configurada.</html>");
            note.setForeground(AppTheme.getMutedForeground());
            modpackContent.add(note, BorderLayout.CENTER);
            panel.add(modpackContent);
            addExistingModsSection(panel, state, refreshReview);
            return panel;
        }

        ModrinthImportModeOption serverOption = new ModrinthImportModeOption(
                ModrinthModpackService.ImportMode.SERVER,
                "Servidor",
                "Instalar solo archivos declarados para servidor."
        );
        ModrinthImportModeOption clientOption = new ModrinthImportModeOption(
                ModrinthModpackService.ImportMode.CLIENT,
                "Cliente",
                "Instalar solo archivos declarados para cliente."
        );
        ModrinthImportModeOption completeOption = new ModrinthImportModeOption(
                ModrinthModpackService.ImportMode.COMPLETE,
                "Completo",
                "Instalar archivos de cliente y servidor."
        );
        JComboBox<ModrinthImportModeOption> modeCombo = new JComboBox<>(new ModrinthImportModeOption[]{
                serverOption,
                clientOption,
                completeOption
        });
        modeCombo.setSelectedItem(completeOption);
        modeCombo.addActionListener(e -> {
            ModrinthImportModeOption selected = (ModrinthImportModeOption) modeCombo.getSelectedItem();
            state.importOptions = new ModrinthModpackService.ImportOptions(
                    selected == null ? ModrinthModpackService.ImportMode.COMPLETE : selected.mode()
            );
            refreshReview.run();
        });

        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.insets = new Insets(0, 0, 8, 8);
        fields.add(new JLabel("Contenido:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1d;
        fields.add(modeCombo, gbc);
        modpackContent.add(fields, BorderLayout.CENTER);

        JLabel note = new JLabel("<html>Se importará todo el contenido de la selección salvo archivos marcados como no compatibles.</html>");
        note.setForeground(AppTheme.getMutedForeground());
        modpackContent.add(note, BorderLayout.SOUTH);

        panel.add(modpackContent);
        addExistingModsSection(panel, state, refreshReview);
        return panel;
    }

    private void addExistingModsSection(JPanel parent, ModpackImportWizardState state, Runnable refreshReview) {
        if (state.existingMods <= 0) {
            return;
        }
        parent.add(Box.createVerticalStrut(18));
        parent.add(createModpackExistingModsStep(state, refreshReview));
    }

    private JComponent createModpackExistingModsStep(ModpackImportWizardState state, Runnable refreshReview) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.getSubtleBorderColor()));
        JLabel intro = new JLabel("<html>Este servidor ya tiene <b>" + state.existingMods + "</b>"
                + (state.existingMods == 1 ? " mod instalado." : " mods instalados.")
                + "<br>Elige qué hacer con ellos antes de instalar el modpack.</html>");
        intro.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        panel.add(intro, BorderLayout.NORTH);

        JRadioButton keepButton = new JRadioButton("Mantener mods actuales y añadir solo los que falten", true);
        JRadioButton replaceButton = new JRadioButton("Eliminar mods actuales e importar el modpack");
        ButtonGroup group = new ButtonGroup();
        group.add(keepButton);
        group.add(replaceButton);

        JPanel choices = new JPanel();
        choices.setLayout(new BoxLayout(choices, BoxLayout.Y_AXIS));
        choices.add(keepButton);
        choices.add(Box.createVerticalStrut(8));
        choices.add(replaceButton);
        panel.add(choices, BorderLayout.CENTER);

        JLabel note = new JLabel("<html>Reemplazar eliminará solo los .jar actuales de las carpetas de mods gestionadas antes de importar.</html>");
        note.setForeground(AppTheme.getMutedForeground());
        panel.add(note, BorderLayout.SOUTH);

        keepButton.addActionListener(e -> {
            state.conflictPolicy = GestorServidores.ModpackImportConflictPolicy.KEEP_EXISTING;
            refreshReview.run();
        });
        replaceButton.addActionListener(e -> {
            state.conflictPolicy = GestorServidores.ModpackImportConflictPolicy.REPLACE_EXISTING;
            refreshReview.run();
        });
        return panel;
    }

    private JComponent createModpackReviewStep(JLabel reviewLabel) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.add(reviewLabel, BorderLayout.CENTER);
        JLabel note = new JLabel("Al avanzar comenzará la descarga, verificación e instalación.");
        note.setForeground(AppTheme.getMutedForeground());
        panel.add(note, BorderLayout.SOUTH);
        return panel;
    }

    private String buildModpackImportReviewHtml(ModpackImportWizardState state) {
        return "<html><b>" + escapeHtml(modpackName(state)) + "</b><br>"
                + "Formato: " + (state.modrinthPack ? "Modrinth .mrpack" : "CurseForge ZIP") + "<br>"
                + "Archivos: " + modpackFileCount(state) + "<br>"
                + escapeHtml(describeImportOptions(state.importOptions)).replace("\n", "<br>") + "<br>"
                + escapeHtml(describeModpackConflictPolicy(state.conflictPolicy)).replace("\n", "<br>")
                + "</html>";
    }

    private String modpackName(ModpackImportWizardState state) {
        if (state == null) {
            return "Modpack";
        }
        if (state.modrinthPack && state.index != null) {
            return firstUsableText(state.index.name(), state.sourceZip.getFileName().toString());
        }
        if (!state.modrinthPack && state.manifest != null) {
            return firstUsableText(state.manifest.name(), state.sourceZip.getFileName().toString());
        }
        return state.sourceZip.getFileName().toString();
    }

    private int modpackFileCount(ModpackImportWizardState state) {
        if (state == null) {
            return 0;
        }
        if (state.modrinthPack && state.index != null) {
            return state.index.files().size();
        }
        if (!state.modrinthPack && state.manifest != null) {
            return state.manifest.files().size();
        }
        return 0;
    }

    private String describeImportOptions(ModrinthModpackService.ImportOptions options) {
        ModrinthModpackService.ImportMode mode = options == null || options.mode() == null
                ? ModrinthModpackService.ImportMode.COMPLETE
                : options.mode();
        String content = switch (mode) {
            case CLIENT -> "Contenido seleccionado: cliente.";
            case COMPLETE -> "Contenido seleccionado: completo.";
            case SERVER -> "Contenido seleccionado: servidor.";
        };
        return content + "\nSe incluirá todo lo compatible con esa selección.";
    }

    private String describeModpackConflictPolicy(GestorServidores.ModpackImportConflictPolicy conflictPolicy) {
        if (conflictPolicy == GestorServidores.ModpackImportConflictPolicy.REPLACE_EXISTING) {
            return "Los mods actuales se eliminarán de las carpetas gestionadas antes de importar el modpack.";
        }
        return "Se mantendrán los mods actuales y se omitirán los que ya existan.";
    }

    private JDialog createModpackLoadingDialog(String dialogTitle, String titleText, String fileName, String fallbackDetail) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, dialogTitle, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JLabel title = new JLabel(titleText == null || titleText.isBlank() ? "Procesando modpack..." : titleText);
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 1f));
        JLabel detail = new JLabel(fileName == null || fileName.isBlank() ? fallbackDetail : fileName);
        detail.setForeground(AppTheme.getMutedForeground());
        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(title);
        text.add(Box.createVerticalStrut(6));
        text.add(detail);
        text.add(Box.createVerticalStrut(6));
        JLabel wait = new JLabel("Por favor espera. La ventana se cerrará al terminar.");
        wait.setForeground(AppTheme.getMutedForeground());
        text.add(wait);
        panel.add(text, BorderLayout.CENTER);
        panel.add(AppTheme.createLoadingProgressBar(true), BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(360, dialog.getHeight()));
        dialog.setLocationRelativeTo(this);
        return dialog;
    }

    private void updateActionState() {
        List<Path> directories = gestorServidores.obtenerDirectoríosExtensiones(server);
        boolean supported = gestorServidores.admiteGestionDeExtensiones(server) && !directories.isEmpty();
        boolean busy = loadingExtensions || mutatingExtensions;
        boolean modsServer = supportsModpackActions(currentEcosystem());
        refreshButton.setToolTipText("Refrescar " + extensionPluralLower());
        installButton.setToolTipText("Instalar " + extensionSingularLower() + " .jar");
        browseCatalogButton.setToolTipText("Explorar catálogo de " + extensionPluralLower());
        openDirectoryButton.setToolTipText("Abrir carpeta de " + extensionPluralLower());
        detailsActionButton.setToolTipText("Eliminar " + extensionSingularLower());
        installButton.setEnabled(supported && !busy);
        browseCatalogButton.setEnabled(supported && !busy);
        importPackButton.setVisible(modsServer);
        exportPackButton.setVisible(modsServer);
        importPackButton.setEnabled(supported && modsServer && !busy);
        exportPackButton.setEnabled(supported && modsServer && !busy);
        openDirectoryButton.setEnabled(supported && !busy);
        detailsActionButton.setEnabled(supported && !busy && extensionsList.getSelectedValue() != null);
        extensionsList.setEnabled(!busy);
    }

    private String safeServerNameForFile() {
        String name = server.getDisplayName();
        if (name == null || name.isBlank()) {
            return "modpack";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]+", "_").trim();
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

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? error : current;
    }

    private String resolveRemoteVersion(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        if (metadata == null || metadata.getKnownRemoteVersion() == null || metadata.getKnownRemoteVersion().isBlank()) {
            return "-";
        }
        return metadata.getKnownRemoteVersion();
    }

    private javax.swing.Icon resolveExtensionIcon(ServerExtension extension, int size) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        String localIconUrl = metadata == null ? null : metadata.getLocalIconUrl();
        if (localIconUrl != null && !localIconUrl.isBlank()) {
            return ExtensionIconLoader.getIcon(localIconUrl, size, this::repaint);
        }
        ExtensionSource source = extension == null ? null : extension.getSource();
        String iconUrl = source == null ? null : source.getIconUrl();
        return ExtensionIconLoader.getIcon(iconUrl, size, this::repaint);
    }

    private String resolveUpdateState(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        ExtensionUpdateState updateState = metadata == null ? null : metadata.getUpdateState();
        String message = metadata == null ? null : metadata.getUpdateMessage();
        String base = switch (updateState == null ? ExtensionUpdateState.UNKNOWN : updateState) {
            case UNTRACKED -> "Sin seguimiento remoto";
            case UP_TO_DATE -> "Al dia";
            case UPDATE_AVAILABLE -> "Actualización disponible";
            case UNKNOWN -> "Estado remoto desconocido";
        };
        if (message == null || message.isBlank()) {
            return base;
        }
        return base + " - " + message;
    }

    private ExtensionCompatibilityReport describeCompatibility(ServerExtension extension) {
        return gestorServidores.validarCompatibilidadExtension(server, extension);
    }

    private InstalledExtensionStatus assessInstalledStatus(ServerExtension extension) {
        if (extension == null) {
            return gestorServidores.evaluarEstadoExtensionInstalada(server, null);
        }
        return statusCache.computeIfAbsent(statusCacheKey(extension), ignored ->
                gestorServidores.evaluarEstadoExtensionInstalada(server, extension));
    }

    private VisualStatus resolveVisualStatus(ServerExtension extension) {
        return visualStatus(assessInstalledStatus(extension));
    }

    private String statusCacheKey(ServerExtension extension) {
        if (extension == null) {
            return "-";
        }
        ExtensionLocalMetadata metadata = extension.getLocalMetadata();
        return firstUsableText(
                metadata == null ? null : metadata.getRelativePath(),
                metadata == null ? null : metadata.getSha256(),
                extension.getId(),
                extension.getFileName(),
                resolveExtensionName(extension)
        );
    }

    private VisualStatus visualStatus(InstalledExtensionStatus status) {
        ExtensionCompatibilityStatus severity = status == null ? ExtensionCompatibilityStatus.WARNING : status.severity();
        if (severity == ExtensionCompatibilityStatus.INCOMPATIBLE) {
            return VisualStatus.INCOMPATIBLE;
        }
        if (severity == ExtensionCompatibilityStatus.WARNING) {
            return VisualStatus.WARNING;
        }
        return VisualStatus.OK;
    }

    private Color statusForeground(VisualStatus status) {
        return ExtensionStatusPresentation.foreground(toCompatibilityStatus(status));
    }

    private Color statusBackground(VisualStatus status) {
        return ExtensionStatusPresentation.background(toCompatibilityStatus(status));
    }

    private ExtensionCompatibilityStatus toCompatibilityStatus(VisualStatus status) {
        return switch (status == null ? VisualStatus.WARNING : status) {
            case OK -> ExtensionCompatibilityStatus.COMPATIBLE;
            case WARNING -> ExtensionCompatibilityStatus.WARNING;
            case INCOMPATIBLE -> ExtensionCompatibilityStatus.INCOMPATIBLE;
        };
    }

    private String buildStatusTooltip(InstalledExtensionStatus status) {
        if (status == null) {
            return "Estado desconocido";
        }
        List<String> details = status.diagnostics();
        if (details.isEmpty()) {
            return status.summary();
        }
        StringBuilder tooltip = new StringBuilder("<html><body style='margin:0;padding:0'>")
                .append(escapeHtml(status.summary()));
        details.stream()
                .filter(detail -> detail != null && !detail.isBlank())
                .distinct()
                .limit(6)
                .forEach(detail -> tooltip.append("<br>- ").append(escapeHtml(detail)));
        tooltip.append("</body></html>");
        return tooltip.toString();
    }

    private boolean extensionFileExists(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        if (metadata == null || metadata.getRelativePath() == null || metadata.getRelativePath().isBlank()) {
            return false;
        }
        try {
            Path resolved = Path.of(server.getServerDir()).resolve(metadata.getRelativePath().replace('/', File.separatorChar));
            return Files.isRegularFile(resolved);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean isBasicallyIncompatible(ServerExtension extension) {
        if (extension == null) {
            return false;
        }
        ServerEcosystemType ecosystemType = currentEcosystem();
        ServerExtensionType extensionType = extension.getExtensionType() == null ? ServerExtensionType.UNKNOWN : extension.getExtensionType();
        if (ecosystemType == ServerEcosystemType.MODS && extensionType == ServerExtensionType.PLUGIN) {
            return true;
        }
        if (ecosystemType == ServerEcosystemType.PLUGINS && extensionType == ServerExtensionType.MOD) {
            return true;
        }
        ServerPlatform extensionPlatform = extension.getPlatform() == null ? ServerPlatform.UNKNOWN : extension.getPlatform();
        ServerPlatform serverPlatform = server.getPlatform() == null ? ServerPlatform.UNKNOWN : server.getPlatform();
        return extensionPlatform != ServerPlatform.UNKNOWN
                && serverPlatform != ServerPlatform.UNKNOWN
                && extensionPlatform != serverPlatform;
    }

    private String selectedRelativePath() {
        ServerExtension selected = extensionsList.getSelectedValue();
        return selected == null || selected.getLocalMetadata() == null
                ? null
                : selected.getLocalMetadata().getRelativePath();
    }

    private void seleccionarExtensionPorRuta(String relativePath) {
        if (extensionsModel.isEmpty()) {
            mostrarPlaceholderDetalles("No hay " + getSectionLabel().toLowerCase(Locale.ROOT) + " instalados");
            return;
        }
        if (relativePath != null) {
            for (int i = 0; i < extensionsModel.size(); i++) {
                ServerExtension extension = extensionsModel.get(i);
                String candidatePath = extension == null || extension.getLocalMetadata() == null
                        ? null
                        : extension.getLocalMetadata().getRelativePath();
                if (relativePath.equals(candidatePath)) {
                    extensionsList.setSelectedIndex(i);
                    mostrarDetalles(extension);
                    return;
                }
            }
        }
        extensionsList.setSelectedIndex(0);
        mostrarDetalles(extensionsModel.getElementAt(0));
    }

    private enum VisualStatus {
        OK,
        WARNING,
        INCOMPATIBLE
    }

    private String escapeHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record ImportSummary(int installed, List<String> warnings) {
    }

    private record ModpackImportWizardResult(
            Path sourceZip,
            boolean modrinthPack,
            ModrinthModpackService.ImportIndex index,
            CurseForgeModpackService.ImportManifest manifest,
            ModrinthModpackService.ImportOptions importOptions,
            GestorServidores.ModpackImportConflictPolicy conflictPolicy
    ) {
    }

    private record ModrinthImportModeOption(ModrinthModpackService.ImportMode mode, String label, String description) {
        @Override
        public String toString() {
            return label + " - " + description;
        }
    }

    private static final class ModpackImportWizardState {
        private final Path sourceZip;
        private final boolean modrinthPack;
        private ModrinthModpackService.ImportIndex index;
        private CurseForgeModpackService.ImportManifest manifest;
        private ModrinthModpackService.ImportOptions importOptions =
                new ModrinthModpackService.ImportOptions(ModrinthModpackService.ImportMode.COMPLETE);
        private GestorServidores.ModpackImportConflictPolicy conflictPolicy =
                GestorServidores.ModpackImportConflictPolicy.KEEP_EXISTING;
        private long existingMods;

        private ModpackImportWizardState(Path sourceZip, boolean modrinthPack) {
            this.sourceZip = sourceZip;
            this.modrinthPack = modrinthPack;
        }
    }

    private record ReloadResult(List<ServerExtension> extensions, Map<String, InstalledExtensionStatus> statuses) {
    }

    private final class ReloadExtensionsWorker extends SwingWorker<ReloadResult, Void> {
        private final List<Path> directories;
        private final String selectedRelativePath;

        private ReloadExtensionsWorker(List<Path> directories, String selectedRelativePath) {
            this.directories = directories == null ? List.of() : List.copyOf(directories);
            this.selectedRelativePath = selectedRelativePath;
        }

        @Override
        protected ReloadResult doInBackground() throws Exception {
            List<ServerExtension> extensions = gestorServidores.sincronizarExtensionesInstaladas(server);
            Map<String, InstalledExtensionStatus> statuses = new HashMap<>();
            Map<ServerExtension, InstalledExtensionStatus> batchStatuses =
                    gestorServidores.evaluarEstadosExtensionesInstaladas(server, extensions);
            for (Map.Entry<ServerExtension, InstalledExtensionStatus> entry : batchStatuses.entrySet()) {
                statuses.put(statusCacheKey(entry.getKey()), entry.getValue());
            }
            return new ReloadResult(extensions, statuses);
        }

        @Override
        protected void done() {
            loadingExtensions = false;
            updateActionState();
            extensionsModel.clear();
            try {
                ReloadResult result = get();
                List<ServerExtension> extensions = result.extensions();
                statusCache.clear();
                statusCache.putAll(result.statuses());
                summaryLabel.setText(getSectionLabel() + " instalados: " + extensions.size());
                directoriesLabel.setText(managedDirectoryLabel() + ": " + formatDirectories(directories));

                extensionsModel.addAll(extensions);
                ExtensionIconLoader.prefetchIcons(
                        extensionsList,
                        extensions.stream()
                                .map(extension -> {
                                    ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
                                    if (metadata != null && metadata.getLocalIconUrl() != null && !metadata.getLocalIconUrl().isBlank()) {
                                        return metadata.getLocalIconUrl();
                                    }
                                    ExtensionSource source = extension == null ? null : extension.getSource();
                                    return source == null ? null : source.getIconUrl();
                                })
                                .filter(iconUrl -> iconUrl != null && !iconUrl.isBlank())
                                .toList(),
                        40,
                        () -> extensionsList.repaint(extensionsList.getVisibleRect())
                );

                if (extensions.isEmpty()) {
                    mostrarPlaceholderDetalles("No hay " + getSectionLabel().toLowerCase(Locale.ROOT) + " instalados");
                    refreshLayout();
                    return;
                }
                seleccionarExtensionPorRuta(selectedRelativePath);
                applyListViewMode();
            } catch (Exception ex) {
                summaryLabel.setText("No se han podido cargar las extensiones.");
                directoriesLabel.setText(rootMessage(ex));
                mostrarPlaceholderDetalles("Error al leer extensiones");
            }
            refreshLayout();
        }
    }

    private final class ManualInstallWorker extends SwingWorker<Void, Void> {
        private final Path selectedPath;
        private final boolean compatibilityAccepted;

        private ManualInstallWorker(Path selectedPath) {
            this(selectedPath, true);
        }

        private ManualInstallWorker(Path selectedPath, boolean compatibilityAccepted) {
            this.selectedPath = selectedPath;
            this.compatibilityAccepted = compatibilityAccepted;
        }

        @Override
        protected Void doInBackground() throws Exception {
            if (!compatibilityAccepted) {
                ExtensionCompatibilityReport compatibility = gestorServidores.validarCompatibilidadExtension(server, selectedPath);
                if (compatibility.incompatible()) {
                    throw new ManualInstallCompatibilityException(compatibility, false);
                }
                if (compatibility.warning()) {
                    throw new ManualInstallCompatibilityException(compatibility, true);
                }
            }
            gestorServidores.instalarExtensionManual(server, selectedPath);
            return null;
        }

        @Override
        protected void done() {
            mutatingExtensions = false;
            updateActionState();
            try {
                get();
                recargarExtensiones(false);
            } catch (Exception ex) {
                Throwable root = rootCause(ex);
                if (root instanceof ManualInstallCompatibilityException compatibilityException) {
                    handleManualInstallCompatibility(selectedPath, compatibilityException);
                    return;
                }
                summaryLabel.setText("No se ha podido instalar " + articleForExtension() + " " + extensionSingularLower() + ".");
                directoriesLabel.setText(rootMessage(ex));
                JOptionPane.showMessageDialog(
                        PanelExtensiones.this,
                        "No se ha podido instalar " + articleForExtension() + " " + extensionSingularLower() + ": " + rootMessage(ex),
                        getSectionLabel(),
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void handleManualInstallCompatibility(Path selectedPath, ManualInstallCompatibilityException exception) {
        ExtensionCompatibilityReport compatibility = exception.report();
        summaryLabel.setText("Instalación manual pendiente.");
        directoriesLabel.setText(selectedPath == null || selectedPath.getFileName() == null ? "-" : selectedPath.getFileName().toString());
        if (!exception.canOverride()) {
            JOptionPane.showMessageDialog(
                    this,
                    compatibility.summary(),
                    "Instalación incompatible",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                this,
                        compatibility.summary() + "\n\n¿Quieres instalar " + articleForExtension() + " " + extensionSingularLower() + " de todas formas?",
                "Compatibilidad dudosa",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        mutatingExtensions = true;
        summaryLabel.setText("Instalando " + extensionSingularLower() + " manual...");
        updateActionState();
        new ManualInstallWorker(selectedPath, true).execute();
    }

    private static final class ManualInstallCompatibilityException extends Exception {
        private final ExtensionCompatibilityReport report;
        private final boolean canOverride;

        private ManualInstallCompatibilityException(ExtensionCompatibilityReport report, boolean canOverride) {
            super(report == null ? "Compatibilidad no disponible." : report.summary());
            this.report = report;
            this.canOverride = canOverride;
        }

        private ExtensionCompatibilityReport report() {
            return report;
        }

        private boolean canOverride() {
            return canOverride;
        }
    }

    private final class RemoveExtensionWorker extends SwingWorker<Void, Void> {
        private final ServerExtension extension;

        private RemoveExtensionWorker(ServerExtension extension) {
            this.extension = extension;
        }

        @Override
        protected Void doInBackground() throws Exception {
            gestorServidores.eliminarExtensionLocal(server, extension);
            return null;
        }

        @Override
        protected void done() {
            mutatingExtensions = false;
            updateActionState();
            try {
                get();
                recargarExtensiones(false);
            } catch (Exception ex) {
                summaryLabel.setText("No se ha podido eliminar " + articleForExtension() + " " + extensionSingularLower() + ".");
                directoriesLabel.setText(rootMessage(ex));
                JOptionPane.showMessageDialog(
                        PanelExtensiones.this,
                        "No se ha podido eliminar " + articleForExtension() + " " + extensionSingularLower() + ": " + rootMessage(ex),
                        getSectionLabel(),
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private final class ExportModpackWorker extends SwingWorker<ModrinthModpackService.ExportResult, Void> {
        private final Path exportTarget;
        private final ModrinthModpackService.ExportMode mode;

        private ExportModpackWorker(Path exportTarget, ModrinthModpackService.ExportMode mode) {
            this.exportTarget = exportTarget;
            this.mode = mode;
        }

        @Override
        protected ModrinthModpackService.ExportResult doInBackground() throws Exception {
            return gestorServidores.exportarModpackModrinth(server, exportTarget, mode);
        }

        @Override
        protected void done() {
            mutatingExtensions = false;
            updateActionState();
            try {
                ModrinthModpackService.ExportResult result = get();
                summaryLabel.setText("Modpack exportado");
                directoriesLabel.setText(result.archivePath().getFileName().toString());
                StringBuilder message = new StringBuilder("Se ha exportado el modpack con ")
                        .append(result.exportedFiles())
                        .append(result.exportedFiles() == 1 ? " mod." : " mods.");
                if (result.overrideFiles() > 0) {
                    message.append("\nOverrides incluidos: ").append(result.overrideFiles()).append(".");
                }
                if (!result.skippedEntries().isEmpty()) {
                    message.append("\n\nNo se han podido incluir algunas entradas:\n- ")
                            .append(String.join("\n- ", result.skippedEntries()));
                }
                JOptionPane.showMessageDialog(PanelExtensiones.this, message.toString(), "Exportar modpack", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                summaryLabel.setText("No se ha podido exportar el modpack.");
                directoriesLabel.setText(rootMessage(ex));
                JOptionPane.showMessageDialog(PanelExtensiones.this, "No se ha podido exportar el modpack: " + rootMessage(ex), "Exportar modpack", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private final class ModpackPreflightWorker extends SwingWorker<ModpackImportWizardState, Void> {
        private final Path sourceZip;
        private final boolean modrinthPack;
        private JDialog loadingDialog;

        private ModpackPreflightWorker(Path sourceZip, boolean modrinthPack) {
            this.sourceZip = sourceZip;
            this.modrinthPack = modrinthPack;
        }

        private void setLoadingDialog(JDialog loadingDialog) {
            this.loadingDialog = loadingDialog;
        }

        @Override
        protected ModpackImportWizardState doInBackground() throws Exception {
            return loadModpackImportWizardState(sourceZip, modrinthPack);
        }

        @Override
        protected void done() {
            if (loadingDialog != null) {
                loadingDialog.dispose();
            }
            try {
                ModpackImportWizardResult wizardResult = showModpackImportWizard(get());
                startModpackImport(wizardResult);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        PanelExtensiones.this,
                        "No se ha podido leer el modpack: " + rootMessage(ex),
                        "Importar modpack",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private final class ImportModpackWorker extends SwingWorker<ImportSummary, Void> {
        private final ModpackImportWizardResult importResult;
        private JDialog loadingDialog;

        private ImportModpackWorker(ModpackImportWizardResult importResult) {
            this.importResult = importResult;
        }

        private void setLoadingDialog(JDialog loadingDialog) {
            this.loadingDialog = loadingDialog;
        }

        @Override
        protected ImportSummary doInBackground() throws Exception {
            List<String> warnings = new ArrayList<>();
            int installed = importResult.modrinthPack()
                    ? gestorServidores.importarModpackModrinth(
                    server,
                    importResult.sourceZip(),
                    importResult.index(),
                    importResult.importOptions(),
                    importResult.conflictPolicy(),
                    warnings,
                    true
            )
                    : gestorServidores.importarModpackCurseForge(
                    server,
                    importResult.sourceZip(),
                    importResult.manifest(),
                    importResult.conflictPolicy(),
                    warnings,
                    true
            );
            return new ImportSummary(installed, List.copyOf(warnings));
        }

        @Override
        protected void done() {
            if (loadingDialog != null) {
                loadingDialog.dispose();
            }
            mutatingExtensions = false;
            updateActionState();
            try {
                ImportSummary summary = get();
                StringBuilder message = new StringBuilder("Se han importado ")
                        .append(summary.installed())
                        .append(summary.installed() == 1 ? " mod." : " mods.");
                if (!summary.warnings().isEmpty()) {
                    message.append("\n\nAvisos:\n- ").append(String.join("\n- ", summary.warnings()));
                }
                JOptionPane.showMessageDialog(PanelExtensiones.this, message.toString(), "Importar modpack", JOptionPane.INFORMATION_MESSAGE);
                recargarExtensiones(false);
            } catch (Exception ex) {
                summaryLabel.setText("No se ha podido importar el modpack.");
                directoriesLabel.setText(rootMessage(ex));
                JOptionPane.showMessageDialog(PanelExtensiones.this, "No se ha podido importar el modpack: " + rootMessage(ex), "Importar modpack", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private final class ExtensionCellRenderer implements ListCellRenderer<ServerExtension> {
        private static final int DETAILED_ICON_SIZE = 52;
        private static final int DETAILED_ACTION_SIZE = 52;
        private static final int COMPACT_ICON_SIZE = 42;
        private static final int COMPACT_ACTION_SIZE = 42;

        @Override
        public Component getListCellRendererComponent(JList<? extends ServerExtension> list,
                                                      ServerExtension value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            InstalledExtensionStatus status = assessInstalledStatus(value);
            boolean compact = viewMode == ExtensionListViewMode.COMPACT;
            if (compact) {
                return createCompactRow(list, value, status, index, isSelected);
            }
            int cardHgap = 10;
            int contentHgap = 8;
            JPanel card = new JPanel(new BorderLayout(cardHgap, 0));
            card.setOpaque(true);
            card.setBorder(AppTheme.createRoundedBorder(
                    new Insets(0, 0, 0, 0),
                    isSelected ? AppTheme.getMainAccent() : AppTheme.getBorderColor(),
                    1f
            ));
            card.setBackground(isSelected ? AppTheme.getSoftSelectionBackground() : AppTheme.getPanelBackground());

            JLabel icon = new JLabel(resolveExtensionIcon(value, DETAILED_ICON_SIZE));
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            icon.setVerticalAlignment(SwingConstants.CENTER);
            icon.setOpaque(true);
            icon.setBackground(AppTheme.withAlpha(AppTheme.getForeground(), 12));
            icon.setPreferredSize(new Dimension(DETAILED_ICON_SIZE, DETAILED_ICON_SIZE));
            card.add(icon, BorderLayout.WEST);

            JPanel content = new JPanel(new BorderLayout(contentHgap, 0));
            content.setOpaque(false);

            JPanel textBlock = new JPanel(new BorderLayout(0, 2));
            textBlock.setOpaque(false);

            Font nameFont = list.getFont().deriveFont(Font.BOLD, 15.5f);
            Font metaFont = list.getFont().deriveFont(Font.PLAIN, 12.5f);
            String extensionName = resolveExtensionName(value);
            String titleMeta = extensionTypeLabel(value) + " / by " + resolveAuthor(value) + " / " + describeSource(value == null ? null : value.getSource());
            JLabel nameLabel = new JLabel(extensionName);
            nameLabel.setFont(nameFont);
            nameLabel.setToolTipText(resolveExtensionName(value));

            JLabel metaLabel = new JLabel(titleMeta);
            metaLabel.setFont(metaFont);
            metaLabel.setForeground(AppTheme.getMutedForeground());
            metaLabel.setToolTipText(titleMeta);
            JPanel titleRow = new ExtensionTitleRow(nameLabel, metaLabel, InstalledExtensionRowTextLayout.TITLE_GAP);
            textBlock.add(titleRow, BorderLayout.NORTH);

            String description = rowDescription(value, status);
            JLabel descriptionLabel = new JLabel(description);
            descriptionLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 210));
            descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(13.25f));
            constrainLabelWidth(descriptionLabel);
            descriptionLabel.setVerticalAlignment(SwingConstants.TOP);
            descriptionLabel.setToolTipText(description);
            textBlock.add(descriptionLabel, BorderLayout.CENTER);

            boolean hover = index == extensionListHoverIndex;

            content.add(textBlock, BorderLayout.CENTER);
            if (hover) {
                content.add(createRowActionIcon(DETAILED_ACTION_SIZE), BorderLayout.EAST);
            }
            card.add(content, BorderLayout.CENTER);
            card.setToolTipText(status == null ? null : status.summary());
            return card;
        }

        private Component createCompactRow(JList<? extends ServerExtension> list, ServerExtension value, InstalledExtensionStatus status, int index, boolean isSelected) {
            int rowHgap = 8;
            JPanel row = new JPanel(new BorderLayout(rowHgap, 0));
            row.setOpaque(true);
            row.setBackground(isSelected ? AppTheme.getSoftSelectionBackground() : AppTheme.getPanelBackground());
            row.setBorder(AppTheme.createRoundedBorder(
                    new Insets(0, 0, 0, 0),
                    isSelected ? AppTheme.getMainAccent() : AppTheme.getBorderColor(),
                    1f
            ));

            JLabel icon = new JLabel(resolveExtensionIcon(value, COMPACT_ICON_SIZE));
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            icon.setVerticalAlignment(SwingConstants.CENTER);
            icon.setPreferredSize(new Dimension(COMPACT_ICON_SIZE, COMPACT_ICON_SIZE));
            icon.setMinimumSize(new Dimension(COMPACT_ICON_SIZE, COMPACT_ICON_SIZE));
            row.add(icon, BorderLayout.WEST);

            JPanel titleRow = new JPanel();
            titleRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
            Font nameFont = list.getFont().deriveFont(Font.BOLD, 13.25f);
            Font metaFont = list.getFont().deriveFont(Font.PLAIN, 12.25f);
            String extensionName = resolveExtensionName(value);
            String titleMeta = extensionTypeLabel(value) + " / by " + resolveAuthor(value) + " / " + describeSource(value == null ? null : value.getSource());
            JLabel nameLabel = new JLabel(extensionName);
            nameLabel.setFont(nameFont);
            nameLabel.setForeground(AppTheme.getForeground());
            nameLabel.setToolTipText(resolveExtensionName(value));

            JLabel metaLabel = new JLabel(titleMeta);
            metaLabel.setFont(metaFont);
            metaLabel.setForeground(AppTheme.getMutedForeground());
            metaLabel.setToolTipText(titleMeta);
            titleRow = new ExtensionTitleRow(nameLabel, metaLabel, InstalledExtensionRowTextLayout.COMPACT_TITLE_GAP);
            titleRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));

            JPanel center = new JPanel(new GridBagLayout());
            center.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 1d;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.WEST;
            center.add(titleRow, gbc);
            row.add(center, BorderLayout.CENTER);
            boolean hover = index == extensionListHoverIndex;
            if (hover) {
                row.add(createRowActionIcon(COMPACT_ACTION_SIZE), BorderLayout.EAST);
            }
            row.setToolTipText(status == null ? null : status.summary());
            return row;
        }

        private void constrainLabelWidth(JLabel label) {
            Dimension preferred = label.getPreferredSize();
            int height = preferred == null ? 1 : Math.max(1, preferred.height);
            label.setMinimumSize(new Dimension(0, height));
            label.setPreferredSize(new Dimension(0, height));
        }

        private JLabel createRowActionIcon(int size) {
            JLabel label = new JLabel();
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setVerticalAlignment(SwingConstants.CENTER);
            label.setPreferredSize(new Dimension(size, size));
            label.setMinimumSize(new Dimension(size, size));
            label.setToolTipText("Eliminar " + extensionSingularLower());
            int iconSize = Math.max(30, Math.min(size - 8, 42));
            label.setIcon(SvgIconFactory.create("doraicons/trash-unselected.svg", iconSize, iconSize, AppTheme::getForeground));
            return label;
        }

    }

    private final class ExtensionTitleRow extends JPanel {
        private final JLabel leadingLabel;
        private final JLabel trailingLabel;
        private final int gap;

        private ExtensionTitleRow(JLabel leadingLabel, JLabel trailingLabel, int gap) {
            this.leadingLabel = leadingLabel;
            this.trailingLabel = trailingLabel;
            this.gap = Math.max(0, gap);
            setOpaque(false);
            setLayout(null);
            constrainLabel(leadingLabel);
            constrainLabel(trailingLabel);
            add(leadingLabel);
            add(trailingLabel);
        }

        @Override
        public void doLayout() {
            Insets insets = getInsets();
            int x = insets.left;
            int y = insets.top;
            int width = Math.max(0, getWidth() - insets.left - insets.right);
            int height = Math.max(0, getHeight() - insets.top - insets.bottom);
            int naturalLeading = leadingLabel.getFontMetrics(leadingLabel.getFont()).stringWidth(leadingLabel.getText());
            int naturalTrailing = trailingLabel.getFontMetrics(trailingLabel.getFont()).stringWidth(trailingLabel.getText());
            InstalledExtensionRowTextLayout.Allocation allocation = InstalledExtensionRowTextLayout.allocateTitleWidths(
                    width,
                    gap,
                    naturalLeading,
                    naturalTrailing
            );
            int leadingWidth = allocation.leadingWidth();
            int trailingWidth = allocation.trailingWidth();
            int actualGap = leadingWidth > 0 && trailingWidth > 0 ? Math.min(gap, Math.max(0, width - leadingWidth - trailingWidth)) : 0;
            leadingLabel.setBounds(x, y, leadingWidth, height);
            trailingLabel.setBounds(x + leadingWidth + actualGap, y, trailingWidth, height);
        }

        @Override
        public Dimension getPreferredSize() {
            Insets insets = getInsets();
            return new Dimension(0, preferredContentHeight() + insets.top + insets.bottom);
        }

        @Override
        public Dimension getMinimumSize() {
            Insets insets = getInsets();
            return new Dimension(0, preferredContentHeight() + insets.top + insets.bottom);
        }

        private int preferredContentHeight() {
            return Math.max(
                    leadingLabel.getPreferredSize().height,
                    trailingLabel.getPreferredSize().height
            );
        }

        private void constrainLabel(JLabel label) {
            Dimension preferred = label.getPreferredSize();
            int height = preferred == null ? 1 : Math.max(1, preferred.height);
            label.setMinimumSize(new Dimension(0, height));
            label.setPreferredSize(new Dimension(0, height));
        }
    }

    private String shortDescription(ServerExtension extension) {
        return baseDescription(extension).replace('\n', ' ').trim();
    }

    private String rowDescription(ServerExtension extension, InstalledExtensionStatus status) {
        String description = shortDescription(extension) + "  |  " + resolveFileName(extension);
        if (status != null && status.severity() == ExtensionCompatibilityStatus.INCOMPATIBLE) {
            description += "  |  Incompatible: " + status.summary();
        }
        return description;
    }

    private String extensionTypeLabel(ServerExtension extension) {
        ServerExtensionType extensionType = extension == null || extension.getExtensionType() == null
                ? ServerExtensionType.UNKNOWN
                : extension.getExtensionType();
        return extensionType.getDisplayName();
    }

    private String sideText(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        boolean supportsClient = sideSupported(metadata == null ? null : metadata.getClientSide());
        boolean supportsServer = sideSupported(metadata == null ? null : metadata.getServerSide());
        if (supportsClient && supportsServer) {
            return "Cliente + servidor";
        }
        if (supportsServer) {
            return "Servidor";
        }
        if (supportsClient) {
            return "Cliente";
        }
        return "Lado indefinido";
    }

    private String ellipsize(String text, Font font, int maxWidth) {
        String value = text == null || text.isBlank() ? "-" : text.trim();
        if (value.length() <= 1 || maxWidth <= 0) {
            return value;
        }
        return TextEllipsizer.rightStrict(value, getFontMetrics(font == null ? getFont() : font), maxWidth);
    }

    private Color neutralBadgeBackground() {
        return ExtensionStatusPresentation.neutralBadgeBackground();
    }

    private Color foregroundFor(Color background) {
        return ExtensionStatusPresentation.foregroundFor(background);
    }

    private boolean sideSupported(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("required") || normalized.equals("optional") || normalized.equals("supported");
    }

    private enum ExtensionListViewMode {
        DETAILED,
        COMPACT
    }
}
