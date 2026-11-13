/*
 * Fichero: AsistenteCreacionServidor.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Asistente modal para crear un servidor nuevo: plataforma, versión, carpeta de destino y nombre de la carpeta.
 * Solo recoge la elección del usuario; la instalación la hace GestorServidores.
 * */

package controlador;

import controlador.platform.ServerCreationOption;
import controlador.platform.ServerPlatformAdapter;
import controlador.platform.ServerPlatformAdapters;
import modelo.extensions.ServerPlatform;
import vista.AppTheme;
import vista.FolderPathLayout;
import vista.PlatformSelectorPanel;
import vista.ProcessWizardDialog;
import vista.SvgIconFactory;
import vista.TextEllipsizer;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class AsistenteCreacionServidor {
    private static final MojangAPI MOJANG_API = new MojangAPI();

    private AsistenteCreacionServidor() {
    }

    static ServerCreationWizardResult mostrar() {
        List<ServerPlatformAdapter> adapters = ServerPlatformAdapters.creatable();
        if (adapters.isEmpty()) {
            JOptionPane.showMessageDialog(
                    null,
                    "No hay plataformas disponibles para crear servidores.",
                    "Crear servidor",
                    JOptionPane.WARNING_MESSAGE
            );
            return null;
        }
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }

        ServerCreationWizardState state = new ServerCreationWizardState();
        Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();

        PlatformSelectorPanel platformSelector = new PlatformSelectorPanel(adapters, null, null);
        platformSelector.setPreferredSize(new Dimension(620, 330));

        DefaultListModel<ServerCreationOption> versionListModel = new DefaultListModel<>();
        JList<ServerCreationOption> versionList = new JList<>(versionListModel);
        versionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        versionList.setVisibleRowCount(12);
        versionList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> createWizardComboLabel(
                list,
                value == null ? "" : value.displayName(),
                isSelected
        ));
        JScrollPane versionScroll = new JScrollPane(versionList);
        versionScroll.setPreferredSize(new Dimension(620, 320));
        JCheckBox includeSnapshotsCheck = new JCheckBox("Snapshots", false);
        JCheckBox includeReleasesCheck = new JCheckBox("Releases", true);
        JCheckBox eulaCheck = new JCheckBox();
        JPanel versionFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        versionFilters.add(new JLabel("Incluir:"));
        versionFilters.add(includeSnapshotsCheck);
        versionFilters.add(includeReleasesCheck);
        JPanel eulaPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JLabel eulaLabel = new JLabel("Acepto el EULA de Mojang (https://aka.ms/MinecraftEULA)");
        Font eulaFont = eulaLabel.getFont();
        eulaLabel.setFont(eulaFont.deriveFont(Font.BOLD));
        eulaPanel.add(eulaLabel);
        eulaPanel.add(eulaCheck);
        JPanel versionControls = new JPanel(new BorderLayout(0, 0));
        versionControls.add(versionFilters, BorderLayout.WEST);
        versionControls.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.CENTER);
        versionControls.add(eulaPanel, BorderLayout.EAST);
        JPanel versionStep = new JPanel(new BorderLayout(0, 8));
        versionStep.add(versionScroll, BorderLayout.CENTER);
        versionStep.add(versionControls, BorderLayout.SOUTH);

        JTextField parentField = new JTextField(32);
        JFileChooser parentChooser = new JFileChooser();
        parentChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        parentChooser.setAcceptAllFileFilterUsed(false);
        parentChooser.setControlButtonsAreShown(false);
        parentChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f != null && f.isDirectory();
            }

            @Override
            public String getDescription() {
                return "Carpetas";
            }
        });
        parentChooser.setPreferredSize(new Dimension(620, 320));
        File initialParentDirectory = parentChooser.getCurrentDirectory();
        if (initialParentDirectory != null) {
            parentField.setText(initialParentDirectory.getAbsolutePath());
        }
        JLabel parentHint = new JLabel("Se creará una nueva carpeta dentro de la carpeta seleccionada.");
        JPanel parentStep = new JPanel(new BorderLayout(0, 8));
        parentStep.add(parentHint, BorderLayout.NORTH);
        parentStep.add(parentChooser, BorderLayout.CENTER);

        JTextField folderNameField = new JTextField(24);
        LeftEllipsisLabel parentPrefixLabel = new LeftEllipsisLabel();
        JButton resetFolderButton = new JButton();
        AppTheme.applyHeaderIconButtonStyle(resetFolderButton);
        resetFolderButton.setToolTipText("Restablecer nombre");
        resetFolderButton.setPreferredSize(new Dimension(40, 40));
        SvgIconFactory.RotatingIcon resetIcon = SvgIconFactory.createRotating(
                "doraicons/reset.svg",
                26,
                26,
                AppTheme::getForeground
        );
        resetFolderButton.setIcon(resetIcon);
        Font pathFont = new Font(Font.MONOSPACED, Font.PLAIN, 16);
        parentPrefixLabel.setFont(pathFont);
        folderNameField.setFont(pathFont);
        folderNameField.setHorizontalAlignment(JTextField.LEFT);
        FolderPathPanel folderPathPanel = new FolderPathPanel(parentPrefixLabel, folderNameField, resetFolderButton);
        Color folderNameBorderColor = UIManager.getColor("Component.borderColor") == null
                ? Color.GRAY
                : UIManager.getColor("Component.borderColor");
        folderPathPanel.setBorder(AppTheme.createRoundedBorder(new Insets(6, 8, 6, 6), folderNameBorderColor, 1f));
        folderPathPanel.setBackground(UIManager.getColor("TextField.background"));
        folderPathPanel.setOpaque(true);
        folderNameField.setOpaque(false);
        folderNameField.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        folderPathPanel.setPreferredSize(new Dimension(620, 42));
        JPanel folderNameWrap = new JPanel(new GridBagLayout());
        folderNameWrap.add(folderPathPanel, new GridBagConstraints());

        AtomicReference<ServerCreationWizardResult> result = new AtomicReference<>();
        AtomicReference<ProcessWizardDialog> wizardRef = new AtomicReference<>();
        AtomicInteger versionDownloadCheckGeneration = new AtomicInteger();
        Runnable[] refreshNextState = new Runnable[1];
        Runnable[] checkSelectedVersionDownload = new Runnable[1];
        AtomicReference<java.util.function.Consumer<AbstractButton>> refreshVersionFilters = new AtomicReference<>();
        Runnable refreshEulaState = () -> {
            boolean accepted = eulaCheck.isSelected();
            eulaLabel.setFont(eulaFont.deriveFont(accepted ? Font.PLAIN : Font.BOLD));
            if (refreshNextState[0] != null) {
                refreshNextState[0].run();
            }
        };
        refreshNextState[0] = () -> {
            ProcessWizardDialog wizard = wizardRef.get();
            if (wizard != null) {
                wizard.refresh();
            }
        };
        Runnable refreshNextStateLater = () -> {
            if (refreshNextState[0] != null) {
                SwingUtilities.invokeLater(refreshNextState[0]);
            }
        };
        checkSelectedVersionDownload[0] = () -> {
            ServerCreationOption selected = state.option;
            if (state.adapter == null
                    || state.adapter.getPlatform() != modelo.extensions.ServerPlatform.VANILLA
                    || selected == null
                    || selected.minecraftVersion() == null
                    || state.serverJarAvailableVersions.contains(selected.minecraftVersion())
                    || state.serverJarUnavailableVersions.contains(selected.minecraftVersion())) {
                state.pendingServerJarVersion = null;
                refreshNextState[0].run();
                return;
            }

            String versionId = selected.minecraftVersion();
            if (Objects.equals(state.pendingServerJarVersion, versionId)) {
                refreshNextState[0].run();
                return;
            }
            state.pendingServerJarVersion = versionId;
            int generation = versionDownloadCheckGeneration.incrementAndGet();
            refreshNextState[0].run();
            MojangAPI.runBackgroundRequest(() -> {
                boolean available;
                try {
                    String url = MOJANG_API.obtenerUrlServerJar(versionId);
                    available = url != null && !url.isBlank();
                } catch (RuntimeException ex) {
                    available = false;
                }
                boolean finalAvailable = available;
                SwingUtilities.invokeLater(() -> {
                    if (generation != versionDownloadCheckGeneration.get()) {
                        return;
                    }
                    if (finalAvailable) {
                        state.serverJarAvailableVersions.add(versionId);
                    } else {
                        state.serverJarUnavailableVersions.add(versionId);
                        actualizarListadoVersiones(state, versionListModel, versionList);
                    }
                    if (Objects.equals(state.pendingServerJarVersion, versionId)) {
                        state.pendingServerJarVersion = null;
                    }
                    refreshNextState[0].run();
                    if (!finalAvailable) {
                        SwingUtilities.invokeLater(checkSelectedVersionDownload[0]);
                    }
                });
            });
        };

        platformSelector.setSelectionListener(selected -> {
            if (selected != state.adapter) {
                state.adapter = selected;
                state.options = selected == null ? null : state.optionsByPlatform.get(selected.getPlatform());
                state.option = null;
                versionListModel.clear();
                boolean snapshotsDisponibles = soportaSnapshotsCreacion(selected);
                includeSnapshotsCheck.setEnabled(snapshotsDisponibles);
                if (!snapshotsDisponibles) {
                    includeSnapshotsCheck.setSelected(false);
                }
                state.includeSnapshots = snapshotsDisponibles && includeSnapshotsCheck.isSelected();
                state.includeReleases = includeReleasesCheck.isSelected();
                actualizarSugerenciaNombreCarpeta(state, folderNameField, true);
                refreshNextState[0].run();
            }
        });
        versionList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || state.updatingVersionList) {
                return;
            }
            ServerCreationOption selected = versionList.getSelectedValue();
            if (!Objects.equals(selected, state.option)) {
                state.option = selected;
                SwingUtilities.invokeLater(checkSelectedVersionDownload[0]);
            }
            refreshNextState[0].run();
        });
        refreshVersionFilters.set(changedCheck -> {
            if (state.updatingVersionFilters) {
                return;
            }
            state.updatingVersionFilters = true;
            try {
                if (!includeSnapshotsCheck.isSelected() && !includeReleasesCheck.isSelected()) {
                    changedCheck.setSelected(true);
                }
                state.includeSnapshots = includeSnapshotsCheck.isEnabled() && includeSnapshotsCheck.isSelected();
                state.includeReleases = includeReleasesCheck.isSelected();
                actualizarListadoVersiones(state, versionListModel, versionList);
            } finally {
                state.updatingVersionFilters = false;
            }
            refreshNextState[0].run();
            SwingUtilities.invokeLater(checkSelectedVersionDownload[0]);
        });
        includeSnapshotsCheck.addActionListener(e -> refreshVersionFilters.get().accept(includeSnapshotsCheck));
        includeReleasesCheck.addActionListener(e -> refreshVersionFilters.get().accept(includeReleasesCheck));
        parentField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                actualizarPadre();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                actualizarPadre();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                actualizarPadre();
            }

            private void actualizarPadre() {
                state.parentDirectory = parentField.getText() == null || parentField.getText().isBlank()
                        ? null
                        : new File(parentField.getText().trim());
                actualizarSugerenciaNombreCarpeta(state, folderNameField, false);
                refreshNextState[0].run();
            }
        });
        folderNameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                actualizarNombreCarpeta();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                actualizarNombreCarpeta();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                actualizarNombreCarpeta();
            }

            private void actualizarNombreCarpeta() {
                state.folderName = folderNameField.getText();
                state.folderNameEdited = !Objects.equals(normalizarNombreCarpeta(state.folderName), state.suggestedFolderName);
                folderPathPanel.revalidate();
                folderPathPanel.repaint();
                refreshNextStateLater.run();
            }
        });
        parentChooser.addPropertyChangeListener(evt -> {
            File selected = null;
            if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(evt.getPropertyName())
                    && evt.getNewValue() instanceof File selectedFile) {
                selected = selectedFile;
            } else if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(evt.getPropertyName())
                    && evt.getNewValue() instanceof File currentDirectory) {
                selected = currentDirectory;
            }
            if (selected == null) return;
            if (!selected.isDirectory()) {
                selected = selected.getParentFile();
            }
            if (selected != null && !Objects.equals(parentField.getText(), selected.getAbsolutePath())) {
                parentField.setText(selected.getAbsolutePath());
            }
        });
        resetFolderButton.addActionListener(e -> {
            folderNameField.setText(state.suggestedFolderName == null ? "" : state.suggestedFolderName);
            folderNameField.requestFocusInWindow();
            folderNameField.selectAll();
            animarIconoReset(resetIcon, resetFolderButton);
        });
        eulaCheck.addActionListener(e -> {
            state.eulaAccepted = eulaCheck.isSelected();
            refreshEulaState.run();
        });

        state.adapter = null;
        includeSnapshotsCheck.setEnabled(soportaSnapshotsCreacion(state.adapter));
        state.includeSnapshots = includeSnapshotsCheck.isSelected();
        state.includeReleases = includeReleasesCheck.isSelected();
        state.parentDirectory = initialParentDirectory == null ? null : initialParentDirectory.getAbsoluteFile();
        platformSelector.setSelectedAdapter(state.adapter);

        List<ProcessWizardDialog.Step> steps = ProcessWizardDialog.steps(
                new ProcessWizardDialog.Step(
                        "platform",
                        "Plataforma",
                        platformSelector,
                        () -> puedeAvanzarPasoCreacionServidor(0, state, platformSelector, versionList, parentField, folderNameField, eulaCheck),
                        ignored -> {
                            String error = validarPasoCreacionServidor(0, state, platformSelector, versionList, parentField, folderNameField, eulaCheck);
                            if (error != null) {
                                return error;
                            }
                            ProcessWizardDialog wizard = wizardRef.get();
                            Component parent = wizard == null ? null : wizard.getDialog();
                            if (!cargarOpcionesCreacionWizard(parent, state, versionListModel, versionList)) {
                                return "No se han podido cargar versiones para la plataforma seleccionada.";
                            }
                            SwingUtilities.invokeLater(checkSelectedVersionDownload[0]);
                            return null;
                        }
                ),
                new ProcessWizardDialog.Step(
                        "version",
                        "Versión",
                        versionStep,
                        () -> puedeAvanzarPasoCreacionServidor(1, state, platformSelector, versionList, parentField, folderNameField, eulaCheck),
                        ignored -> {
                            String error = validarPasoCreacionServidor(1, state, platformSelector, versionList, parentField, folderNameField, eulaCheck);
                            if (error != null) {
                                return error;
                            }
                            if (!esOpcionCreacionDescargable(state)) {
                                SwingUtilities.invokeLater(checkSelectedVersionDownload[0]);
                                return "Comprobando descarga de servidor...";
                            }
                            return null;
                        }
                ),
                new ProcessWizardDialog.Step(
                        "parent",
                        "Carpeta de destino",
                        parentStep,
                        () -> puedeAvanzarPasoCreacionServidor(2, state, platformSelector, versionList, parentField, folderNameField, eulaCheck),
                        ignored -> validarPasoCreacionServidor(2, state, platformSelector, versionList, parentField, folderNameField, eulaCheck)
                ),
                new ProcessWizardDialog.Step(
                        "folderName",
                        "Nombre de carpeta",
                        folderNameWrap,
                        () -> puedeAvanzarPasoCreacionServidor(3, state, platformSelector, versionList, parentField, folderNameField, eulaCheck),
                        ignored -> {
                            String error = validarPasoCreacionServidor(3, state, platformSelector, versionList, parentField, folderNameField, eulaCheck);
                            if (error != null) {
                                return error;
                            }
                            result.set(new ServerCreationWizardResult(
                                    state.adapter,
                                    state.option,
                                    new File(state.parentDirectory, normalizarNombreCarpeta(state.folderName))
                            ));
                            return null;
                        }
                )
        );
        ProcessWizardDialog wizard = ProcessWizardDialog.create(
                owner,
                "Crear servidor",
                steps,
                new ProcessWizardDialog.Options(
                        new Dimension(760, 520),
                        "Crear servidor",
                        () -> confirmarCancelacionAsistenteCreacion(wizardRef.get() == null ? null : wizardRef.get().getDialog()),
                        step -> {
                            if (step == 3) {
                                actualizarVistaNombreCarpeta(state, parentPrefixLabel, folderNameField);
                            }
                        }
                )
        );
        wizardRef.set(wizard);
        wizard.refresh();
        return wizard.showDialog() ? result.get() : null;
    }

    private static JLabel createWizardComboLabel(JList<?> list, String text, boolean isSelected) {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setOpaque(true);
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        if (isSelected) {
            label.setBackground(list.getSelectionBackground());
            label.setForeground(list.getSelectionForeground());
        } else {
            label.setBackground(list.getBackground());
            label.setForeground(list.getForeground());
        }
        return label;
    }

    private static boolean cargarOpcionesCreacionWizard(Component parent,
                                                 ServerCreationWizardState state,
                                                 DefaultListModel<ServerCreationOption> versionListModel,
                                                 JList<ServerCreationOption> versionList) {
        if (state == null || state.adapter == null) {
            return false;
        }
        if (state.options != null && !state.options.isEmpty()) {
            actualizarListadoVersiones(state, versionListModel, versionList);
            return true;
        }
        List<ServerCreationOption> options = cargarOpcionesCreacion(state.adapter);
        if (options == null || options.isEmpty()) {
            return false;
        }
        state.options = options;
        state.optionsByPlatform.put(state.adapter.getPlatform(), options);
        actualizarListadoVersiones(state, versionListModel, versionList);
        return !versionListModel.isEmpty();
    }

    private static List<ServerCreationOption> cargarOpcionesCreacion(ServerPlatformAdapter adapter) {
        try {
            return DialogoTareaEnCurso.ejecutar(
                    "Cargando versiones",
                    "Consultando versiones disponibles de " + adapter.getCreationDisplayName() + "...",
                    adapter::listCreationOptions
            );
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    null,
                    "No se han podido cargar las versiones de " + adapter.getCreationDisplayName() + ": " + e.getMessage(),
                    "Crear servidor",
                    JOptionPane.ERROR_MESSAGE
            );
            return List.of();
        }
    }

    private static void actualizarListadoVersiones(ServerCreationWizardState state,
                                            DefaultListModel<ServerCreationOption> versionListModel,
                                            JList<ServerCreationOption> versionList) {
        if (state == null || versionListModel == null || versionList == null) {
            return;
        }
        ServerCreationOption previousSelection = state.option;
        state.updatingVersionList = true;
        try {
            versionList.setValueIsAdjusting(true);
            if (state.options == null || state.options.isEmpty()) {
                versionListModel.clear();
                state.option = null;
                return;
            }
            List<ServerCreationOption> filteredOptions = state.options.stream()
                    .filter(option -> debeMostrarOpcionCreacion(option, state.includeSnapshots, state.includeReleases))
                    .filter(option -> !esOpcionCreacionNoDescargableConocida(state, option))
                    .toList();
            versionListModel.clear();
            versionListModel.addAll(filteredOptions);
            if (filteredOptions.isEmpty()) {
                state.option = null;
                return;
            }
            int selectedIndex = -1;
            if (previousSelection != null) {
                for (int i = 0; i < filteredOptions.size(); i++) {
                    if (Objects.equals(filteredOptions.get(i), previousSelection)) {
                        selectedIndex = i;
                        break;
                    }
                }
            }
            if (selectedIndex < 0) {
                selectedIndex = 0;
            }
            versionList.setSelectedIndex(selectedIndex);
            versionList.ensureIndexIsVisible(selectedIndex);
            state.option = filteredOptions.get(selectedIndex);
        } finally {
            versionList.setValueIsAdjusting(false);
            state.updatingVersionList = false;
        }
    }

    static boolean debeMostrarOpcionCreacion(ServerCreationOption option, boolean includeSnapshots, boolean includeReleases) {
        if (option == null) {
            return false;
        }
        boolean snapshot = esSnapshot(option);
        return snapshot ? includeSnapshots : includeReleases;
    }

    private static boolean esOpcionCreacionNoDescargableConocida(ServerCreationWizardState state,
                                                          ServerCreationOption option) {
        if (state == null || option == null || option.platform() != modelo.extensions.ServerPlatform.VANILLA) {
            return false;
        }
        return state.serverJarUnavailableVersions.contains(option.minecraftVersion());
    }

    private static boolean soportaSnapshotsCreacion(ServerPlatformAdapter adapter) {
        return adapter != null && adapter.supportsUnstableCreationOptions();
    }

    private static boolean puedeAvanzarPasoCreacionServidor(int stepIndex,
                                                     ServerCreationWizardState state,
                                                     PlatformSelectorPanel platformSelector,
                                                     JList<ServerCreationOption> versionList,
                                                     JTextField parentField,
                                                     JTextField folderNameField,
                                                     JCheckBox eulaCheck) {
        if (state == null) {
            return false;
        }
        return switch (stepIndex) {
            case 0 -> platformSelector.getSelectedAdapter() != null;
            case 1 -> {
                ServerCreationOption selected = versionList.getSelectedValue();
                boolean hasEnabledFilter = state.includeSnapshots || state.includeReleases;
                boolean downloadable = selected != null
                        && Objects.equals(selected, state.option)
                        && esOpcionCreacionDescargable(state);
                yield hasEnabledFilter
                        && versionList.getModel().getSize() > 0
                        && selected != null
                        && eulaCheck.isSelected()
                        && downloadable;
            }
            case 2 -> {
                String parentText = parentField.getText();
                yield parentText != null && !parentText.isBlank() && new File(parentText.trim()).isDirectory();
            }
            case 3 -> {
                String folderName = folderNameField.getText();
                yield validarNombreCarpetaServidor(folderName) == null
                        && state.parentDirectory != null
                        && !existeCarpetaConNombreNoPortable(state.parentDirectory, folderName);
            }
            default -> false;
        };
    }

    private static boolean esOpcionCreacionDescargable(ServerCreationWizardState state) {
        if (state == null || state.option == null || state.adapter == null) {
            return false;
        }
        if (state.adapter.getPlatform() != modelo.extensions.ServerPlatform.VANILLA) {
            return true;
        }
        String versionId = state.option.minecraftVersion();
        if (versionId == null || versionId.isBlank()) {
            return false;
        }
        if (Objects.equals(state.pendingServerJarVersion, versionId)) {
            return false;
        }
        return state.serverJarAvailableVersions.contains(versionId);
    }

    private static boolean esSnapshot(ServerCreationOption option) {
        if (option == null) {
            return false;
        }
        if (option.versionType() != null && !option.versionType().isBlank()) {
            return option.isSnapshot();
        }
        if (option.minecraftVersion() == null) {
            return false;
        }
        String version = option.minecraftVersion().toLowerCase();
        return version.contains("snapshot")
                || version.contains("pre")
                || version.contains("rc")
                || version.matches("\\d{2}w\\d{2}[a-z]");
    }

    private static String validarPasoCreacionServidor(int stepIndex,
                                               ServerCreationWizardState state,
                                               PlatformSelectorPanel platformSelector,
                                               JList<ServerCreationOption> versionList,
                                               JTextField parentField,
                                               JTextField folderNameField,
                                               JCheckBox eulaCheck) {
        if (state == null) {
            return "No se ha podido preparar la creación.";
        }
        return switch (stepIndex) {
            case 0 -> {
                state.adapter = platformSelector.getSelectedAdapter();
                yield state.adapter == null ? "Selecciona una plataforma." : null;
            }
            case 1 -> {
                if (!state.includeSnapshots && !state.includeReleases) {
                    yield "Selecciona al menos un tipo de versión.";
                }
                if (versionList.getModel().getSize() <= 0) {
                    yield "No hay versiones disponibles con los filtros seleccionados.";
                }
                state.option = versionList.getSelectedValue();
                if (state.option == null) {
                    yield "Selecciona una versión.";
                }
                state.eulaAccepted = eulaCheck.isSelected();
                yield state.eulaAccepted ? null : "Debes aceptar el EULA para crear el servidor.";
            }
            case 2 -> {
                String parentText = parentField.getText();
                if (parentText == null || parentText.isBlank()) {
                    yield "Selecciona una carpeta de destino.";
                }
                File parent = new File(parentText.trim());
                if (!parent.isDirectory()) {
                    yield "La carpeta de destino no existe.";
                }
                state.parentDirectory = parent.getAbsoluteFile();
                yield null;
            }
            case 3 -> {
                String folderName = folderNameField.getText();
                String error = validarNombreCarpetaServidor(folderName);
                if (error != null) {
                    yield error;
                }
                if (existeCarpetaConNombreNoPortable(state.parentDirectory, folderName)) {
                    yield "Ya existe una carpeta con ese nombre.";
                }
                state.folderName = normalizarNombreCarpeta(folderName);
                yield null;
            }
            default -> null;
        };
    }

    private static void actualizarVistaNombreCarpeta(ServerCreationWizardState state,
                                              JLabel parentPrefixLabel,
                                              JTextField folderNameField) {
        if (state == null) {
            return;
        }
        parentPrefixLabel.setText(formatearPrefijoCarpetaPadre(state.parentDirectory));
        actualizarSugerenciaNombreCarpeta(state, folderNameField, false);
    }

    private static void actualizarSugerenciaNombreCarpeta(ServerCreationWizardState state,
                                                   JTextField folderNameField,
                                                   boolean forceReplace) {
        if (state == null || state.option == null || state.parentDirectory == null) {
            return;
        }
        File suggestedFolder = resolverDirectorioServidorDisponible(
                state.parentDirectory.getAbsoluteFile(),
                state.option.directoryName()
        );
        String previousSuggestion = state.suggestedFolderName;
        state.suggestedFolderName = suggestedFolder.getName();
        boolean shouldReplace = forceReplace
                || !state.folderNameEdited
                || state.folderName == null
                || Objects.equals(normalizarNombreCarpeta(state.folderName), previousSuggestion);
        if (!shouldReplace) {
            return;
        }
        state.folderName = state.suggestedFolderName;
        state.folderNameEdited = false;
        if (folderNameField != null && !Objects.equals(folderNameField.getText(), state.folderName)) {
            folderNameField.setText(state.folderName);
        }
    }

    private static boolean confirmarCancelacionAsistenteCreacion(Component parent) {
        int option = JOptionPane.showConfirmDialog(
                parent,
                "¿Quieres cancelar la creación del servidor?",
                "Cancelar creación",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        return option == JOptionPane.YES_OPTION;
    }

    private static String formatearPrefijoCarpetaPadre(File carpetaPadre) {
        if (carpetaPadre == null) {
            return "";
        }
        String path = carpetaPadre.getAbsolutePath();
        return path.endsWith(File.separator) ? path : path + File.separator;
    }

    private static void animarIconoReset(SvgIconFactory.RotatingIcon icon, AbstractButton button) {
        if (icon == null || button == null) {
            return;
        }
        Timer timer = new Timer(24, null);
        final int frames = 32;
        final int[] frame = {0};
        timer.addActionListener(e -> {
            frame[0]++;
            double progress = Math.min(1d, frame[0] / (double) frames);
            icon.setAngleRadians(progress * Math.PI * 2d);
            button.repaint();
            if (frame[0] >= frames) {
                icon.setAngleRadians(0d);
                button.repaint();
                ((Timer) e.getSource()).stop();
            }
        });
        timer.start();
    }

    static String validarNombreCarpetaServidor(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "El nombre de la carpeta no puede estar vacío.";
        }
        if (!Objects.equals(nombre, nombre.trim())) {
            return "El nombre no puede empezar ni terminar con espacios.";
        }
        if (nombre.equals(".") || nombre.equals("..")) {
            return "Ese nombre está reservado.";
        }
        if (nombre.endsWith(".")) {
            return "El nombre no puede terminar en punto.";
        }
        if (nombre.getBytes(StandardCharsets.UTF_8).length > 255) {
            return "El nombre es demasiado largo.";
        }
        for (int i = 0; i < nombre.length(); i++) {
            char c = nombre.charAt(i);
            if (c < 32 || c == 127) {
                return "El nombre no puede contener caracteres de control.";
            }
            if ("<>:\"/\\|?*".indexOf(c) >= 0) {
                return "El nombre contiene caracteres no válidos para una carpeta.";
            }
        }
        String baseName = nombre;
        int dotIndex = baseName.indexOf('.');
        if (dotIndex >= 0) {
            baseName = baseName.substring(0, dotIndex);
        }
        String upperBaseName = baseName.toUpperCase();
        Set<String> reservedWindowsNames = Set.of(
                "CON", "PRN", "AUX", "NUL",
                "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
                "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        );
        if (reservedWindowsNames.contains(upperBaseName)) {
            return "Ese nombre está reservado por Windows.";
        }
        return null;
    }

    private static String normalizarNombreCarpeta(String nombre) {
        return nombre == null ? "" : nombre.trim();
    }

    private static boolean existeCarpetaConNombreNoPortable(File carpetaPadre, String nombre) {
        if (carpetaPadre == null || nombre == null || nombre.isBlank()) {
            return false;
        }
        if (new File(carpetaPadre, nombre).exists()) {
            return true;
        }
        String[] siblings = carpetaPadre.list();
        if (siblings == null) {
            return false;
        }
        for (String sibling : siblings) {
            if (nombre.equalsIgnoreCase(sibling)) {
                return true;
            }
        }
        return false;
    }

    private static File resolverDirectorioServidorDisponible(File directorioPadre, String nombreBase) {
        File padre = directorioPadre != null ? directorioPadre : new File(".");
        String base = (nombreBase == null || nombreBase.isBlank()) ? "server" : nombreBase;
        File candidato = new File(padre, base);
        int copia = 1;
        while(candidato.exists()){
            candidato = new File(padre, base + "_" + copia);
            copia++;
        }
        return candidato;
    }

    private static final class FolderPathPanel extends JPanel {
        private static final int RESET_GAP = 6;

        private final JLabel prefixLabel;
        private final JTextField folderNameField;
        private final JButton resetButton;

        private FolderPathPanel(JLabel prefixLabel, JTextField folderNameField, JButton resetButton) {
            super(null);
            this.prefixLabel = prefixLabel;
            this.folderNameField = folderNameField;
            this.resetButton = resetButton;
            add(prefixLabel);
            add(folderNameField);
            add(resetButton);
        }

        @Override
        public void doLayout() {
            Insets insets = getInsets();
            int contentX = insets.left;
            int contentY = insets.top;
            int contentW = Math.max(0, getWidth() - insets.left - insets.right);
            int contentH = Math.max(0, getHeight() - insets.top - insets.bottom);

            Dimension resetSize = resetButton.getPreferredSize();
            int resetW = Math.min(resetSize.width, contentW);
            int resetH = Math.min(resetSize.height, contentH);
            int resetX = contentX + contentW - resetW;
            int resetY = contentY + Math.max(0, (contentH - resetH) / 2);
            resetButton.setBounds(resetX, resetY, resetW, resetH);

            int availableTextW = Math.max(0, contentW - resetW - RESET_GAP);
            FontMetrics fieldMetrics = folderNameField.getFontMetrics(folderNameField.getFont());
            FolderPathLayout.Bounds bounds = FolderPathLayout.calculate(
                    availableTextW,
                    anchoPrefijoCompleto(),
                    folderNameField.getText(),
                    fieldMetrics
            );
            int prefixW = bounds.prefixWidth();

            prefixLabel.setVisible(prefixW > 0);
            prefixLabel.setBounds(contentX, contentY, prefixW, contentH);
            folderNameField.setBounds(contentX + prefixW, contentY, Math.max(0, availableTextW - prefixW), contentH);
        }

        private int anchoPrefijoCompleto() {
            String text = prefixLabel.getText();
            if (text == null || text.isEmpty()) {
                return 0;
            }
            FontMetrics metrics = prefixLabel.getFontMetrics(prefixLabel.getFont());
            return metrics.stringWidth(text);
        }
    }

    private static final class LeftEllipsisLabel extends JLabel {
        @Override
        protected void paintComponent(Graphics g) {
            String text = getText();
            if (text == null || text.isEmpty()) {
                super.paintComponent(g);
                return;
            }

            FontMetrics componentMetrics = getFontMetrics(getFont());
            int componentAvailableW = Math.max(0, getWidth() - getInsets().left - getInsets().right);
            if (componentMetrics.stringWidth(text) <= componentAvailableW) {
                super.paintComponent(g);
                return;
            }

            if (isOpaque()) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                applyTextRenderingHints(g2);
                Insets insets = getInsets();
                int availableW = Math.max(0, getWidth() - insets.left - insets.right);
                if (availableW <= 0) {
                    return;
                }
                g2.setFont(getFont());
                g2.setColor(getForeground());
                FontMetrics metrics = g2.getFontMetrics();
                String visibleText = TextEllipsizer.left(text, metrics, availableW);
                int y = insets.top + Math.max(0, (getHeight() - insets.top - insets.bottom - metrics.getHeight()) / 2) + metrics.getAscent();
                g2.setClip(insets.left, insets.top, availableW, Math.max(0, getHeight() - insets.top - insets.bottom));
                g2.drawString(visibleText, insets.left, y);
            } finally {
                g2.dispose();
            }
        }

        private void applyTextRenderingHints(Graphics2D g2) {
            Object desktopHints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
            if (desktopHints instanceof Map<?, ?> hints) {
                for (Map.Entry<?, ?> hint : hints.entrySet()) {
                    if (hint.getKey() instanceof RenderingHints.Key key) {
                        g2.setRenderingHint(key, hint.getValue());
                    }
                }
                return;
            }
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }

    }

    private static final class ServerCreationWizardState {
        private ServerPlatformAdapter adapter;
        private List<ServerCreationOption> options;
        private ServerCreationOption option;
        private final Map<ServerPlatform, List<ServerCreationOption>> optionsByPlatform =
                new EnumMap<>(ServerPlatform.class);
        private File parentDirectory;
        private String suggestedFolderName;
        private String folderName;
        private boolean folderNameEdited;
        private boolean includeSnapshots;
        private boolean includeReleases = true;
        private boolean eulaAccepted;
        private boolean updatingVersionList;
        private boolean updatingVersionFilters;
        private String pendingServerJarVersion;
        private final Set<String> serverJarAvailableVersions = new HashSet<>();
        private final Set<String> serverJarUnavailableVersions = new HashSet<>();
    }

    record ServerCreationWizardResult(
            ServerPlatformAdapter adapter,
            ServerCreationOption option,
            File targetDirectory
    ) {
    }
}
