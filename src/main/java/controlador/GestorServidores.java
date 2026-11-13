/*
 * Fichero: GestorServidores.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Esta clase lleva a cabo toda la gestión de los servidores como conjunto algunas funciones son crear, borrar, listar,
 * importar, inicializar, etc.
 * */

package controlador;

import controlador.automation.ServerAutomationService;
import controlador.extensions.ExtensionCompatibilityReport;
import controlador.extensions.ExtensionCatalogDetails;
import controlador.extensions.ExtensionCatalogEntry;
import controlador.extensions.ExtensionCatalogProviderDescriptor;
import controlador.extensions.ExtensionCatalogQuery;
import controlador.extensions.ExtensionCatalogService;
import controlador.extensions.CurseForgeModpackService;
import controlador.extensions.ExtensionArtifactDownloader;
import controlador.extensions.ExtensionDownloadPlan;
import controlador.extensions.ExtensionInstallResolution;
import controlador.extensions.ExtensionInstallResolutionState;
import controlador.extensions.InstalledExtensionStatus;
import controlador.extensions.ExtensionUpdateCandidate;
import controlador.extensions.ModrinthModpackService;
import controlador.extensions.ServerExtensionsService;
import modelo.Server;
import modelo.ServerConfig;
import modelo.automation.ServerAutomationRule;
import controlador.platform.JavaRuntimeCompatibility;
import controlador.platform.ServerInstallationRequest;
import controlador.platform.ServerCreationOption;
import controlador.platform.FileDownloader;
import controlador.platform.ServerPlatformAdapter;
import controlador.platform.ServerPlatformAdapters;
import controlador.platform.ServerPlatformProfile;
import controlador.platform.ServerValidationResult;
import lombok.Getter;
import lombok.Setter;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vista.AppTheme;
import vista.FolderPathLayout;
import vista.PlatformSelectorPanel;
import vista.ProcessWizardDialog;
import vista.SvgIconFactory;
import vista.TextEllipsizer;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Getter
@Setter

@SuppressWarnings("unused")
public class GestorServidores {
    public enum ModpackImportConflictPolicy {
        KEEP_EXISTING,
        REPLACE_EXISTING
    }

    private static final Pattern MINECRAFT_VERSION_PATTERN = Pattern.compile("(?i)(?:1\\.(?:0|[1-9][0-9]*)(?:\\.\\d+)?(?:-(?:snapshot|pre|rc)-?\\d+)?|[2-9]\\d*\\.\\d+(?:\\.\\d+)?(?:-(?:snapshot|pre|rc)-?\\d+)?|\\d{2}w\\d{2}[a-z]|[ab]\\d+\\.\\d+(?:\\.\\d+)?)");

    // ===== ATRIBUTOS =====
    private static final MojangAPI MOJANG_API = new MojangAPI();

    private static final int PUERTO_MINECRAFT_DEFECTO = 25565;
    private static final int PUERTO_MAXIMO = 65535;

    private static final String JSON_FILE = "dora-server-list.json";
    private static final String MODPACK_BACKUP_DIR = ".dora-backup";

