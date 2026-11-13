/*
 * Fichero: ConversorPlataformaServidor.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Convierte un servidor existente a otra plataforma (por ejemplo de Fabric a NeoForge o de Spigot a Paper):
 * comprueba qué destinos hay disponibles, muestra el asistente de conversión e instala la plataforma nueva
 * conservando mundos, configuración y extensiones.
 * */

package controlador;

import controlador.platform.FileDownloader;
import controlador.platform.ServerCreationOption;
import controlador.platform.ServerInstallationRequest;
import controlador.platform.ServerPlatformAdapter;
import controlador.platform.ServerPlatformAdapters;
import controlador.platform.ServerPlatformProfile;
import modelo.Server;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerPlatform;
import vista.AppTheme;
import vista.PlatformSelectorPanel;
import vista.ProcessWizardDialog;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

final class ConversorPlataformaServidor {
    private static final MojangAPI MOJANG_API = new MojangAPI();

    private final GestorServidores gestor;

    ConversorPlataformaServidor(GestorServidores gestor) {
        this.gestor = gestor;
    }

    boolean puedeConvertirseAPlataformaCompatible(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return false;
        }

        Path serverDir;
        try {
            serverDir = Path.of(server.getServerDir());
        } catch (RuntimeException ex) {
            return false;
        }

        if (!Files.isDirectory(serverDir)) {
            return false;
        }

        ServerPlatformProfile profile = PerfilPlataformaServidor.detectar(serverDir);
        if (profile != null) {
            PerfilPlataformaServidor.aplicar(server, profile);
        }

        ServerPlatform sourcePlatform = profile == null ? server.getPlatform() : profile.platform();
        if (sourcePlatform == null || sourcePlatform == ServerPlatform.UNKNOWN) {
            return false;
        }
        return plataformasObjetivoConversion(sourcePlatform).stream()
                .anyMatch(ServerPlatformAdapter::supportsAutomatedCreation);
    }

    Server convertirServidorAPlataformaCompatible(Server server) {
        if (server == null) return null;
        if (server.getServerProcess() != null && server.getServerProcess().isAlive()) {
            JOptionPane.showMessageDialog(
                    null,
                    "Detén el servidor antes de iniciar una conversión de plataforma.",
                    "Convertir servidor",
                    JOptionPane.WARNING_MESSAGE
            );
            return null;
        }

        ConversionSource source = resolverOrigenConversion(server);
        if (source == null || source.platform() == null || source.platform() == ServerPlatform.UNKNOWN) {
            JOptionPane.showMessageDialog(
                    null,
                    "No se ha podido detectar una plataforma de origen compatible para convertir este servidor.",
                    "Convertir servidor",
                    JOptionPane.WARNING_MESSAGE
            );
            return null;
        }

        List<ConversionTargetAvailability> targetAvailability = cargarDisponibilidadConversion(server, source.platform());
        if (targetAvailability.isEmpty()) {
            JOptionPane.showMessageDialog(
                    null,
                    "No se ha encontrado ninguna plataforma destino compatible para convertir este servidor.",
                    "Convertir servidor",
                    JOptionPane.WARNING_MESSAGE
            );
            return null;
        }
        if (targetAvailability.stream().noneMatch(ConversionTargetAvailability::available)) {
            JOptionPane.showMessageDialog(
                    null,
                    "No hay conversiones disponibles para Minecraft " + textoODesconocido(server == null ? null : server.getVersion()) + ".",
                    "Convertir servidor",
                    JOptionPane.WARNING_MESSAGE
            );
            return null;
        }

        ConversionWizardResult wizardResult = mostrarAsistenteConversion(server, source, targetAvailability);
        if (wizardResult == null) return null;

        Server converted = convertirServidorExistente(
                server,
                wizardResult.adapter(),
                wizardResult.option(),
                (url, destination) -> MOJANG_API.descargar(url, destination, null),
                wizardResult.createBackup()
        );
        if (converted != null && wizardResult.warning().crossEcosystem()) {
            mostrarAvisoPostConversionEcosistema(server, source.ecosystemType(), wizardResult.option().platform().getDefaultEcosystemType());
        }
        return converted;
    }

    private ConversionWizardResult mostrarAsistenteConversion(Server server,
                                                              ConversionSource source,
                                                              List<ConversionTargetAvailability> targetAvailability) {
        if (source == null || targetAvailability == null || targetAvailability.isEmpty()) {
            return null;
        }
        List<ServerPlatformAdapter> targetAdapters = targetAvailability.stream()
                .map(ConversionTargetAvailability::adapter)
                .filter(Objects::nonNull)
                .toList();
        if (targetAdapters.isEmpty()) {
            return null;
        }

        Map<ServerPlatform, String> disabledTooltips = new java.util.LinkedHashMap<>();
        for (ConversionTargetAvailability availability : targetAvailability) {
            if (availability != null && availability.adapter() != null && !availability.available()) {
                disabledTooltips.put(availability.adapter().getPlatform(), availability.unavailableReason());
            }
        }

        ConversionWizardState state = new ConversionWizardState();
        state.adapter = targetAvailability.stream()
                .filter(ConversionTargetAvailability::available)
                .map(ConversionTargetAvailability::adapter)
                .findFirst()
                .orElse(null);

        AtomicReference<ProcessWizardDialog> wizardRef = new AtomicReference<>();
        AtomicReference<ConversionWizardResult> result = new AtomicReference<>();
        DefaultComboBoxModel<ServerCreationOption> versionModel = new DefaultComboBoxModel<>();
        JComboBox<ServerCreationOption> versionBox = new JComboBox<>(versionModel);
        versionBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.displayName());
            label.setOpaque(true);
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            return label;
        });
        versionBox.addActionListener(e -> state.option = (ServerCreationOption) versionBox.getSelectedItem());

        Runnable updateVersions = () -> {
            versionModel.removeAllElements();
            state.option = null;
            ConversionTargetAvailability availability = conversionAvailabilityFor(targetAvailability, state.adapter);
            if (availability != null) {
                for (ServerCreationOption option : availability.options()) {
                    versionModel.addElement(option);
                }
            }
            if (versionModel.getSize() > 0) {
                versionBox.setSelectedIndex(0);
                state.option = (ServerCreationOption) versionBox.getSelectedItem();
            }
        };

        PlatformSelectorPanel platformSelector = new PlatformSelectorPanel(
                targetAdapters,
                state.adapter,
                adapter -> {
                    state.adapter = adapter;
                    updateVersions.run();
                    ProcessWizardDialog wizard = wizardRef.get();
                    if (wizard != null) {
                        wizard.refresh();
                    }
                },
                disabledTooltips
        );
        updateVersions.run();

        JPanel versionPanel = new JPanel(new BorderLayout(0, 12));
        JLabel versionContext = new JLabel(
                "Servidor actual: " + source.platform().getLegacyTypeName()
                        + " | Minecraft " + textoODesconocido(server == null ? null : server.getVersion())
        );
        versionContext.setForeground(AppTheme.getMutedForeground());
        versionPanel.add(versionContext, BorderLayout.NORTH);

        JCheckBox backupCheck = new JCheckBox("Crear backup completo antes de continuar", true);
        backupCheck.setOpaque(false);
        backupCheck.addActionListener(e -> state.createBackup = backupCheck.isSelected());
        JLabel conversionWarningLabel = new JLabel();
        conversionWarningLabel.setVerticalAlignment(SwingConstants.TOP);
        JLabel backupDetailLabel = new JLabel("<html>Los mundos, server.properties, EULA, icono y carpetas de extensiones existentes se conservarán cuando sea posible.</html>");
        backupDetailLabel.setForeground(AppTheme.getMutedForeground());
        JPanel backupPanel = new JPanel(new BorderLayout(0, 10));
        backupPanel.add(conversionWarningLabel, BorderLayout.NORTH);
        JPanel backupChoicePanel = new JPanel(new BorderLayout(0, 4));
        backupChoicePanel.setOpaque(false);
        backupChoicePanel.add(backupCheck, BorderLayout.NORTH);
        backupChoicePanel.add(backupDetailLabel, BorderLayout.CENTER);
        backupPanel.add(backupChoicePanel, BorderLayout.CENTER);
        JPanel versionAndBackupPanel = new JPanel(new BorderLayout(0, 12));
        versionAndBackupPanel.setOpaque(false);
        versionAndBackupPanel.add(versionBox, BorderLayout.NORTH);
        versionAndBackupPanel.add(backupPanel, BorderLayout.CENTER);
        versionPanel.add(versionAndBackupPanel, BorderLayout.CENTER);

        Runnable updateWarning = () -> {
            ConversionWarning warning = construirAvisoConversion(source, state.option == null ? null : state.option.platform());
            conversionWarningLabel.setText("""
                    <html>
                    Se va a convertir el servidor seleccionado de %s a %s.<br><br>
                    %s
                    </html>
                    """.formatted(
                    source.platform().getLegacyTypeName(),
                    state.option == null || state.option.platform() == null ? "desconocida" : state.option.platform().getLegacyTypeName(),
                    warning.message().replace("\n", "<br>")
            ));
        };
        versionBox.addActionListener(e -> updateWarning.run());

        List<ProcessWizardDialog.Step> steps = ProcessWizardDialog.steps(
                new ProcessWizardDialog.Step(
                        "platform",
                        "Plataforma destino",
                        platformSelector,
                        () -> {
                            ConversionTargetAvailability availability = conversionAvailabilityFor(targetAvailability, state.adapter);
                            return availability != null && availability.available();
                        },
                        ignored -> {
                            if (state.adapter == null) {
                                return "Selecciona una plataforma destino.";
                            }
                            ConversionTargetAvailability availability = conversionAvailabilityFor(targetAvailability, state.adapter);
                            return availability == null || !availability.available()
                                    ? firstNonBlank(availability == null ? null : availability.unavailableReason(), "La plataforma seleccionada no está disponible.")
                                    : null;
                        }
                ),
                new ProcessWizardDialog.Step(
                        "version",
                        "Versión compatible",
                        versionPanel,
                        () -> state.option != null,
                        ignored -> {
                            state.option = (ServerCreationOption) versionBox.getSelectedItem();
                            if (state.option == null) {
                                return "Selecciona una versión compatible.";
                            }
                            state.createBackup = backupCheck.isSelected();
                            ConversionWarning warning = construirAvisoConversion(source, state.option.platform());
                            result.set(new ConversionWizardResult(state.adapter, state.option, state.createBackup, warning));
                            return null;
                        }
                )
        );

        ProcessWizardDialog wizard = ProcessWizardDialog.create(
                null,
                "Convertir servidor",
                steps,
                new ProcessWizardDialog.Options(
                        new Dimension(760, 520),
                        "Convertir servidor",
                        () -> true,
                        step -> {
                            if (step == 1) {
                                updateVersions.run();
                                updateWarning.run();
                            }
                        }
                )
        );
        wizardRef.set(wizard);
        return wizard.showDialog() ? result.get() : null;
    }

    private ConversionTargetAvailability conversionAvailabilityFor(List<ConversionTargetAvailability> availability,
                                                                  ServerPlatformAdapter adapter) {
        if (availability == null || adapter == null) {
            return null;
        }
        return availability.stream()
                .filter(entry -> entry != null && Objects.equals(entry.adapter(), adapter))
                .findFirst()
                .orElse(null);
    }

    private ConversionSource resolverOrigenConversion(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return null;
        }
        Path serverDir;
        try {
            serverDir = Path.of(server.getServerDir());
        } catch (RuntimeException ex) {
            return null;
        }
        ServerPlatformProfile sourceProfile = PerfilPlataformaServidor.detectar(serverDir);
        if (sourceProfile != null) {
            PerfilPlataformaServidor.aplicar(server, sourceProfile);
            return new ConversionSource(sourceProfile.platform(), sourceProfile.ecosystemType());
        }
        ServerPlatform platform = server.getPlatform() == null ? ServerPlatform.UNKNOWN : server.getPlatform();
        ServerEcosystemType ecosystemType = server.getEcosystemType() == null ? platform.getDefaultEcosystemType() : server.getEcosystemType();
        return platform == ServerPlatform.UNKNOWN ? null : new ConversionSource(platform, ecosystemType);
    }

    private ConversionWarning construirAvisoConversion(ConversionSource source, ServerPlatform targetPlatform) {
        ServerEcosystemType sourceEcosystem = source == null ? ServerEcosystemType.UNKNOWN : source.ecosystemType();
        ServerEcosystemType targetEcosystem = targetPlatform == null ? ServerEcosystemType.UNKNOWN : targetPlatform.getDefaultEcosystemType();
        boolean crossEcosystem = sourceEcosystem != ServerEcosystemType.UNKNOWN
                && targetEcosystem != ServerEcosystemType.UNKNOWN
                && sourceEcosystem != targetEcosystem
                && sourceEcosystem != ServerEcosystemType.NONE
                && targetEcosystem != ServerEcosystemType.NONE;
        String warningText;
        if (crossEcosystem && sourceEcosystem == ServerEcosystemType.MODS && targetEcosystem == ServerEcosystemType.PLUGINS) {
            warningText = "Mods y plugins son ecosistemas distintos. Los .jar de mods que ya existan se conservarán en la carpeta mods, pero no se migrarán ni se tratarán como plugins.";
        } else if (crossEcosystem && sourceEcosystem == ServerEcosystemType.PLUGINS && targetEcosystem == ServerEcosystemType.MODS) {
            warningText = "Plugins y mods son ecosistemas distintos. Los .jar de plugins que ya existan se conservarán en la carpeta plugins, pero no se migrarán ni se tratarán como mods.";
        } else if (sourceEcosystem == ServerEcosystemType.MODS && targetEcosystem == ServerEcosystemType.MODS) {
            warningText = "Vas a cambiar de cargador de mods. Los mods instalados se conservarán, pero pueden no ser compatibles con el nuevo loader.";
        } else if (sourceEcosystem == ServerEcosystemType.PLUGINS && targetEcosystem == ServerEcosystemType.PLUGINS) {
            warningText = "Vas a cambiar de plataforma de plugins. La mayoría de plugins Bukkit/Paper suelen ser compatibles, pero conviene revisar el resultado.";
        } else {
            warningText = "Dora instalará la plataforma seleccionada y creará la carpeta de extensiones correspondiente.";
        }
        return new ConversionWarning(warningText, crossEcosystem);
    }

    ConversionWarning construirAvisoConversion(ServerPlatform sourcePlatform, ServerPlatform targetPlatform) {
        ServerPlatform resolvedSource = sourcePlatform == null ? ServerPlatform.UNKNOWN : sourcePlatform;
        return construirAvisoConversion(
                new ConversionSource(resolvedSource, resolvedSource.getDefaultEcosystemType()),
                targetPlatform
        );
    }

    private void mostrarAvisoPostConversionEcosistema(Server server,
                                                      ServerEcosystemType sourceEcosystem,
                                                      ServerEcosystemType targetEcosystem) {
        if (GraphicsEnvironment.isHeadless() || server == null || server.getServerDir() == null) {
            return;
        }
        String oldFolder = sourceEcosystem == ServerEcosystemType.MODS ? "mods" : "plugins";
        String newFolder = targetEcosystem == ServerEcosystemType.MODS ? "mods" : "plugins";
        JOptionPane.showMessageDialog(
                null,
                "La conversión cambió de ecosistema. Los archivos anteriores siguen en "
                        + Path.of(server.getServerDir()).resolve(oldFolder)
                        + ". Las nuevas extensiones deben instalarse en "
                        + Path.of(server.getServerDir()).resolve(newFolder)
                        + ". La caché local de extensiones se ha actualizado conservando los metadatos anteriores.",
                "Conversión completada",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private static String textoODesconocido(String value) {
        return value == null || value.isBlank() ? "desconocida" : value;
    }

    private List<ConversionTargetAvailability> cargarDisponibilidadConversion(Server server, ServerPlatform sourcePlatform) {
        List<ServerPlatformAdapter> targetAdapters = plataformasObjetivoConversion(sourcePlatform);
        if (targetAdapters.isEmpty()) {
            return List.of();
        }
        try {
            return DialogoTareaEnCurso.ejecutar(
                    "Cargando versiones",
                    "Consultando conversiones compatibles con Minecraft " + textoODesconocido(server == null ? null : server.getVersion()) + "...",
                    () -> evaluarDisponibilidadConversion(server, targetAdapters)
            );
        } catch (IOException e) {
            return targetAdapters.stream()
                    .map(adapter -> ConversionTargetAvailability.unavailable(
                            adapter,
                            "No se han podido consultar las versiones de " + adapter.getCreationDisplayName() + "."
                    ))
                    .toList();
        }
    }

    List<ConversionTargetAvailability> evaluarDisponibilidadConversion(Server server,
                                                                       List<ServerPlatformAdapter> targetAdapters) {
        if (targetAdapters == null || targetAdapters.isEmpty()) {
            return List.of();
        }
        List<ConversionTargetAvailability> availability = new ArrayList<>();
        String minecraftVersion = textoODesconocido(server == null ? null : server.getVersion());
        for (ServerPlatformAdapter adapter : targetAdapters) {
            if (adapter == null) {
                continue;
            }
            if (!adapter.supportsAutomatedCreation()) {
                availability.add(ConversionTargetAvailability.unavailable(adapter, adapter.getCreationUnavailableReason()));
                continue;
            }
            try {
                List<ServerCreationOption> options = filtrarOpcionesConversion(server, adapter.listCreationOptions());
                if (options.isEmpty()) {
                    availability.add(ConversionTargetAvailability.unavailable(
                            adapter,
                            "No disponible para Minecraft " + minecraftVersion + "."
                    ));
                } else {
                    availability.add(ConversionTargetAvailability.available(adapter, options));
                }
            } catch (IOException e) {
                availability.add(ConversionTargetAvailability.unavailable(
                        adapter,
                        "No se han podido consultar las versiones de " + adapter.getCreationDisplayName() + "."
                ));
            }
        }
        return availability;
    }

    private List<ServerCreationOption> filtrarOpcionesConversion(Server server, List<ServerCreationOption> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        if (server == null || server.getVersion() == null || server.getVersion().isBlank()) {
            return options;
        }
        return options.stream()
                .filter(option -> ServerCreationOption.isCompatibleMinecraftVersion(option.minecraftVersion(), server.getVersion()))
                .toList();
    }

    private List<ServerPlatformAdapter> plataformasObjetivoConversion(ServerPlatform sourcePlatform) {
        ServerPlatform resolvedSource = sourcePlatform == null ? ServerPlatform.UNKNOWN : sourcePlatform;
        return ServerPlatformAdapters.all().stream()
                .filter(ServerPlatformAdapter::supportsAutomatedCreation)
                .filter(adapter -> adapter.getPlatform() != ServerPlatform.VANILLA)
                .filter(adapter -> adapter.getPlatform() != ServerPlatform.UNKNOWN)
                .filter(adapter -> adapter.getPlatform() != resolvedSource)
                .filter(adapter -> adapter.getPlatform().isModPlatform() || adapter.getPlatform().isPluginPlatform())
                .toList();
    }

    Server convertirServidorExistente(Server server,
                                      ServerPlatformAdapter targetAdapter,
                                      ServerCreationOption option,
                                      controlador.platform.FileDownloader downloader) {
        return convertirServidorExistente(server, targetAdapter, option, downloader, true);
    }

    Server convertirServidorExistente(Server server,
                                      ServerPlatformAdapter targetAdapter,
                                      ServerCreationOption option,
                                      controlador.platform.FileDownloader downloader,
                                      boolean createBackup) {
        if (server == null || targetAdapter == null || option == null) return null;
        if (server.getServerDir() == null || server.getServerDir().isBlank()) return null;
        if (server.getServerProcess() != null && server.getServerProcess().isAlive()) return null;
        if (!targetAdapter.supportsAutomatedCreation()) return null;
        if (option.platform() != targetAdapter.getPlatform()) return null;

        Path serverDir = Path.of(server.getServerDir());
        if (!Files.isDirectory(serverDir)) return null;

        ConversionPreservationHelper.Snapshot preservationSnapshot = null;
        try {
            ConversionSource source = resolverOrigenConversion(server);
            ServerEcosystemType sourceEcosystem = source == null ? ServerEcosystemType.UNKNOWN : source.ecosystemType();
            ServerEcosystemType targetEcosystem = option.platform() == null
                    ? ServerEcosystemType.UNKNOWN
                    : option.platform().getDefaultEcosystemType();
            preservationSnapshot = ConversionPreservationHelper.prepare(serverDir, option.platform(), createBackup);

            DialogoTareaEnCurso.ejecutar(
                    "Convirtiendo servidor",
                    "Instalando " + option.displayName() + " sobre el servidor actual...",
                    () -> {
                        targetAdapter.install(new Server(), new ServerInstallationRequest(
                                serverDir,
                                option.minecraftVersion(),
                                option.platformVersion(),
                                true,
                                GestorServidores.resolverIconoPorDefecto(),
                                MOJANG_API,
                                downloader
                        ));
                        return null;
                    }
            );

            ConversionPreservationHelper.restore(preservationSnapshot, serverDir);
            for (Path extensionDirectory : targetAdapter.getExtensionDirectories(serverDir)) {
                if (extensionDirectory != null) {
                    Files.createDirectories(extensionDirectory);
                }
            }
            GestorMundos.sincronizarMundosServidor(server);

            ServerPlatformProfile targetProfile = PerfilPlataformaServidor.detectar(serverDir);
            if (targetProfile != null && targetProfile.platform() == option.platform()) {
                PerfilPlataformaServidor.aplicar(server, targetProfile);
            } else {
                aplicarMetadatosPlataformaDestino(server, targetAdapter, option);
            }
            if (server.getLoaderVersion() == null || server.getLoaderVersion().isBlank()) {
                server.setLoaderVersion(option.platformVersion());
            }

            revisarCacheExtensionesTrasConversion(server, sourceEcosystem, targetEcosystem);
            gestor.guardarServidor(server);
            return server;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    null,
                    "No se ha podido convertir el servidor: " + e.getMessage(),
                    "Convertir servidor",
                    JOptionPane.ERROR_MESSAGE
            );
            return null;
        } finally {
            ConversionPreservationHelper.cleanup(preservationSnapshot);
        }
    }

    private void aplicarMetadatosPlataformaDestino(Server server,
                                                   ServerPlatformAdapter targetAdapter,
                                                   ServerCreationOption option) {
        if (server == null || targetAdapter == null || option == null) {
            return;
        }
        server.setPlatform(option.platform());
        server.setLoader(targetAdapter.getLoader());
        server.setEcosystemType(targetAdapter.getEcosystemType());
        server.setCapabilities(targetAdapter.getCapabilities());
        server.setVersion(option.minecraftVersion());
        server.setLoaderVersion(option.platformVersion());
    }

    private void revisarCacheExtensionesTrasConversion(Server server,
                                                       ServerEcosystemType sourceEcosystem,
                                                       ServerEcosystemType targetEcosystem) throws IOException {
        if (server == null) {
            return;
        }
        gestor.sincronizarExtensionesServidor(server);
        gestor.getServerExtensionsService().persistInstalledExtensionCache(server);
    }

    private record ConversionSource(ServerPlatform platform, ServerEcosystemType ecosystemType) {
    }

    record ConversionWizardResult(ServerPlatformAdapter adapter,
                                  ServerCreationOption option,
                                  boolean createBackup,
                                  ConversionWarning warning) {
    }

    private static final class ConversionWizardState {
        private ServerPlatformAdapter adapter;
        private ServerCreationOption option;
        private boolean createBackup = true;
    }

    record ConversionTargetAvailability(ServerPlatformAdapter adapter,
                                        List<ServerCreationOption> options,
                                        String unavailableReason) {
        boolean available() {
            return adapter != null && options != null && !options.isEmpty();
        }

        static ConversionTargetAvailability available(ServerPlatformAdapter adapter, List<ServerCreationOption> options) {
            return new ConversionTargetAvailability(adapter, options == null ? List.of() : List.copyOf(options), null);
        }

        static ConversionTargetAvailability unavailable(ServerPlatformAdapter adapter, String unavailableReason) {
            return new ConversionTargetAvailability(adapter, List.of(), unavailableReason);
        }
    }

    record ConversionWarning(String message, boolean crossEcosystem) {
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