    private static File getJsonFile() {
        return AppPaths.configDirectory().resolve(JSON_FILE).toFile();
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final File jsonFile;
    private final ServerExtensionsService serverExtensionsService = new ServerExtensionsService();
    private final ExtensionCatalogService extensionCatalogService = new ExtensionCatalogService();
    private final CurseForgeModpackService curseForgeModpackService = new CurseForgeModpackService();
    private final ModrinthModpackService modrinthModpackService;
    private final FileDownloader extensionDownloader = new ExtensionArtifactDownloader();
    private ServerAutomationService automationService;

    private String avisoServidoresNoCargados;
    private String avisoListaServidoresIlegible;

    // Lista Principal (runtime + persistencia)
    private List<Server> listaServidores;
    // Servidores guardados cuya carpeta no está disponible ahora mismo (disco externo desconectado, carpeta de red...).
    // No se muestran, pero se conservan en el JSON para no perder su configuración y volver a cargarlos más adelante.
    private final List<Server> servidoresNoDisponibles = new ArrayList<>();
    private Server servidorSeleccionado;

    // Esto gestiona los listeners
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // ===== CONSTRUCTORES =====

    // Constructor por defecto
    public GestorServidores() {
        this(getJsonFile(), new ModrinthModpackService());
    }

    GestorServidores(File jsonFile) {
        this(jsonFile, new ModrinthModpackService());
    }

    GestorServidores(File jsonFile, ModrinthModpackService modrinthModpackService) {
        this.jsonFile = jsonFile == null ? getJsonFile() : jsonFile;
        this.modrinthModpackService = modrinthModpackService == null ? new ModrinthModpackService() : modrinthModpackService;
        this.listaServidores = cargarServidores();
        boolean cambiosOrden = normalizarMetadatosOrden(true);
        validarYLimpiarServidoresPersistidos();
        boolean cambiosMundos = sincronizarMundosServidoresCargados();
        if (cambiosOrden || cambiosMundos) {
            guardarServidores();
        }
    }

    private boolean sincronizarMundosServidoresCargados() {
        if (listaServidores == null || listaServidores.isEmpty()) return false;

        boolean cambios = false;
        for (Server server : listaServidores) {
            if (server == null) continue;
            try {
                if (GestorMundos.sincronizarMundosServidor(server)) {
                    cambios = true;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return cambios;
    }

    // cargamos todos los servidores del JSON
    private List<Server> cargarServidores(){
        File file = jsonFile;
        if (!file.exists()) {
            List<Server> servidores = new ArrayList<>();
            try{
                File parent = file.getParentFile();
                if (parent != null) {
                    Files.createDirectories(parent.toPath());
                }
                Utilidades.escribirArchivoAtomico(
                        file.toPath(),
                        mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(servidores)
                );
            } catch (Exception e) {
                System.err.println("Error al inicializar servidores: " + e.getMessage());
            }
            return servidores;
        }
        try{
            List<Server> servidores = mapper.readValue(file, new TypeReference<>(){});
            return servidores == null ? new ArrayList<>() : new ArrayList<>(servidores);
        } catch (JacksonException e) {
            System.err.println("Error al cargar servidores: " + e.getMessage());
            // El siguiente guardado sobrescribiría el archivo ilegible, así que antes dejamos una copia recuperable.
            Path copia = respaldarArchivoIlegible(file.toPath());
            avisoListaServidoresIlegible = "No se ha podido leer " + JSON_FILE + " porque su contenido no es válido."
                    + "\nDora empezará con la lista de servidores vacía."
                    + (copia == null
                    ? "\nNo se ha podido crear una copia de seguridad del archivo."
                    : "\nSe ha guardado una copia del archivo original en:\n" + copia);
            AppErrorReporter.report("No se ha podido leer " + JSON_FILE + ".", e);
            return new ArrayList<>();
        }
    }

    // Copia un archivo de datos ilegible a un nombre único para que no se pierda al volver a guardar.
    static Path respaldarArchivoIlegible(Path archivo) {
        if (archivo == null || !Files.isRegularFile(archivo)) {
            return null;
        }
        try {
            String marca = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path copia = archivo.resolveSibling(archivo.getFileName() + ".corrupt-" + marca);
            int intento = 1;
            while (Files.exists(copia)) {
                copia = archivo.resolveSibling(archivo.getFileName() + ".corrupt-" + marca + "-" + intento++);
            }
            Files.copy(archivo, copia);
            return copia;
        } catch (IOException | RuntimeException ex) {
            System.err.println("No se ha podido respaldar " + archivo + ": " + ex.getMessage());
            return null;
        }
    }

    private boolean normalizarMetadatosOrden(boolean reordenarListaInterna) {
        if (listaServidores == null) {
            listaServidores = new ArrayList<>();
            return false;
        }

        boolean cambios = false;
        List<Server> depurada = new ArrayList<>();
        for (Server server : listaServidores) {
            if (server == null) {
                cambios = true;
                continue;
            }
            if (asegurarMetadatosPersistentes(server)) {
                cambios = true;
            }
            depurada.add(server);
        }
        listaServidores = depurada;

        List<Server> porOrdenBase = new ArrayList<>(listaServidores);
        porOrdenBase.sort(Comparator.comparingInt(this::valorOrdenListaParaNormalizar));
        for (int i = 0; i < porOrdenBase.size(); i++) {
            Server server = porOrdenBase.get(i);
            if (!Objects.equals(server.getOrdenLista(), i)) {
                server.setOrdenLista(i);
                cambios = true;
            }
        }

        int siguienteOrdenFavorito = obtenerSiguienteOrdenFavorito();
        for (Server server : listaServidores) {
            if (!Boolean.TRUE.equals(server.getFavorito())) {
                continue;
            }
            if (server.getOrdenFavorito() != null) {
                continue;
            }
            server.setOrdenFavorito(siguienteOrdenFavorito++);
            cambios = true;
        }

        if (reordenarListaInterna) {
            listaServidores = copiarListaServidoresOrdenada();
        }
        return cambios;
    }

    private boolean asegurarMetadatosPersistentes(Server server) {
        boolean cambios = false;
        if (server.migrarModeloLegacy()) {
            cambios = true;
        }
        if (server.getId() == null || server.getId().isBlank()) {
            server.setId(UUID.randomUUID().toString());
            cambios = true;
        }
        if (server.getServerConfig() == null) {
            server.setServerConfig(new ServerConfig());
            cambios = true;
        }
        if (sincronizarPuertoConfigDesdeProperties(server)) {
            cambios = true;
        }
        if (server.getFavorito() == null) {
            server.setFavorito(Boolean.FALSE);
            cambios = true;
        }
        if (server.getEstadisticasRangoSegundos() == null || server.getEstadisticasRangoSegundos() <= 0) {
            server.setEstadisticasRangoSegundos(300);
            cambios = true;
        }
        if (server.getEstadisticasPersistenciaActiva() == null) {
            server.setEstadisticasPersistenciaActiva(Boolean.TRUE);
            cambios = true;
        }
        if (server.getEstadisticasVentanaRecienteSegundos() == null || server.getEstadisticasVentanaRecienteSegundos() <= 0) {
            server.setEstadisticasVentanaRecienteSegundos(30 * 24 * 60 * 60);
            cambios = true;
        }
        if (server.getEstadisticasResolucionHistoricaSegundos() == null || server.getEstadisticasResolucionHistoricaSegundos() <= 0) {
            server.setEstadisticasResolucionHistoricaSegundos(60);
            cambios = true;
        }
        if (server.getEstadisticasCpuActiva() == null) {
            server.setEstadisticasCpuActiva(Boolean.TRUE);
            cambios = true;
        }
        if (server.getEstadisticasCpuHistorial() == null) {
            server.setEstadisticasCpuHistorial(Boolean.TRUE);
            cambios = true;
        }
        if (server.getEstadisticasRamActiva() == null) {
            server.setEstadisticasRamActiva(Boolean.TRUE);
            cambios = true;
        }
        if (server.getEstadisticasRamHistorial() == null) {
            server.setEstadisticasRamHistorial(Boolean.TRUE);
            cambios = true;
        }
        if (server.getEstadisticasDiscoActiva() == null) {
            server.setEstadisticasDiscoActiva(Boolean.TRUE);
            cambios = true;
        }
        if (server.getEstadisticasDiscoHistorial() == null) {
            server.setEstadisticasDiscoHistorial(Boolean.TRUE);
            cambios = true;
        }
        if (server.getEstadisticasJugadoresActiva() == null) {
            server.setEstadisticasJugadoresActiva(Boolean.FALSE);
            cambios = true;
        }
        if (server.getEstadisticasJugadoresHistorial() == null) {
            server.setEstadisticasJugadoresHistorial(Boolean.TRUE);
            cambios = true;
        }
        if (server.getPreviewRenderProfileId() == null || server.getPreviewRenderProfileId().isBlank()) {
            server.setPreviewRenderProfileId("quality");
            cambios = true;
        }
        if (server.getPreviewRenderRealtime() == null) {
            server.setPreviewRenderRealtime(Boolean.FALSE);
            cambios = true;
        }
        if (server.getPreviewShowSpawn() == null) {
            server.setPreviewShowSpawn(Boolean.FALSE);
            cambios = true;
        }
        if (server.getPreviewShowPlayers() == null) {
            server.setPreviewShowPlayers(Boolean.FALSE);
            cambios = true;
        }
        if (server.getPreviewShowChunkGrid() == null) {
            server.setPreviewShowChunkGrid(Boolean.FALSE);
            cambios = true;
        }
        if (server.getPreviewUseWholeMap() == null) {
            server.setPreviewUseWholeMap(Boolean.FALSE);
            cambios = true;
        }
        if (server.getPreviewRenderLimitPixels() == null || server.getPreviewRenderLimitPixels() < 0) {
            server.setPreviewRenderLimitPixels(256);
            cambios = true;
        }
        if (server.getPreviewRenderCenterId() == null || server.getPreviewRenderCenterId().isBlank()) {
            server.setPreviewRenderCenterId("spawn");
            cambios = true;
        }
        return cambios;
    }

    private boolean sincronizarPuertoConfigDesdeProperties(Server server) {
        if (server == null || server.getServerConfig() == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return false;
        }
        Path propertiesPath;
        try {
            propertiesPath = Path.of(server.getServerDir()).resolve("server.properties");
        } catch (RuntimeException e) {
            return false;
        }
        if (!Files.isRegularFile(propertiesPath)) {
            return false;
        }
        try {
            Properties properties = Utilidades.cargarPropertiesUtf8(propertiesPath);
            Integer propertiesPort = parsePort(properties.getProperty("server-port"));
            if (propertiesPort == null || propertiesPort == server.getServerConfig().getPuerto()) {
                return false;
            }
            server.getServerConfig().setPuerto(propertiesPort);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private boolean sincronizarExtensionesServidor(Server server) throws IOException {
        if (server == null) {
            return false;
        }
        List<ServerExtension> actuales = server.getExtensions() == null ? List.of() : List.copyOf(server.getExtensions());
        List<ServerExtension> detectadas = serverExtensionsService.detectInstalledExtensions(server);
        if (actuales.equals(detectadas)) {
            return false;
        }
        server.setExtensions(new ArrayList<>(detectadas));
        return true;
    }

    private void copiarMetadatosOrdenSiFaltan(Server origen, Server destino) {
        if (origen == null || destino == null) return;
        if (destino.getOrdenLista() == null) {
            destino.setOrdenLista(origen.getOrdenLista());
        }
        if (destino.getFavorito() == null) {
            destino.setFavorito(origen.getFavorito());
        }
        if (destino.getOrdenFavorito() == null) {
            destino.setOrdenFavorito(origen.getOrdenFavorito());
        }
    }

    private int valorOrdenListaParaNormalizar(Server server) {
        if (server == null || server.getOrdenLista() == null) {
            return Integer.MAX_VALUE;
        }
        return server.getOrdenLista();
    }

    private int valorOrdenVisual(Integer orden) {
        return orden == null ? Integer.MAX_VALUE : orden;
    }

    private int obtenerSiguienteOrdenLista() {
        int maximo = -1;
        for (Server server : listaServidores) {
            if (server == null || server.getOrdenLista() == null) continue;
            maximo = Math.max(maximo, server.getOrdenLista());
        }
        return maximo + 1;
    }

    private int obtenerSiguienteOrdenFavorito() {
        int maximo = -1;
        for (Server server : listaServidores) {
            if (server == null || server.getOrdenFavorito() == null) continue;
            maximo = Math.max(maximo, server.getOrdenFavorito());
        }
        return maximo + 1;
    }

    private Comparator<Server> comparadorVisualServidores() {
        return Comparator
                .comparing((Server server) -> !Boolean.TRUE.equals(server.getFavorito()))
                .thenComparingInt(server -> Boolean.TRUE.equals(server.getFavorito())
                        ? valorOrdenVisual(server.getOrdenFavorito())
                        : Integer.MAX_VALUE)
                .thenComparingInt(server -> valorOrdenVisual(server.getOrdenLista()))
                .thenComparing(server -> textoOrdenable(server.getDisplayName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(server -> textoOrdenable(server.getServerDir()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(server -> textoOrdenable(server.getId()), String.CASE_INSENSITIVE_ORDER);
    }

    private List<Server> copiarListaServidoresOrdenada() {
        List<Server> ordenados = new ArrayList<>(listaServidores);
        ordenados.sort(comparadorVisualServidores());
        return ordenados;
    }

    private String textoOrdenable(String texto) {
        return texto == null ? "" : texto;
    }

    private String firstNonBlank(String... values) {
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

    private ServerPlatformProfile inspeccionarServidor(Path serverDir) {
        return ServerPlatformAdapters.detect(serverDir);
    }

    public boolean puedeConvertirseAPlataformaCompatible(Server server) {
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

        ServerPlatformProfile profile = inspeccionarServidor(serverDir);
        if (profile != null) {
            aplicarPerfilPlataforma(server, profile);
        }

        ServerPlatform sourcePlatform = profile == null ? server.getPlatform() : profile.platform();
        if (sourcePlatform == null || sourcePlatform == ServerPlatform.UNKNOWN) {
            return false;
        }
        return plataformasObjetivoConversion(sourcePlatform).stream()
                .anyMatch(ServerPlatformAdapter::supportsAutomatedCreation);
    }

    public boolean admiteGestionDeExtensiones(Server server) {
        return serverExtensionsService.supportsManagedExtensions(server);
    }

    public List<Path> obtenerDirectoríosExtensiones(Server server) {
        return serverExtensionsService.getManagedExtensionDirectories(server);
    }

    public List<ServerExtension> detectarExtensionesInstaladas(Server server) throws IOException {
        if (server == null) {
            return List.of();
        }
        return serverExtensionsService.detectInstalledExtensions(server);
    }

    public List<ServerExtension> sincronizarExtensionesInstaladas(Server server) throws IOException {
        if (server == null) {
            return List.of();
        }
        boolean cambios = sincronizarExtensionesServidor(server);
        if (cambios) {
            serverExtensionsService.persistInstalledExtensionCache(server);
            guardarServidor(server);
        }
        return server.getExtensions() == null ? List.of() : List.copyOf(server.getExtensions());
    }

    public ServerExtension instalarExtensionManual(Server server, Path extensionJar) throws IOException {
        if (server == null) {
            throw new IOException("No se ha indicado el servidor.");
        }
        ServerExtension installed = serverExtensionsService.installManualJar(server, extensionJar);
        serverExtensionsService.persistInstalledExtensionCache(server);
        guardarServidor(server);
        return installed;
    }

    public ExtensionCompatibilityReport validarCompatibilidadExtension(Server server, Path extensionJar) throws IOException {
        return serverExtensionsService.validateCompatibility(server, extensionJar);
    }

    public ExtensionCompatibilityReport validarCompatibilidadExtension(Server server, ServerExtension extension) {
        return serverExtensionsService.validateCompatibility(server, extension);
    }

    public InstalledExtensionStatus evaluarEstadoExtensionInstalada(Server server, ServerExtension extension) {
        return serverExtensionsService.assessInstalledExtension(server, extension);
    }

    public Map<ServerExtension, InstalledExtensionStatus> evaluarEstadosExtensionesInstaladas(Server server, List<ServerExtension> extensions) {
        return serverExtensionsService.assessInstalledExtensions(server, extensions);
    }

    public void guardarMetadatosExtensionInstalada(Server server, ServerExtension extension) {
        if (server == null || extension == null) {
            return;
        }
        try {
            serverExtensionsService.persistInstalledExtensionCache(server);
            guardarServidor(server);
        } catch (IOException ex) {
            System.err.println("Error al guardar metadatos de extensión: " + ex.getMessage());
        }
    }

    public boolean eliminarExtensionLocal(Server server, ServerExtension extension) throws IOException {
        if (server == null || extension == null) {
            return false;
        }
        boolean removed = serverExtensionsService.removeExtension(server, extension);
        serverExtensionsService.persistInstalledExtensionCache(server);
        guardarServidor(server);
        return removed;
    }

    public List<ExtensionCatalogProviderDescriptor> obtenerRepositoríosExtensiones() {
        return extensionCatalogService.getAvailableProviders();
    }

    public List<ExtensionCatalogEntry> buscarExtensionesExternas(ExtensionCatalogQuery query) throws IOException {
        return extensionCatalogService.search(query);
    }

    public List<ExtensionCatalogEntry> buscarExtensionesExternas(Server server, String queryText, int limit) throws IOException {
        return extensionCatalogService.search(extensionCatalogService.buildQueryForServer(server, queryText, limit));
    }

    public ExtensionCatalogDetails obtenerDetalleExtensionExterna(String providerId,
                                                                  String projectId,
                                                                  ExtensionCatalogQuery query) throws IOException {
        return extensionCatalogService.getDetails(providerId, projectId, query).orElse(null);
    }

    public ExtensionDownloadPlan prepararDescargaExtensionExterna(String providerId,
                                                                  String projectId,
                                                                  String versionId,
                                                                  Server server) throws IOException {
        return extensionCatalogService.resolveDownload(providerId, projectId, versionId, server).orElse(null);
    }

    public ExtensionInstallResolution evaluarInstalacionExterna(Server server,
                                                                ExtensionCatalogEntry entry) {
        return serverExtensionsService.evaluateCatalogInstallation(server, entry);
    }

    public ExtensionInstallResolution evaluarInstalacionExterna(Server server,
                                                                ExtensionDownloadPlan downloadPlan) {
        return serverExtensionsService.evaluateCatalogInstallation(server, downloadPlan);
    }

    public List<ExtensionUpdateCandidate> buscarActualizacionesExtensiones(Server server) throws IOException {
        List<ExtensionUpdateCandidate> updates = extensionCatalogService.findUpdates(server);
        if (serverExtensionsService.applyUpdateMetadata(server, updates)) {
            serverExtensionsService.persistInstalledExtensionCache(server);
            guardarServidor(server);
        }
        return updates;
    }

    public ServerExtension instalarExtensionExterna(Server server,
                                                    String providerId,
                                                    String projectId,
                                                    String versionId) throws IOException {
        if (server == null) {
            throw new IOException("No se ha indicado el servidor.");
        }
        ExtensionDownloadPlan downloadPlan = prepararDescargaExtensionExterna(providerId, projectId, versionId, server);
        if (downloadPlan == null || !downloadPlan.ready()) {
            throw new IOException("No se ha podido resolver una descarga compatible para la extensión externa.");
        }
        ServerExtension installed = serverExtensionsService.installCatalogDownload(server, downloadPlan, extensionDownloader);
        serverExtensionsService.persistInstalledExtensionCache(server);
        guardarServidor(server);
        return installed;
    }

    public ServerExtension instalarExtensionExterna(Server server,
                                                    ExtensionDownloadPlan downloadPlan) throws IOException {
        if (server == null) {
            throw new IOException("No se ha indicado el servidor.");
        }
        if (downloadPlan == null || !downloadPlan.ready()) {
            throw new IOException("No se ha podido resolver una descarga compatible para la extensión externa.");
        }
        ServerExtension installed = serverExtensionsService.installCatalogDownload(server, downloadPlan, extensionDownloader);
        serverExtensionsService.persistInstalledExtensionCache(server);
        guardarServidor(server);
        return installed;
    }

    public CurseForgeModpackService.ExportResult exportarModpackCurseForge(Server server,
                                                                           Path targetZip,
                                                                           CurseForgeModpackService.ExportMode mode) throws IOException {
        return curseForgeModpackService.exportServerPack(server, targetZip, mode);
    }

    public ModrinthModpackService.ExportResult exportarModpackModrinth(Server server,
                                                                       Path targetPack,
                                                                       ModrinthModpackService.ExportMode mode) throws IOException {
        ModrinthModpackService.ExportResult result = modrinthModpackService.exportServerPack(server, targetPack, mode);
        if (result.resolvedManualFiles() > 0) {
            serverExtensionsService.persistInstalledExtensionCache(server);
            guardarServidor(server);
        }
        return result;
    }

    public CurseForgeModpackService.ImportManifest leerManifestModpackCurseForge(Path sourceZip) throws IOException {
        return curseForgeModpackService.readManifest(sourceZip);
    }

    public ModrinthModpackService.ImportIndex leerIndiceModpackModrinth(Path sourcePack) throws IOException {
        return modrinthModpackService.readIndex(sourcePack);
    }

    public int importarModpackCurseForge(Server server,
                                         Path sourceZip,
                                         List<String> warnings) throws IOException {
        return importarModpackCurseForge(server, sourceZip, ModpackImportConflictPolicy.KEEP_EXISTING, warnings);
    }

    public int importarModpackCurseForge(Server server,
                                         Path sourceZip,
                                         ModpackImportConflictPolicy conflictPolicy,
                                         List<String> warnings) throws IOException {
        return importarModpackCurseForge(server, sourceZip, curseForgeModpackService.readManifest(sourceZip), conflictPolicy, warnings);
    }

    public int importarModpackCurseForge(Server server,
                                         Path sourceZip,
                                         CurseForgeModpackService.ImportManifest manifest,
                                         ModpackImportConflictPolicy conflictPolicy,
                                         List<String> warnings) throws IOException {
        return importarModpackCurseForge(server, sourceZip, manifest, conflictPolicy, warnings, false);
    }

    public int importarModpackCurseForge(Server server,
                                         Path sourceZip,
                                         CurseForgeModpackService.ImportManifest manifest,
                                         ModpackImportConflictPolicy conflictPolicy,
                                         List<String> warnings,
                                         boolean extensionsAlreadySynced) throws IOException {
        if (server == null) {
            throw new IOException("No se ha indicado el servidor.");
        }
        prepararImportacionModpack(server, !extensionsAlreadySynced);
        CurseForgeModpackService.ImportManifest resolvedManifest = manifest == null
                ? curseForgeModpackService.readManifest(sourceZip)
                : manifest;
        List<ModpackDownloadTask> tasks = new ArrayList<>();
        int installed = 0;
        if (warnings != null) {
            String serverVersion = server.getVersion() == null ? null : server.getVersion().trim();
            if (resolvedManifest.minecraftVersion() != null
                    && serverVersion != null
                    && !resolvedManifest.minecraftVersion().equalsIgnoreCase(serverVersion)) {
                warnings.add("El manifest usa Minecraft " + resolvedManifest.minecraftVersion() + " y el servidor actual usa " + serverVersion + ".");
            }
        }
        for (CurseForgeModpackService.ManifestFileRef fileRef : resolvedManifest.files()) {
            if (fileRef == null || !fileRef.required()) {
                continue;
            }
            ExtensionDownloadPlan plan = curseForgeModpackService.resolveDownloadPlan(fileRef.projectID(), fileRef.fileID(), server);
            if (debeOmitirsePlanModpack(server, plan, conflictPolicy, warnings)) {
                continue;
            }
            tasks.add(new ModpackDownloadTask(plan, extensionDownloader));
        }
        ModpackReplaceBackup backup = null;
        if (conflictPolicy == ModpackImportConflictPolicy.REPLACE_EXISTING) {
            validarTareasModpackAntesDeReemplazar(tasks);
            backup = apartarJarsGestionadosParaReemplazoModpack(server);
        }
        try {
            for (ModpackDownloadTask task : tasks) {
                serverExtensionsService.installCatalogDownload(server, task.plan(), task.downloader());
                installed++;
            }
        } catch (IOException | RuntimeException ex) {
            throw restaurarModsTrasFalloDeModpack(server, backup, ex, true);
        }
        confirmarReemplazoModpack(backup, warnings);
        serverExtensionsService.persistInstalledExtensionCache(server);
        guardarServidor(server);
        return installed;
    }

    public int importarModpackModrinth(Server server,
                                       Path sourcePack,
                                       List<String> warnings) throws IOException {
        return importarModpackModrinth(server, sourcePack, ModrinthModpackService.ImportOptions.server(), warnings);
    }

    public int importarModpackModrinth(Server server,
                                       Path sourcePack,
                                       ModrinthModpackService.ImportOptions options,
                                       List<String> warnings) throws IOException {
        return importarModpackModrinth(server, sourcePack, options, ModpackImportConflictPolicy.KEEP_EXISTING, warnings);
    }

    public int importarModpackModrinth(Server server,
                                       Path sourcePack,
                                       ModrinthModpackService.ImportOptions options,
                                       ModpackImportConflictPolicy conflictPolicy,
                                       List<String> warnings) throws IOException {
        return importarModpackModrinth(server, sourcePack, modrinthModpackService.readIndex(sourcePack), options, conflictPolicy, warnings);
    }

    public int importarModpackModrinth(Server server,
                                       Path sourcePack,
                                       ModrinthModpackService.ImportIndex index,
                                       ModrinthModpackService.ImportOptions options,
                                       ModpackImportConflictPolicy conflictPolicy,
                                       List<String> warnings) throws IOException {
        return importarModpackModrinth(server, sourcePack, index, options, conflictPolicy, warnings, false);
    }

    public int importarModpackModrinth(Server server,
                                       Path sourcePack,
                                       ModrinthModpackService.ImportIndex index,
                                       ModrinthModpackService.ImportOptions options,
                                       ModpackImportConflictPolicy conflictPolicy,
                                       List<String> warnings,
                                       boolean extensionsAlreadySynced) throws IOException {
        if (server == null) {
            throw new IOException("No se ha indicado el servidor.");
        }
        ModrinthModpackService.ImportIndex resolvedIndex = index == null ? modrinthModpackService.readIndex(sourcePack) : index;
        if (warnings != null) {
            warnings.addAll(modrinthModpackService.validateDependencies(resolvedIndex, server));
        }
        prepararImportacionModpack(server, !extensionsAlreadySynced);
        List<ModpackDownloadTask> tasks = new ArrayList<>();
        {
            int installed = 0;
            for (ModrinthModpackService.IndexedFile file : resolvedIndex.files()) {
                if (file == null) {
                    continue;
                }
                ModrinthModpackService.ImportFileDecision decision = modrinthModpackService.evaluateImportFile(file, options);
                if (decision.warning() != null && !decision.warning().isBlank() && warnings != null) {
                    warnings.add(decision.warning());
                }
                if (!decision.install()) {
                    continue;
                }
                if (!file.path().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                    if (warnings != null) {
                        warnings.add(file.path() + ": omitido porque no es un .jar gestionable.");
                    }
                    continue;
                }
                if (debeOmitirseArchivoModpackExistente(
                        server,
                        fileNameFromModpackPath(file.path()),
                        conflictPolicy,
                        warnings
                )) {
                    continue;
                }
                ExtensionDownloadPlan plan = modrinthModpackService.resolveImportDownloadPlan(file, server);
                if (plan.projectId() == null || plan.versionId() == null) {
                    if (warnings != null) {
                        warnings.add(file.path() + ": instalado con identidad Modrinth incompleta porque no se pudo resolver el proyecto/versión.");
                    }
                }
                if (debeOmitirsePlanModpack(server, plan, conflictPolicy, warnings)) {
                    continue;
                }
                tasks.add(new ModpackDownloadTask(
                        plan,
                        (url, destination) -> modrinthModpackService.downloadAndVerify(file, url, destination, extensionDownloader)
                ));
            }
            ModpackReplaceBackup backup = null;
            if (conflictPolicy == ModpackImportConflictPolicy.REPLACE_EXISTING) {
                validarTareasModpackAntesDeReemplazar(tasks);
                backup = apartarJarsGestionadosParaReemplazoModpack(server);
            }
            try {
                for (ModpackDownloadTask task : tasks) {
                    serverExtensionsService.installCatalogDownload(server, task.plan(), task.downloader());
                    installed++;
                }
            } catch (IOException | RuntimeException ex) {
                throw restaurarModsTrasFalloDeModpack(server, backup, ex, true);
            }
            confirmarReemplazoModpack(backup, warnings);
            int overrides = modrinthModpackService.extractOverrides(sourcePack, server, options, warnings);
            if (overrides > 0 && warnings != null) {
                warnings.add("Se han extraído " + overrides + " archivos de override compatibles.");
            }
            serverExtensionsService.persistInstalledExtensionCache(server);
            guardarServidor(server);
            return installed;
        }
    }

    private void prepararImportacionModpack(Server server,
                                            boolean refreshExtensions) throws IOException {
        if (refreshExtensions) {
            sincronizarExtensionesServidor(server);
        }
    }

    private boolean debeOmitirsePlanModpack(Server server,
                                            ExtensionDownloadPlan plan,
                                            ModpackImportConflictPolicy conflictPolicy,
                                            List<String> warnings) throws IOException {
        ExtensionInstallResolution resolution = serverExtensionsService.evaluateCatalogInstallation(server, plan);
        if (plan == null || !plan.ready()) {
            throw new IOException("El modpack contiene una descarga que no se ha podido resolver.");
        }
        if (resolution == null || resolution.state() == ExtensionInstallResolutionState.AVAILABLE) {
            return false;
        }
        if (resolution.state() == ExtensionInstallResolutionState.INCOMPATIBLE) {
            throw new IOException(firstNonBlank(resolution.message(), "El modpack contiene un mod incompatible con este servidor."));
        }
        if (conflictPolicy == ModpackImportConflictPolicy.REPLACE_EXISTING) {
            return false;
        }
        if (warnings != null) {
            warnings.add(firstNonBlank(
                    resolution.message(),
                    firstNonBlank(plan == null ? null : plan.fileName(), "Mod") + ": omitido porque ya existe en el servidor."
            ));
        }
        return true;
    }

    private boolean debeOmitirseArchivoModpackExistente(Server server,
                                                        String fileName,
                                                        ModpackImportConflictPolicy conflictPolicy,
                                                        List<String> warnings) throws IOException {
        if (conflictPolicy == ModpackImportConflictPolicy.REPLACE_EXISTING
                || fileName == null
                || fileName.isBlank()) {
            return false;
        }
        ExtensionDownloadPlan fileProbe = new ExtensionDownloadPlan(
                "modrinth",
                null,
                null,
                null,
                null,
                fileName,
                null,
                ExtensionSourceType.MODRINTH,
                ServerExtensionType.MOD,
                server == null || server.getPlatform() == null ? ServerPlatform.UNKNOWN : server.getPlatform(),
                server == null ? null : server.getVersion(),
                true,
                null
        );
        ExtensionInstallResolution resolution = serverExtensionsService.evaluateCatalogInstallation(server, fileProbe);
        if (resolution == null || resolution.state() == ExtensionInstallResolutionState.AVAILABLE) {
            return false;
        }
        if (resolution.state() == ExtensionInstallResolutionState.INCOMPATIBLE) {
            throw new IOException(firstNonBlank(resolution.message(), "El modpack contiene un mod incompatible con este servidor."));
        }
        if (warnings != null) {
            warnings.add(firstNonBlank(
                    resolution.message(),
                    fileName + ": omitido porque ya existe en el servidor."
            ));
        }
        return true;
    }

    private String fileNameFromModpackPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return fileName.isBlank() ? null : fileName;
    }

    private void validarTareasModpackAntesDeReemplazar(List<ModpackDownloadTask> tasks) throws IOException {
        if (tasks == null || tasks.isEmpty()) {
            throw new IOException("No se han encontrado mods instalables en el modpack seleccionado. No se eliminarán los mods actuales.");
        }
        Set<String> targetFileNames = new HashSet<>();
        for (ModpackDownloadTask task : tasks) {
            ExtensionDownloadPlan plan = task == null ? null : task.plan();
            if (plan == null || !plan.ready()) {
                throw new IOException("El modpack contiene una descarga que no se ha podido resolver.");
            }
            String fileName = plan.fileName();
            if (fileName == null || fileName.isBlank() || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                throw new IOException("El modpack contiene una entrada sin nombre de archivo .jar válido.");
            }
            String key = fileName.toLowerCase(java.util.Locale.ROOT);
            if (!targetFileNames.add(key)) {
                throw new IOException("El modpack contiene entradas duplicadas para " + fileName + ".");
            }
        }
    }

    // Aparta los mods actuales a una carpeta de respaldo en lugar de borrarlos: si la importación falla
    // a medias (red, hash, API) se pueden restaurar y el servidor no se queda con medio modpack.
    private ModpackReplaceBackup apartarJarsGestionadosParaReemplazoModpack(Server server) throws IOException {
        if (server == null) {
            throw new IOException("No se ha indicado el servidor.");
        }
        if (server.getServerProcess() != null && server.getServerProcess().isAlive()) {
            throw new IOException("Detén el servidor antes de reemplazar los mods actuales.");
        }
        Path serverDir = resolverDirectorioServidor(server);
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            throw new IOException("No se ha encontrado la carpeta del servidor.");
        }
        Path normalizedServerDir = serverDir.toAbsolutePath().normalize();
        List<Path> extensionDirs = serverExtensionsService.getManagedExtensionDirectories(server);
        List<Path> jars = new ArrayList<>();
        for (Path extensionDir : extensionDirs) {
            if (extensionDir == null || !Files.isDirectory(extensionDir)) {
                continue;
            }
            Path normalizedExtensionDir = extensionDir.toAbsolutePath().normalize();
            if (!normalizedExtensionDir.startsWith(normalizedServerDir)) {
                throw new IOException("La carpeta de extensiones no está dentro de la carpeta del servidor: " + extensionDir);
            }
            try (Stream<Path> children = Files.list(extensionDir)) {
                jars.addAll(children
                        .filter(path -> esJarGestionadoSeguro(normalizedServerDir, normalizedExtensionDir, path))
                        .map(path -> path.toAbsolutePath().normalize())
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .toList());
            }
        }

        List<ServerExtension> previousExtensions = server.getExtensions() == null
                ? new ArrayList<>()
                : new ArrayList<>(server.getExtensions());
        String marca = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backupDir = normalizedServerDir.resolve(MODPACK_BACKUP_DIR).resolve("mods-" + marca + "-" + UUID.randomUUID().toString().substring(0, 8));
        ModpackReplaceBackup backup = new ModpackReplaceBackup(
                backupDir,
                normalizedServerDir,
                new ArrayList<>(),
                previousExtensions,
                List.copyOf(extensionDirs)
        );
        try {
            for (Path jar : jars) {
                Path respaldo = backupDir.resolve(normalizedServerDir.relativize(jar));
                Files.createDirectories(respaldo.getParent());
                try {
                    Files.move(jar, respaldo);
                } catch (IOException ex) {
                    throw new IOException("No se ha podido apartar el mod actual " + jar.getFileName()
                            + ". Detén el servidor y cierra cualquier programa que esté usando ese archivo.", ex);
                }
                backup.movedJars().add(jar);
            }
        } catch (IOException | RuntimeException ex) {
            // Nada se ha instalado todavía: solo devolvemos a su sitio lo que ya se hubiera movido,
            // sin borrar los jars que siguen en la carpeta (son los originales que no llegaron a moverse).
            throw restaurarModsTrasFalloDeModpack(server, backup, ex, false);
        }
        server.setExtensions(serverExtensionsService.detectInstalledExtensions(server));
        serverExtensionsService.persistInstalledExtensionCache(server);
        return backup;
    }

    // La importación ha terminado bien: ya no hace falta el respaldo de los mods anteriores.
    private void confirmarReemplazoModpack(ModpackReplaceBackup backup, List<String> warnings) {
        if (backup == null) {
            return;
        }
        int total = backup.movedJars().size();
        try {
            FileSystemSafety.deleteDirectoryTree(backup.backupDir());
            Path parent = backup.backupDir().getParent();
            if (parent != null && Files.isDirectory(parent)) {
                try (Stream<Path> remaining = Files.list(parent)) {
                    if (remaining.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            if (warnings != null) {
                warnings.add("No se ha podido borrar el respaldo temporal de los mods anteriores: " + backup.backupDir());
            }
        }
        if (total > 0 && warnings != null) {
            warnings.add("Se han eliminado " + total + (total == 1
                    ? " mod actual antes de importar el modpack."
                    : " mods actuales antes de importar el modpack."));
        }
    }

    // Deshace un reemplazo fallido: quita lo instalado a medias y devuelve los mods anteriores a su sitio.
    // Devuelve la excepción que debe relanzarse, con el contexto de la restauración.
    private IOException restaurarModsTrasFalloDeModpack(Server server,
                                                        ModpackReplaceBackup backup,
                                                        Exception cause,
                                                        boolean borrarInstaladosParciales) {
        IOException original = cause instanceof IOException io
                ? io
                : new IOException(firstNonBlank(cause.getMessage(), "Error inesperado al importar el modpack."), cause);
        if (backup == null) {
            return original;
        }
        try {
            for (Path extensionDir : borrarInstaladosParciales ? backup.extensionDirs() : List.<Path>of()) {
                if (extensionDir == null || !Files.isDirectory(extensionDir)) {
                    continue;
                }
                Path normalizedExtensionDir = extensionDir.toAbsolutePath().normalize();
                List<Path> parciales;
                try (Stream<Path> children = Files.list(extensionDir)) {
                    parciales = children
                            .filter(path -> esJarGestionadoSeguro(backup.serverDir(), normalizedExtensionDir, path))
                            .toList();
                }
                for (Path parcial : parciales) {
                    Files.deleteIfExists(parcial);
                }
            }
            for (Path jar : backup.movedJars()) {
                Path respaldo = backup.backupDir().resolve(backup.serverDir().relativize(jar));
                if (Files.isRegularFile(respaldo, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(jar.getParent());
                    Files.move(respaldo, jar, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            FileSystemSafety.deleteDirectoryTree(backup.backupDir());
            Path parent = backup.backupDir().getParent();
            if (parent != null && Files.isDirectory(parent)) {
                try (Stream<Path> remaining = Files.list(parent)) {
                    if (remaining.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
            server.setExtensions(new ArrayList<>(backup.previousExtensions()));
            server.setExtensions(serverExtensionsService.detectInstalledExtensions(server));
            serverExtensionsService.persistInstalledExtensionCache(server);
            return new IOException(firstNonBlank(original.getMessage(), "La importación del modpack ha fallado.")
                    + "\nNo se ha cambiado nada: se han restaurado los mods que había antes de importar.", original);
        } catch (IOException | RuntimeException restoreFailure) {
            IOException failure = new IOException(firstNonBlank(original.getMessage(), "La importación del modpack ha fallado.")
                    + "\nAdemás, no se han podido restaurar todos los mods anteriores. Siguen guardados en: "
                    + backup.backupDir(), original);
            failure.addSuppressed(restoreFailure);
            return failure;
        }
    }

    private record ModpackReplaceBackup(Path backupDir,
                                        Path serverDir,
                                        List<Path> movedJars,
                                        List<ServerExtension> previousExtensions,
                                        List<Path> extensionDirs) {
    }

    private boolean esJarGestionadoSeguro(Path normalizedServerDir, Path normalizedExtensionDir, Path path) {
        if (path == null || Files.isSymbolicLink(path)) {
            return false;
        }
        Path normalizedPath = path.toAbsolutePath().normalize();
        return normalizedPath.startsWith(normalizedServerDir)
                && normalizedPath.startsWith(normalizedExtensionDir)
                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar");
    }

    private record ModpackDownloadTask(ExtensionDownloadPlan plan, FileDownloader downloader) {
    }

    private Path resolverDirectorioServidor(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return null;
        }
        try {
            return Path.of(server.getServerDir());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void aplicarPerfilPlataforma(Server server, ServerPlatformProfile profile) {
        if (server == null || profile == null) return;
        server.setPlatform(profile.platform());
        server.setLoader(profile.loader());
        server.setLoaderVersion(profile.loaderVersion());
        server.setEcosystemType(profile.ecosystemType());
        server.setCapabilities(profile.capabilities());
        if (profile.minecraftVersion() != null
                && MINECRAFT_VERSION_PATTERN.matcher(profile.minecraftVersion().trim()).matches()) {
            server.setVersion(profile.minecraftVersion());
        }
    }

    private ServerPlatformAdapter resolverAdaptador(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return ServerPlatformAdapters.forPlatform(null);
        }

        Path serverDir = Path.of(server.getServerDir());
        ServerPlatformProfile profile = inspeccionarServidor(serverDir);
        if (profile != null) {
            aplicarPerfilPlataforma(server, profile);
            return ServerPlatformAdapters.forPlatform(profile.platform());
        }
        return ServerPlatformAdapters.forPlatform(server.getPlatform());
    }

    // esta función de encarga de comprobar si los servidores almacenados son correctos, si no lo son se eliminan
    private void validarYLimpiarServidoresPersistidos() {
        if ((listaServidores == null || listaServidores.isEmpty()) && servidoresNoDisponibles.isEmpty()) return;

        // Revisamos también los que no estaban disponibles: puede que su carpeta haya vuelto.
        List<Server> candidatos = new ArrayList<>();
        if (listaServidores != null) {
            candidatos.addAll(listaServidores);
        }
        candidatos.addAll(servidoresNoDisponibles);
        Set<String> idsNoDisponiblesAntes = new HashSet<>();
        for (Server server : servidoresNoDisponibles) {
            if (server != null && server.getId() != null) {
                idsNoDisponiblesAntes.add(server.getId());
            }
        }

        List<Server> cargables = new ArrayList<>(); // aquí almacenamos los correctos
        List<Server> noCargables = new ArrayList<>(); // aquí almacenamos los que no se pueden mostrar ahora

        // si han ocurrido cambios se los notificaremos al usuario
        boolean cambios = false;

        for (Server server : candidatos) {
            if (server == null) continue;
            // si el servidor no es cargable lo apartamos en noCargables, pero NO lo borramos del JSON
            if (esServidorCargable(server)) {
                try {
                    ServerPlatformProfile profile = inspeccionarServidor(Path.of(server.getServerDir()));
                    if (profile != null) {
                        aplicarPerfilPlataforma(server, profile);
                        cambios = true;
                    }
                } catch (RuntimeException ignored) {
                    // si no se puede detectar, no bloqueamos la carga
                }
                cargables.add(server);
            } else {
                noCargables.add(server);
            }
        }

        List<Server> nuevosNoCargables = noCargables.stream()
                .filter(server -> server.getId() == null || !idsNoDisponiblesAntes.contains(server.getId()))
                .toList();
        boolean recuperados = cargables.stream()
                .anyMatch(server -> server.getId() != null && idsNoDisponiblesAntes.contains(server.getId()));

        listaServidores = cargables;
        servidoresNoDisponibles.clear();
        servidoresNoDisponibles.addAll(noCargables);
        if (normalizarMetadatosOrden(true)) {
            cambios = true;
        }

        if (nuevosNoCargables.isEmpty() && !recuperados && !cambios) return;

        guardarServidores();
        notificarCambio();

        if (nuevosNoCargables.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("No se han podido cargar ")
                .append(nuevosNoCargables.size())
                .append(" servidores guardados (carpeta inexistente o servidor inválido).")
                .append("\nNo se mostrarán, pero se conservan en " + JSON_FILE
                        + " y volverán a aparecer cuando su carpeta esté disponible.");

        // mostramos un máximo de 8 servidores que no se han podido cargar
        int maxDetalles = 8;
        int mostrados = 0;
        for (Server server : nuevosNoCargables) {
            if (mostrados >= maxDetalles) break;
            String nombre = server.getDisplayName() == null ? "(sin nombre)" : server.getDisplayName();
            String dir = server.getServerDir() == null ? "(sin carpeta)" : server.getServerDir();
            sb.append("\n- ").append(nombre).append(" [").append(dir).append("]");
            mostrados++;
        }
        if (nuevosNoCargables.size() > maxDetalles) {
            sb.append("\n... y ").append(nuevosNoCargables.size() - maxDetalles).append(" más.");
        }

        avisoServidoresNoCargados = sb.toString();
    }

    // Servidores guardados que ahora mismo no se pueden cargar; siguen persistidos en el JSON.
    public List<Server> getServidoresNoDisponibles() {
        return List.copyOf(servidoresNoDisponibles);
    }

    // Olvida definitivamente un servidor no disponible (lo quita del JSON). No toca su carpeta.
    public boolean olvidarServidorNoDisponible(String serverId) {
        if (serverId == null) return false;
        boolean removed = servidoresNoDisponibles.removeIf(server -> server != null && serverId.equals(server.getId()));
        if (removed) {
            guardarServidores();
        }
        return removed;
    }

    public void refrescarServidoresGuardados() {
        validarYLimpiarServidoresPersistidos();
        if (automationService != null) {
            automationService.refreshSchedules();
        }
        if (servidorSeleccionado != null && getServerById(servidorSeleccionado.getId()) == null) {
            servidorSeleccionado = null;
            notificarEstadoServidor(null);
        }
    }

    // Si hay servidores que no han podido ser cargados se notifica, si no, pasamos directamente al programa
    public void mostrarAvisoArranqueSiProcede(Component parent) {
        mostrarAvisoListaIlegibleSiProcede(parent);
        if (avisoServidoresNoCargados == null || avisoServidoresNoCargados.isBlank()) return;
        String mensaje = avisoServidoresNoCargados;
        avisoServidoresNoCargados = null;
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                parent,
                mensaje,
                "Servidores no cargados",
                JOptionPane.WARNING_MESSAGE
        ));
    }

    private void mostrarAvisoListaIlegibleSiProcede(Component parent) {
        if (avisoListaServidoresIlegible == null || avisoListaServidoresIlegible.isBlank()) return;
        String mensaje = avisoListaServidoresIlegible;
        avisoListaServidoresIlegible = null;
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println(mensaje);
            return;
        }
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                parent,
                mensaje,
                "Lista de servidores ilegible",
                JOptionPane.ERROR_MESSAGE
        ));
    }

    String getAvisoListaServidoresIlegible() {
        return avisoListaServidoresIlegible;
    }

    // Consideramos "cargable" si hay exactamente un .jar y parece un server jar de Minecraft
    private boolean esServidorCargable(Server server) {
        if (server == null) return false;
        if (server.getServerDir() == null || server.getServerDir().isBlank()) return false;

        Path dir = Path.of(server.getServerDir());
        if (!Files.isDirectory(dir)) return false;
        ServerPlatformProfile profile = inspeccionarServidor(dir);
        ServerPlatformAdapter adapter = ServerPlatformAdapters.forPlatform(profile == null ? server.getPlatform() : profile.platform());
        ServerValidationResult validation = adapter.validate(dir);
        if (validation.valid() && profile != null) {
            aplicarPerfilPlataforma(server, profile);
        }
        return validation.valid();
    }
    // ===== LISTENERS Y CAMBIOS =====

    // Esto permite que otros escuchen
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }


    // Añade un listener SOLO para una propiedad concreta (ej: "serverState")
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    // Esto permite que dejen de escuchar
    public void removePropertyChangeListener(PropertyChangeListener listener){
        pcs.removePropertyChangeListener(listener);
    }

    // Quita el listener de una propiedad concreta
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }

    // este método notifica a los oyentes de que ha ocurrido un cambio en la lista de servidores
    private void notificarCambio(){
        pcs.firePropertyChange("listaServidores", null, getListaServidores());
    }

    // este método notifica a los oyentes de que ha ocurrido un cambio en el estado del servidor
    public void notificarEstadoServidor(Server server){
        try {
            if (automationService != null) {
                automationService.onServerStateChanged(server);
            }
            pcs.firePropertyChange("estadoServidor", null, server);
        } catch (RuntimeException ex) {
            AppErrorReporter.report("Error notificando el estado del servidor.", ex);
        }
    }

    public void notificarConfiguracionServidor(Server server){
        pcs.firePropertyChange("configuracionServidor", null, server);
    }

    public synchronized ServerAutomationService getAutomationService() {
        if (automationService == null) {
            automationService = new ServerAutomationService(this);
        }
        return automationService;
    }

    public void iniciarAutomatizaciones() {
        getAutomationService().start();
    }

    public synchronized void detenerAutomatizaciones() {
        if (automationService == null) {
            return;
        }
        automationService.close();
        automationService = null;
    }

    public List<ServerAutomationRule> getReglasAutomatizacion(Server server) {
        if (server == null || server.getAutomationRules() == null) {
            return List.of();
        }
        List<ServerAutomationRule> rules = server.getAutomationRules().stream()
                .filter(Objects::nonNull)
                .toList();
        return List.copyOf(rules);
    }

    public void reemplazarReglasAutomatizacion(Server server, List<ServerAutomationRule> rules) {
        if (server == null) {
            return;
        }
        List<ServerAutomationRule> normalizadas = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        if (rules != null) {
            for (ServerAutomationRule rule : rules) {
                if (rule != null) {
                    normalizadas.add(normalizarReglaAutomatizacion(rule, usedIds));
                }
            }
        }
        server.setAutomationRules(normalizadas);
        guardarServidor(server);
        notificarAutomatizacionesServidor(server);
    }

    public void guardarReglaAutomatizacion(Server server, ServerAutomationRule rule) {
        if (server == null || rule == null) {
            return;
        }
        Set<String> usedIds = new HashSet<>();
        if (server.getAutomationRules() != null) {
            for (ServerAutomationRule existing : server.getAutomationRules()) {
                if (existing != null && !Objects.equals(existing.getId(), rule.getId())) {
                    usedIds.add(existing.getId());
                }
            }
        }
        ServerAutomationRule normalizada = normalizarReglaAutomatizacion(rule, usedIds);
        List<ServerAutomationRule> rules = new ArrayList<>(server.getAutomationRules() == null ? List.of() : server.getAutomationRules());
        int existingIndex = -1;
        for (int i = 0; i < rules.size(); i++) {
            ServerAutomationRule existing = rules.get(i);
            if (existing != null && Objects.equals(existing.getId(), normalizada.getId())) {
                existingIndex = i;
                break;
            }
        }
        if (existingIndex >= 0) {
            rules.set(existingIndex, normalizada);
        } else {
            rules.add(normalizada);
        }
        server.setAutomationRules(rules);
        guardarServidor(server);
        notificarAutomatizacionesServidor(server);
    }

    public boolean eliminarReglaAutomatizacion(Server server, String ruleId) {
        if (server == null || ruleId == null || server.getAutomationRules() == null) {
            return false;
        }
        List<ServerAutomationRule> rules = new ArrayList<>(server.getAutomationRules());
        boolean removed = rules.removeIf(rule -> rule != null && Objects.equals(rule.getId(), ruleId));
        if (!removed) {
            return false;
        }
        server.setAutomationRules(rules);
        guardarServidor(server);
        notificarAutomatizacionesServidor(server);
        return true;
    }

    private ServerAutomationRule normalizarReglaAutomatizacion(ServerAutomationRule rule, Set<String> usedIds) {
        String id = rule.getId();
        if (id == null || id.isBlank() || (usedIds != null && usedIds.contains(id))) {
            id = UUID.randomUUID().toString();
            rule.setId(id);
        }
        if (usedIds != null) {
            usedIds.add(id);
        }
        if (rule.getEnabled() == null) {
            rule.setEnabled(false);
        }
        return rule;
    }

    private void notificarAutomatizacionesServidor(Server server) {
        if (automationService != null) {
            automationService.onServerRulesChanged(server);
        }
        pcs.firePropertyChange("automatizacionesServidor", null, server);
    }

    // ===== FUNCIONES Y MÉTODOS =====

    public Server crearServidor(){
        ServerCreationWizardResult wizardResult = mostrarAsistenteCreacionServidor();
        if (wizardResult == null) return null;
        return crearServidorAutomatizado(
                wizardResult.adapter(),
                wizardResult.option(),
                wizardResult.targetDirectory().toPath()
        );
    }

    public Server convertirServidorAPlataformaCompatible(Server server) {
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
        ServerPlatformProfile sourceProfile = inspeccionarServidor(serverDir);
        if (sourceProfile != null) {
            aplicarPerfilPlataforma(server, sourceProfile);
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

    private String textoODesconocido(String value) {
        return value == null || value.isBlank() ? "desconocida" : value;
    }

    private List<ServerCreationOption> cargarOpcionesCreacion(ServerPlatformAdapter adapter) {
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

    Server crearServidorAutomatizado(ServerPlatformAdapter adapter, ServerCreationOption option, Path targetDirectory) {
        if (adapter == null || option == null || targetDirectory == null) {
            return null;
        }

        try {
            Files.createDirectories(targetDirectory);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    null,
                    "No se ha podido crear la carpeta del servidor: " + e.getMessage(),
                    "Instalación",
                    JOptionPane.ERROR_MESSAGE
            );
            return null;
        }

        try {
            DialogoTareaEnCurso.ejecutar(
                    "Instalando " + adapter.getCreationDisplayName(),
                    "Preparando servidor " + option.displayName() + "...",
                    () -> {
                        Server transientServer = new Server();
                        adapter.install(transientServer, new ServerInstallationRequest(
                                targetDirectory,
                                option.minecraftVersion(),
                                option.platformVersion(),
                                true,
                                resolverIconoPorDefecto(),
                                MOJANG_API,
                                (url, destination) -> MOJANG_API.descargar(url, destination, null)
                        ));
                        return transientServer;
                    }
            );
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    null,
                    "No se ha podido instalar el servidor: " + ErroresDescarga.mensaje(e),
                    "Instalación",
                    JOptionPane.ERROR_MESSAGE
            );
            return null;
        }

        Server server = new Server();
        server.setServerDir(targetDirectory.toAbsolutePath().toString());
        aplicarPerfilPlataforma(server, inspeccionarServidor(targetDirectory));
        if (server.getVersion() == null || server.getVersion().isBlank()) {
            server.setVersion(option.minecraftVersion());
        }
        if (server.getPlatform() == null || server.getPlatform() == modelo.extensions.ServerPlatform.UNKNOWN) {
            server.setPlatform(option.platform());
        }
        server.setDisplayName(construirNombreServidorImportado(server.getVersion(), server.getServerDir(), false));
        try {
            sincronizarExtensionesServidor(server);
        } catch (IOException ignored) {
        }
        guardarServidor(server);
        GestorMundos.sincronizarMundosServidor(server);
        return server;
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
                                resolverIconoPorDefecto(),
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

            ServerPlatformProfile targetProfile = inspeccionarServidor(serverDir);
            if (targetProfile != null && targetProfile.platform() == option.platform()) {
                aplicarPerfilPlataforma(server, targetProfile);
            } else {
                aplicarMetadatosPlataformaDestino(server, targetAdapter, option);
            }
            if (server.getLoaderVersion() == null || server.getLoaderVersion().isBlank()) {
                server.setLoaderVersion(option.platformVersion());
            }

            revisarCacheExtensionesTrasConversion(server, sourceEcosystem, targetEcosystem);
            guardarServidor(server);
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
        sincronizarExtensionesServidor(server);
        serverExtensionsService.persistInstalledExtensionCache(server);
    }

    private Path resolverIconoPorDefecto() {
        try (InputStream in = GestorServidores.class.getResourceAsStream("/doraimages/default_image.png")) {
            if (in == null) {
                return null;
            }
            Path tempFile = Files.createTempFile("dora-default-icon", ".png");
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit();
            return tempFile;
        } catch (IOException e) {
            return null;
        }
    }

    // Esta función es la encargada de importar un servidor a partir de una carpeta
    public Server importarServidor(){
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle("Selecciona el directorio del servidor");

        // pedimos al usuario que indique la carpeta
        if(chooser.showDialog(chooser,"Seleccionar") == JFileChooser.APPROVE_OPTION){
            return importarServidorDesdeDirectorio(chooser.getSelectedFile().toPath());
        }
        return null;
    }

    Server importarServidorDesdeDirectorio(Path directorio) {
        if (directorio == null) return null;

        Path directorioNormalizadoPath = normalizarDirectorioServidor(directorio);
        if (directorioNormalizadoPath == null || !Files.isDirectory(directorioNormalizadoPath)) {
            System.out.println("El directorio seleccionado no existe o no es una carpeta válida.");
            return null;
        }

        String directorioNormalizado = directorioNormalizadoPath.toString();
        for (Server existente : listaServidores) {
            if (existente == null || existente.getServerDir() == null) continue;
            Path existentePath;
            try {
                existentePath = normalizarDirectorioServidor(Path.of(existente.getServerDir()));
            } catch (RuntimeException e) {
                continue;
            }
            if (Objects.equals(existentePath, directorioNormalizadoPath)) {
                System.out.println("El servidor ya está importado.");
                return existente;
            }
        }

        ServerPlatformProfile profile = inspeccionarServidor(directorioNormalizadoPath);
        if (profile == null) {
            System.out.println("No se ha podido detectar una plataforma válida para el servidor importado.");
            return null;
        }

        Server server = new Server();
        server.setServerDir(directorioNormalizado);
        aplicarPerfilPlataforma(server, profile);
        if (server.getVersion() == null || server.getVersion().isBlank()) {
            server.setVersion(DetectorVersionServidor.detectarVersionVanilla(server));
        }
        server.setDisplayName(construirNombreServidorImportado(server.getVersion(), server.getServerDir(), false));

        guardarServidor(server);
        GestorMundos.sincronizarMundosServidor(server);
        try {
            sincronizarExtensionesServidor(server);
            guardarServidor(server);
        } catch (IOException ignored) {
        }
        return server;
    }

    private Path normalizarDirectorioServidor(Path directorio) {
        if (directorio == null) {
            return null;
        }
        try {
            return directorio.toRealPath();
        } catch (IOException | RuntimeException e) {
            try {
                return directorio.toAbsolutePath().normalize();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    // Guarda la lista de servidores en el JSON
    public void guardarServidores(){
        normalizarMetadatosOrden(true);
        try{
            File parent = jsonFile.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            // Los no disponibles se guardan también: solo están ocultos, no eliminados.
            List<Server> persistidos = copiarListaServidoresOrdenada();
            persistidos.addAll(servidoresNoDisponibles);
            Utilidades.escribirArchivoAtomico(
                    jsonFile.toPath(),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(persistidos)
            );
        } catch (Exception e) {
            System.err.println("Error al guardar servidores: " + e.getMessage());
            AppErrorReporter.report("No se ha podido guardar " + JSON_FILE + ".", e);
        }
    }

    public List<Server> getListaServidores() {
        normalizarMetadatosOrden(false);
        return copiarListaServidoresOrdenada();
    }

    public void setListaServidores(List<Server> listaServidores) {
        this.listaServidores = listaServidores == null ? new ArrayList<>() : new ArrayList<>(listaServidores);
        normalizarMetadatosOrden(true);
    }

    // Guarda un sólo servidor en la lista de servidores y luego lo guarda en el JSON
    public void guardarServidor(Server server){
        if (server == null) return;
        if (listaServidores == null) {
            listaServidores = new ArrayList<>();
        }

        asegurarMetadatosPersistentes(server);

        int indiceExistente = -1;
        for (int i = 0; i < listaServidores.size(); i++) {
            Server actual = listaServidores.get(i);
            if (actual == null) continue;
            if (Objects.equals(actual.getId(), server.getId())) {
                indiceExistente = i;
                copiarMetadatosOrdenSiFaltan(actual, server);
                break;
            }
        }

        if (indiceExistente >= 0) {
            listaServidores.set(indiceExistente, server);
        } else {
            if (server.getOrdenLista() == null) {
                server.setOrdenLista(obtenerSiguienteOrdenLista());
            }
            listaServidores.add(server);
        }

        if (Boolean.TRUE.equals(server.getFavorito()) && server.getOrdenFavorito() == null) {
            server.setOrdenFavorito(obtenerSiguienteOrdenFavorito());
        }

        guardarServidores();
        notificarCambio();
    }

    public void establecerFavorito(Server server, boolean favorito) {
        if (server == null) return;

        Server serverPersistido = getServerById(server.getId());
        if (serverPersistido == null) return;
        if (Boolean.valueOf(favorito).equals(serverPersistido.getFavorito())) return;

        serverPersistido.setFavorito(favorito);
        if (favorito && serverPersistido.getOrdenFavorito() == null) {
            serverPersistido.setOrdenFavorito(obtenerSiguienteOrdenFavorito());
        }

        guardarServidor(serverPersistido);
    }

    public void reordenarServidores(List<String> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) return;
        normalizarMetadatosOrden(false);

        Set<String> idsFavoritosAplicados = new HashSet<>();
        int ordenFavorito = 0;
        for (String id : serverIds) {
            Server server = getServerById(id);
            if (server == null || !Boolean.TRUE.equals(server.getFavorito())) continue;
            if (!idsFavoritosAplicados.add(id)) continue;
            server.setOrdenFavorito(ordenFavorito++);
        }

        List<Server> favoritosRestantes = new ArrayList<>(listaServidores);
        favoritosRestantes.sort(Comparator.comparingInt(server -> valorOrdenVisual(server.getOrdenFavorito())));
        for (Server server : favoritosRestantes) {
            if (server == null || !Boolean.TRUE.equals(server.getFavorito())) continue;
            if (!idsFavoritosAplicados.add(server.getId())) continue;
            server.setOrdenFavorito(ordenFavorito++);
        }

        Set<String> idsAplicados = new HashSet<>();
        int orden = 0;
        for (String id : serverIds) {
            Server server = getServerById(id);
            if (server == null) continue;
            if (!idsAplicados.add(id)) continue;
            if (Boolean.TRUE.equals(server.getFavorito())) continue;
            server.setOrdenLista(orden++);
        }

        List<Server> resto = new ArrayList<>(listaServidores);
        resto.sort(Comparator.comparingInt(server -> valorOrdenVisual(server.getOrdenLista())));
        for (Server server : resto) {
            if (server == null) continue;
            if (!idsAplicados.add(server.getId())) continue;
            if (Boolean.TRUE.equals(server.getFavorito())) continue;
            server.setOrdenLista(orden++);
        }

        guardarServidores();
        notificarCambio();
    }

    // Elimina un servidor de la lista de servidores y del JSON, no toca los archivos del servidor
    public boolean eliminarServidor(Server server){
        if(server == null) return false;
        if(server.getServerProcess() != null && server.getServerProcess().isAlive()){
            return false; // no podemos eliminarlo mientras está en ejecución para no dejar procesos abiertos
        }

        boolean removed = listaServidores.removeIf(s -> Objects.equals(s.getId(), server.getId()));
        if(!removed) return false;

        normalizarMetadatosOrden(true);

        if(servidorSeleccionado != null && Objects.equals(servidorSeleccionado.getId(), server.getId())){
            servidorSeleccionado = null;
        }
        // Una vez eliminado de la lista de servidores guardamos el JSON
        guardarServidores();
        if (automationService != null) {
            automationService.refreshSchedules();
        }
        notificarCambio();
        return true;
    }

    // Obtener una lista de todos los servidores activos
    public List<Server> getServidoresActivos() {
        if (listaServidores == null || listaServidores.isEmpty()) return List.of();
        return listaServidores.stream()
                .filter(s -> s != null && s.getServerProcess() != null && s.getServerProcess().isAlive())
                .toList();
    }

    // Pedir una parada segura ("stop") a todos los servidores activos para salir del programa.
    // Devuelve los servidores a los que se ha pedido la parada.
    public List<Server> pedirParadaServidoresActivosParaSalir() {
        List<Server> activos = getServidoresActivos();
        for (Server server : activos) {
            try {
                safePararServidor(server);
            } catch (RuntimeException ignored) {
            }
        }
        return activos;
    }

    // Esperar hasta "espera" a que los servidores indicados terminen (incluidos procesos hijos).
    // Devuelve los que siguen vivos. No fuerza ningún cierre: guardar el mundo puede tardar.
    public List<Server> esperarParadaServidores(List<Server> servidores, Duration espera) throws InterruptedException {
        long limite = System.nanoTime() + Math.max(0L, espera.toNanos());
        for (Server server : servidores) {
            Process proceso = server == null ? null : server.getServerProcess();
            if (proceso == null) continue;
            long restante = limite - System.nanoTime();
            if (restante <= 0) break;
            proceso.waitFor(restante, TimeUnit.NANOSECONDS);
        }
        return servidores.stream()
                .filter(s -> s != null && ProcessTreeTerminator.isTreeAlive(s.getServerProcess()))
                .toList();
    }

    // Forzar el cierre de los servidores indicados que sigan vivos.
    public void forzarPararServidores(List<Server> servidores) {
        for (Server server : servidores) {
            if (server == null || !ProcessTreeTerminator.isTreeAlive(server.getServerProcess())) continue;
            try {
                forzarPararServidor(server);
            } catch (RuntimeException ignored) {
            }
        }
    }

    // ===== MÉTODOS DE CONTROL DEL SERVIDOR =====

    // Cerrojo de arranque: evita dobles arranques y que dos servidores elijan el mismo puerto.
    // No se mantiene mientras se muestra un diálogo: si un hilo de fondo (automatizaciones,
    // reinicio) lo tuviera esperando al EDT y el EDT intentara arrancar otro servidor, la app se congelaría.
    private final Object inicioLock = new Object();

    private record SolicitudPuertoInicio(String bindHost,
                                         int puertoOcupado,
                                         boolean puertoDefectoDisponible,
                                         int puertoRecomendado) {
    }

    public void iniciarServidor(Server server) throws IOException {
        if (!confirmarCompatibilidadJava(server)) {
            return;
        }
        Integer puertoElegido = null;
        while (true) {
            SolicitudPuertoInicio solicitud;
            synchronized (inicioLock) {
                solicitud = iniciarServidorBloqueado(server, puertoElegido);
            }
            if (solicitud == null) {
                return;
            }
            // El puerto está ocupado: preguntamos al usuario fuera del cerrojo.
            puertoElegido = solicitarPuertoAlternativo(
                    server,
                    solicitud.bindHost(),
                    solicitud.puertoOcupado(),
                    solicitud.puertoDefectoDisponible(),
                    solicitud.puertoRecomendado()
            );
            if (puertoElegido == null) {
                server.appendConsoleLinea("[INFO] Inicio cancelado por selección de puerto.");
                return;
            }
        }
    }

    // Servidores para los que el usuario ya ha aceptado el aviso de Java en esta sesión.
    private final Set<String> avisoJavaAceptado = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // Avisa si la versión de Minecraft del servidor no encaja con el Java del sistema (el que usan los arranques).
    // Devuelve false si el usuario cancela el inicio. Se llama fuera de inicioLock porque puede mostrar un diálogo.
    private boolean confirmarCompatibilidadJava(Server server) {
        if (server == null || (server.getServerProcess() != null && server.getServerProcess().isAlive())) {
            return true;
        }
        String aviso;
        try {
            aviso = JavaRuntimeCompatibility.warningFor(
                    server.getVersion(),
                    server.getPlatform() == ServerPlatform.FORGE,
                    JavaRuntimeCompatibility.detectPathJavaMajor()
            );
        } catch (RuntimeException e) {
            return true;
        }
        if (aviso == null) {
            return true;
        }
        server.appendConsoleLinea("[WARN] " + aviso);
        String clave = server.getId() == null ? server.getServerDir() : server.getId();
        // Arranques automáticos o sin interfaz: solo el aviso en consola.
        if (GraphicsEnvironment.isHeadless() || !SwingUtilities.isEventDispatchThread()
                || (clave != null && avisoJavaAceptado.contains(clave))) {
            return true;
        }
        Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        Object[] opciones = {"Iniciar igualmente", "Cancelar"};
        int respuesta = JOptionPane.showOptionDialog(
                owner,
                aviso + "\n\nDora usa el Java instalado en el sistema para arrancar los servidores.",
                "Versión de Java",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                opciones,
                opciones[1]
        );
        if (respuesta != 0) {
            server.appendConsoleLinea("[INFO] Inicio cancelado por la versión de Java.");
            return false;
        }
        if (clave != null) {
            avisoJavaAceptado.add(clave);
        }
        return true;
    }

    // Devuelve una solicitud de puerto si hay que preguntar al usuario; null si ha terminado (arrancado o fallido).
    private SolicitudPuertoInicio iniciarServidorBloqueado(Server server, Integer puertoElegido) throws IOException {
        // Compruebo si está en marcha, si no, prosigo
        if(server.getServerProcess()!=null && server.getServerProcess().isAlive()) {
            server.appendConsoleLinea("[INFO] El servidor ya está iniciado. ");
            return null;
        }
        Path dir = Path.of(server.getServerDir());
        try {
            GestorMundos.sincronizarMundosServidor(server);
        } catch (RuntimeException e) {
            server.appendConsoleLinea("[WARN] No se ha podido preparar dora-worlds: " + e.getMessage());
        }

        ServerPlatformAdapter adapter = resolverAdaptador(server);
        Path jar;
        try {
            jar = adapter.resolveExecutableJar(dir);
        } catch (RuntimeException e) {
            server.appendConsoleLinea("[ERROR] No se ha podido encontrar el .jar del servidor: " + e.getMessage());
            return null;
        } catch (IOException e) {
            server.appendConsoleLinea("[ERROR] No se ha podido resolver el .jar del servidor: " + e.getMessage());
            return null;
        }
        if (jar == null && adapter.requiresExecutableJarForStart()) {
            server.appendConsoleLinea("[ERROR] No se ha podido encontrar el .jar del servidor.");
            return null;
        }

        // Creo un proceso con la dirección del servidor y la RAM elegida y en el primer puerto que esté libre desde 25565
        ServerConfig serverConfig = asegurarServerConfig(server);
        sincronizarPuertoConfigDesdeProperties(server);
        Object resolucionPuerto = resolverPuertoParaInicio(server, serverConfig, puertoElegido);
        if (resolucionPuerto instanceof SolicitudPuertoInicio solicitud) {
            return solicitud;
        }
        int puerto = (Integer) resolucionPuerto;
        if(puerto != serverConfig.getPuerto()){
            // si el puerto no está libre pasamos al siguiente
            serverConfig.setPuerto(puerto);
        }
        try{
            // escribimos el puerto nuevo en las propiedades del servidor
            Utilidades.escribirPuertoEnProperties(dir, puerto);
            guardarServidor(server);
            notificarConfiguracionServidor(server);
        } catch (RuntimeException e) {
            server.appendConsoleLinea("[ERROR] No se ha podido escribir el puerto en server.properties: " + e.getMessage());
        }

        ProcessBuilder pb;
        try {
            pb = adapter.buildStartProcess(server, jar);
        } catch (RuntimeException e) {
            server.appendConsoleLinea("[ERROR] No se ha podido preparar el arranque del servidor: " + e.getMessage());
            return null;
        }
        server.appendConsoleLinea("[INFO] Iniciando servidor con "+serverConfig.getRamInit()+"M y "+serverConfig.getRamMax()+"M de RAM.");

        try{
            // creo el proceso y se lo asigno al servidor
            Process proceso = pb.start();
            server.setServerProcess(proceso);
            server.setIniciando(true);
            notificarEstadoServidor(server);

            // comienzo a leer la consola
            startLogReader(server);
            notificarEstadoServidor(server);

            // detecto cuando el proceso finaliza
            proceso.onExit().thenRun(()->{
               try {
                   if(server.getServerProcess() == proceso){
                       server.setServerProcess(null);
                   }
                   server.setIniciando(false);
                   server.setLogReaderIniciado(false);
                   server.appendConsoleLinea("[INFO] El servidor se ha detenido.");
                   notificarEstadoServidor(server);
                   // si el usuario ha pedido un reinicio y hemos parado el servidor entonces lo iniciamos de nuevo
                   if(server.getRestartPending()){
                       server.setRestartPending(false);
                       try {
                           iniciarServidor(server);
                       } catch (IOException e) {
                           server.appendConsoleLinea("[ERROR] Error al reiniciar el servidor: " + e.getMessage());
                       }
                   }
               } catch (RuntimeException ex) {
                   AppErrorReporter.report("Error al procesar la parada del servidor.", ex);
                   server.appendConsoleLinea("[ERROR] Error al procesar la parada del servidor: " + ex.getMessage());
               }
            });

        } catch (IOException e) {
            server.setIniciando(false);
            server.appendConsoleLinea("[ERROR] El servidor no se pudo iniciar"+e.getMessage());
            AppErrorReporter.report("El proceso del servidor no se pudo iniciar.", e);
        }
        return null;
    }

    // Eliminar el proceso de servidor, a evitar, puede provocar corrupción de mundos
    public void forzarPararServidor(Server server){
        Process proceso = server.getServerProcess();

        if(!ProcessTreeTerminator.isTreeAlive(proceso)) {
            server.appendConsoleLinea("[INFO] El servidor no está en ejecución");
            server.setServerProcess(null);
            server.setIniciando(false);
            notificarEstadoServidor(server);
            return;
        }
        server.appendConsoleLinea("[INFO] Forzando cierre del servidor...");

        // Cerramos el árbol completo: en Forge/NeoForge el proceso guardado es run.bat/run.sh y el
        // servidor Java real es un proceso hijo que, si no, quedaría vivo y huérfano.
        boolean destruido = false;
        try{
            destruido = ProcessTreeTerminator.terminate(proceso, Duration.ofSeconds(3), Duration.ofSeconds(3));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally{
            if(destruido){
                server.appendConsoleLinea("[INFO] Se ha cerrado el servidor de forma forzada, no se han guardado los cambios.");
            }
            else{
                server.appendConsoleLinea("[ERROR] No se ha podido cerrar el servidor.");
            }
            // Si no está vivo limpiamos referencia
            if(!proceso.isAlive()) server.setServerProcess(null);
            if(!proceso.isAlive()) server.setIniciando(false);
            notificarEstadoServidor(server);
        }
    }

    public void safePararServidor(Server server){
        mandarComando(server, "stop");
    }

    public void reiniciarServidor(Server server) throws IOException {
        if (server == null) {
            return;
        }
        Process proceso = server.getServerProcess();
        if (proceso != null && proceso.isAlive()) {
            server.setRestartPending(true);
            safePararServidor(server);
            notificarEstadoServidor(server);
            return;
        }
        iniciarServidor(server);
    }

    public Server getServerById(String id){
        if (listaServidores == null || id == null) return null;
        return listaServidores.stream()
                .filter(Objects::nonNull)
                .filter(servidor -> Objects.equals(servidor.getId(), id))
                .findFirst()
                .orElse(null);
    }

    public void mandarComando(Server server, String comando){
        mandarComando(server, comando, true);
    }

    public void mandarComando(Server server, String comando, boolean mostrarEnConsola){
        if(server.getServerProcess()==null || !server.getServerProcess().isAlive()) {
            server.appendConsoleLinea("[INFO] No has iniciado el servidor.");
            return;
        }
        try{
            OutputStream os = server.getServerProcess().getOutputStream();
            PrintWriter pw = new PrintWriter(os, true);
            pw.println(comando);
            if(mostrarEnConsola){
                server.appendConsoleLinea("[INFO] Enviado comando: '"+comando+"'.");
            }
        } catch (Exception e) {
            server.appendConsoleLinea("[ERROR] Error mandando '"+comando+"' "+e.getMessage());
        }
    }

    // ===== LECTURA DE LOGS =====

    private void startLogReader(Server server){
        if (server.getLogReaderIniciado()) return;
        if (server.getServerProcess() == null) return;

        server.setLogReaderIniciado(true);

        Thread lector = new Thread(
            new ServerLogReader(
                    server,
                    server.getServerProcess().getInputStream(),
                    () -> SwingUtilities.invokeLater(() -> notificarEstadoServidor(server))
            ),
            "log-reader-"+server.getId()
        );
        lector.setDaemon(true);
        lector.start();
    }

    private ServerConfig asegurarServerConfig(Server server) {
        ServerConfig serverConfig = server == null ? null : server.getServerConfig();
        if(serverConfig == null){
            serverConfig = new ServerConfig();
            if (server != null) {
                server.setServerConfig(serverConfig);
            }
        }
        return serverConfig;
    }

    // Devuelve el puerto (Integer) a usar o una SolicitudPuertoInicio si hay que preguntar al usuario.
    // Debe llamarse con inicioLock tomado.
    private Object resolverPuertoParaInicio(Server server, ServerConfig serverConfig, Integer puertoElegido) {
        String bindHost = resolveServerBindHost(server);
        Set<Integer> reservados = puertosDeOtrosServidoresActivos(server);
        int puertoObjetivo = puertoElegido != null ? puertoElegido : puertoConfigurado(serverConfig);

        if (isPortAvailableForStart(bindHost, puertoObjetivo, reservados)) {
            return puertoObjetivo;
        }

        Integer siguientePuerto = null;
        for (int p = Math.max(1, puertoObjetivo + 1); p <= PUERTO_MAXIMO; p++) {
            if (isPortAvailableForStart(bindHost, p, reservados)) {
                siguientePuerto = p;
                break;
            }
        }
        if (siguientePuerto == null) {
            throw new RuntimeException("No hay puertos disponibles entre " + Math.max(1, puertoObjetivo + 1) + " y 65535");
        }
        return new SolicitudPuertoInicio(
                bindHost,
                puertoObjetivo,
                isPortAvailableForStart(bindHost, PUERTO_MINECRAFT_DEFECTO, reservados),
                siguientePuerto
        );
    }

    // Un servidor recién lanzado tarda unos segundos en abrir su puerto y, hasta entonces, el puerto parece libre.
    // Tratamos como ocupados los puertos de los demás servidores que Dora tiene en marcha.
    private Set<Integer> puertosDeOtrosServidoresActivos(Server server) {
        Set<Integer> puertos = new HashSet<>();
        for (Server otro : getServidoresActivos()) {
            if (otro == server || otro.getServerConfig() == null) continue;
            puertos.add(otro.getServerConfig().getPuerto());
        }
        return puertos;
    }

    private static boolean isPortAvailableForStart(String bindHost, int port, Set<Integer> reservados) {
        return !reservados.contains(port) && isPortAvailable(bindHost, port);
    }

    private int puertoConfigurado(ServerConfig serverConfig) {
        if (serverConfig == null || !isValidPortNumber(serverConfig.getPuerto())) {
            return PUERTO_MINECRAFT_DEFECTO;
        }
        return serverConfig.getPuerto();
    }

    private String resolveServerBindHost(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return null;
        }
        try {
            Properties properties = Utilidades.cargarPropertiesUtf8(Path.of(server.getServerDir()).resolve("server.properties"));
            String serverIp = properties.getProperty("server-ip");
            return serverIp == null || serverIp.isBlank() ? null : serverIp.trim();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isValidPortNumber(int port) {
        return port > 0 && port <= PUERTO_MAXIMO;
    }

    private Integer buscarSiguientePuertoDisponible(String bindHost, int desde) {
        for(int p = Math.max(1, desde); p <= PUERTO_MAXIMO; p++){
            if(isPortAvailable(bindHost, p)){
                return p;
            }
        }
        return null;
    }

    // comprobamos si hay libre un puerto
    private static boolean isPortAvailable(String bindHost, int port){
        if(port <= 0 || port > PUERTO_MAXIMO) return false;
        try(ServerSocket socket = new ServerSocket()){
            socket.setReuseAddress(false);
            String host = bindHost == null || bindHost.isBlank() ? "0.0.0.0" : bindHost.trim();
            socket.bind(new InetSocketAddress(host, port));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private ServerCreationWizardResult mostrarAsistenteCreacionServidor() {
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

    private JLabel createWizardComboLabel(JList<?> list, String text, boolean isSelected) {
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

    private boolean cargarOpcionesCreacionWizard(Component parent,
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

    private void actualizarListadoVersiones(ServerCreationWizardState state,
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

    private boolean debeMostrarOpcionCreacion(ServerCreationOption option, boolean includeSnapshots, boolean includeReleases) {
        if (option == null) {
            return false;
        }
        boolean snapshot = esSnapshot(option);
        return snapshot ? includeSnapshots : includeReleases;
    }

    private boolean esOpcionCreacionNoDescargableConocida(ServerCreationWizardState state,
                                                          ServerCreationOption option) {
        if (state == null || option == null || option.platform() != modelo.extensions.ServerPlatform.VANILLA) {
            return false;
        }
        return state.serverJarUnavailableVersions.contains(option.minecraftVersion());
    }

    private boolean soportaSnapshotsCreacion(ServerPlatformAdapter adapter) {
        return adapter != null && adapter.supportsUnstableCreationOptions();
    }

    private boolean puedeAvanzarPasoCreacionServidor(int stepIndex,
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

    private boolean esOpcionCreacionDescargable(ServerCreationWizardState state) {
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

    private boolean esSnapshot(ServerCreationOption option) {
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

    private String validarPasoCreacionServidor(int stepIndex,
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

    private void actualizarVistaNombreCarpeta(ServerCreationWizardState state,
                                              JLabel parentPrefixLabel,
                                              JTextField folderNameField) {
        if (state == null) {
            return;
        }
        parentPrefixLabel.setText(formatearPrefijoCarpetaPadre(state.parentDirectory));
        actualizarSugerenciaNombreCarpeta(state, folderNameField, false);
    }

    private void actualizarSugerenciaNombreCarpeta(ServerCreationWizardState state,
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

    private boolean confirmarCancelacionAsistenteCreacion(Component parent) {
        int option = JOptionPane.showConfirmDialog(
                parent,
                "¿Quieres cancelar la creación del servidor?",
                "Cancelar creación",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        return option == JOptionPane.YES_OPTION;
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

    private record ServerCreationWizardResult(
            ServerPlatformAdapter adapter,
            ServerCreationOption option,
            File targetDirectory
    ) {
    }

    private Integer solicitarPuertoAlternativo(Server server,
                                               String bindHost,
                                               int puertoOcupado,
                                               boolean puertoDefectoDisponible,
                                               int puertoRecomendado) {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }

        AtomicReference<Integer> resultado = new AtomicReference<>();
        Runnable dialogTask = () -> resultado.set(mostrarDialogoPuertoOcupado(
                server,
                bindHost,
                puertoOcupado,
                puertoDefectoDisponible,
                puertoRecomendado
        ));

        if (SwingUtilities.isEventDispatchThread()) {
            dialogTask.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(dialogTask);
            } catch (Exception e) {
                return null;
            }
        }
        return resultado.get();
    }

    private Integer mostrarDialogoPuertoOcupado(Server server,
                                                String bindHost,
                                                int puertoOcupado,
                                                boolean puertoDefectoDisponible,
                                                int puertoRecomendadoInicial) {
        Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        JDialog dialog = new JDialog(owner, "Puerto del servidor ocupado", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 14, 16));

        String nombreServidor = server == null || server.getDisplayName() == null || server.getDisplayName().isBlank()
                ? "este servidor"
                : server.getDisplayName();
        JLabel message = new JLabel("<html><body style='width:560px'>No se ha podido usar el puerto guardado "
                + puertoOcupado + " para " + escapeHtml(nombreServidor)
                + ". Puedes cancelar el inicio o guardar otro puerto antes de arrancar.</body></html>");
        root.add(message, BorderLayout.NORTH);

        ButtonGroup group = new ButtonGroup();
        JRadioButton defaultRadio = new JRadioButton(
                puertoDefectoDisponible ? "Puerto por defecto (recomendado)" : "Puerto por defecto",
                puertoDefectoDisponible
        );
        JRadioButton recommendedRadio = new JRadioButton(
                puertoDefectoDisponible ? "Siguiente puerto disponible" : "Siguiente puerto disponible (recomendado)",
                !puertoDefectoDisponible
        );
        JRadioButton customRadio = new JRadioButton("Puerto personalizado");
        defaultRadio.setEnabled(puertoDefectoDisponible);
        group.add(defaultRadio);
        group.add(recommendedRadio);
        group.add(customRadio);

        JLabel defaultValueLabel = new JLabel(puertoDefectoDisponible ? String.valueOf(PUERTO_MINECRAFT_DEFECTO) : "No disponible");
        defaultValueLabel.setFont(defaultValueLabel.getFont().deriveFont(Font.BOLD, 22f));
        JLabel recommendedValueLabel = new JLabel(String.valueOf(puertoRecomendadoInicial));
        recommendedValueLabel.setFont(recommendedValueLabel.getFont().deriveFont(Font.BOLD, 22f));
        JTextField customPortField = new JTextField(String.valueOf(puertoRecomendadoInicial), 8);
        customPortField.setEnabled(false);
        JLabel statusLabel = new JLabel(" ");

        JPanel defaultPanel = new JPanel(new BorderLayout(0, 8));
        defaultPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 14));
        defaultPanel.add(defaultRadio, BorderLayout.NORTH);
        defaultPanel.add(new JLabel("Puerto 25565:"), BorderLayout.CENTER);
        defaultPanel.add(defaultValueLabel, BorderLayout.SOUTH);

        JPanel recommendedPanel = new JPanel(new BorderLayout(0, 8));
        recommendedPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 14));
        recommendedPanel.add(recommendedRadio, BorderLayout.NORTH);
        recommendedPanel.add(new JLabel("Puerto sugerido:"), BorderLayout.CENTER);
        recommendedPanel.add(recommendedValueLabel, BorderLayout.SOUTH);

        JPanel customPanel = new JPanel(new BorderLayout(0, 8));
        customPanel.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 10));
        customPanel.add(customRadio, BorderLayout.NORTH);
        customPanel.add(new JLabel("Elige un puerto entre 1 y " + PUERTO_MAXIMO + ":"), BorderLayout.CENTER);
        customPanel.add(customPortField, BorderLayout.SOUTH);

        JPanel optionsPanel = new JPanel(new GridLayout(1, 3, 0, 0));
        optionsPanel.add(defaultPanel);
        optionsPanel.add(recommendedPanel);
        optionsPanel.add(customPanel);
        Color dividerColor = UIManager.getColor("Component.borderColor");
        optionsPanel.setBorder(BorderFactory.createLineBorder(dividerColor == null ? Color.GRAY : dividerColor));
        root.add(optionsPanel, BorderLayout.CENTER);

        JButton cancelButton = new JButton("Cancelar inicio");
        JButton saveButton = new JButton("Guardar");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelButton);
        buttons.add(saveButton);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);

        AtomicReference<Integer> selectedPort = new AtomicReference<>();
        AtomicInteger recommendedPort = new AtomicInteger(puertoRecomendadoInicial);

        defaultRadio.addActionListener(e -> customPortField.setEnabled(false));
        recommendedRadio.addActionListener(e -> customPortField.setEnabled(false));
        customRadio.addActionListener(e -> {
            customPortField.setEnabled(true);
            customPortField.requestFocusInWindow();
            customPortField.selectAll();
        });

        cancelButton.addActionListener(e -> {
            selectedPort.set(null);
            dialog.dispose();
        });
        saveButton.addActionListener(e -> {
            if (defaultRadio.isSelected()) {
                if (isPortAvailable(bindHost, PUERTO_MINECRAFT_DEFECTO)) {
                    selectedPort.set(PUERTO_MINECRAFT_DEFECTO);
                    dialog.dispose();
                    return;
                }
                defaultRadio.setEnabled(false);
                recommendedRadio.setSelected(true);
                statusLabel.setText("El puerto por defecto ya no está disponible.");
                return;
            }
            if (recommendedRadio.isSelected()) {
                int candidate = recommendedPort.get();
                if (isPortAvailable(bindHost, candidate)) {
                    selectedPort.set(candidate);
                    dialog.dispose();
                    return;
                }
                Integer refreshed = buscarSiguientePuertoDisponible(bindHost, candidate + 1);
                if (refreshed == null) {
                    statusLabel.setText("No hay puertos disponibles.");
                    return;
                }
                recommendedPort.set(refreshed);
                recommendedValueLabel.setText(String.valueOf(refreshed));
                statusLabel.setText("El puerto sugerido se ha ocupado. Nueva sugerencia: " + refreshed + ".");
                return;
            }

            Integer customPort = parsePort(customPortField.getText());
            if (customPort == null) {
                statusLabel.setText("Introduce un puerto válido entre 1 y " + PUERTO_MAXIMO + ".");
                customRadio.setSelected(true);
                customPortField.setEnabled(true);
                customPortField.requestFocusInWindow();
                customPortField.selectAll();
                return;
            }
            if (!isPortAvailable(bindHost, customPort)) {
                statusLabel.setText("El puerto " + customPort + " ya está ocupado por otro proceso.");
                customRadio.setSelected(true);
                customPortField.setEnabled(true);
                customPortField.requestFocusInWindow();
                customPortField.selectAll();
                return;
            }
            selectedPort.set(customPort);
            dialog.dispose();
        });

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(640, dialog.getHeight()));
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        return selectedPort.get();
    }

    private Integer parsePort(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            int port = Integer.parseInt(text.trim());
            return port > 0 && port <= PUERTO_MAXIMO ? port : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private File resolverDirectorioServidorDisponible(File directorioPadre, String nombreBase) {
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

    private String construirNombreServidorImportado(String version, String serverDir, boolean forzarSufijoCopia) {
        String versionNormalizada = (version == null || version.isBlank()) ? null : version.trim();
        String base = versionNormalizada == null ? nombreCarpetaServidor(serverDir) : "Servidor " + versionNormalizada;

        int duplicadosExistentes = 0;
        if (listaServidores != null) {
            for (Server existente : listaServidores) {
                if (existente == null) continue;
                if (serverDir != null && serverDir.equals(existente.getServerDir())) continue;
                if (versionNormalizada == null) {
                    if (!Objects.equals(base, existente.getDisplayName())) continue;
                } else if (!Objects.equals(versionNormalizada, normalizarVersionNombre(existente.getVersion()))) {
                    continue;
                }
                duplicadosExistentes++;
            }
        }

        if (!forzarSufijoCopia && duplicadosExistentes == 0) {
            return base;
        }
        return base + " (" + duplicadosExistentes + ")";
    }

    private String normalizarVersionNombre(String version) {
        return (version == null || version.isBlank()) ? "sin versión" : version.trim();
    }

    private String nombreCarpetaServidor(String serverDir) {
        if (serverDir == null || serverDir.isBlank()) {
            return "Servidor importado";
        }
        try {
            Path path = Path.of(serverDir);
            Path fileName = path.getFileName();
            if (fileName != null && !fileName.toString().isBlank()) {
                return fileName.toString();
            }
        } catch (RuntimeException ignored) {
        }
        return "Servidor importado";
    }
}
