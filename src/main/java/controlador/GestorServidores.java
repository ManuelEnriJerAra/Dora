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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerPlatform;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static controlador.PuertosServidor.PUERTO_MAXIMO;
import static controlador.PuertosServidor.PUERTO_MINECRAFT_DEFECTO;
import static controlador.PuertosServidor.isPortAvailableForStart;
import static controlador.PuertosServidor.puertoConfigurado;
import static controlador.PuertosServidor.resolveServerBindHost;

@Getter
@Setter

@SuppressWarnings("unused")
public class GestorServidores {
    public enum ModpackImportConflictPolicy {
        KEEP_EXISTING,
        REPLACE_EXISTING
    }

    // ===== ATRIBUTOS =====
    private static final MojangAPI MOJANG_API = new MojangAPI();

    @Getter(AccessLevel.NONE)
    private final ArchivoListaServidores archivoServidores;
    private final ServerExtensionsService serverExtensionsService = new ServerExtensionsService();
    private final ExtensionCatalogService extensionCatalogService = new ExtensionCatalogService();
    private final CurseForgeModpackService curseForgeModpackService = new CurseForgeModpackService();
    private final ModrinthModpackService modrinthModpackService;
    private final FileDownloader extensionDownloader = new ExtensionArtifactDownloader();
    @Getter(AccessLevel.NONE)
    private final ImportadorModpacks importadorModpacks;
    @Getter(AccessLevel.NONE)
    private final ConversorPlataformaServidor conversor = new ConversorPlataformaServidor(this);
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
        this(null, new ModrinthModpackService());
    }

    GestorServidores(File jsonFile) {
        this(jsonFile, new ModrinthModpackService());
    }

    GestorServidores(File jsonFile, ModrinthModpackService modrinthModpackService) {
        this.archivoServidores = new ArchivoListaServidores(jsonFile);
        this.modrinthModpackService = modrinthModpackService == null ? new ModrinthModpackService() : modrinthModpackService;
        this.importadorModpacks = new ImportadorModpacks(
                this,
                serverExtensionsService,
                curseForgeModpackService,
                this.modrinthModpackService,
                extensionDownloader
        );
        this.listaServidores = archivoServidores.cargar();
        this.avisoListaServidoresIlegible = archivoServidores.getAvisoArchivoIlegible();
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
            if (MetadatosServidor.asegurarPersistentes(server)) {
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

    boolean sincronizarExtensionesServidor(Server server) throws IOException {
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

    public boolean puedeConvertirseAPlataformaCompatible(Server server) {
        return conversor.puedeConvertirseAPlataformaCompatible(server);
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
        return importadorModpacks.importarModpackCurseForge(server, sourceZip, manifest, conflictPolicy, warnings, extensionsAlreadySynced);
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
        return importadorModpacks.importarModpackModrinth(server, sourcePack, index, options, conflictPolicy, warnings, extensionsAlreadySynced);
    }

    private ServerPlatformAdapter resolverAdaptador(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return ServerPlatformAdapters.forPlatform(null);
        }

        Path serverDir = Path.of(server.getServerDir());
        ServerPlatformProfile profile = PerfilPlataformaServidor.detectar(serverDir);
        if (profile != null) {
            PerfilPlataformaServidor.aplicar(server, profile);
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
                    ServerPlatformProfile profile = PerfilPlataformaServidor.detectar(Path.of(server.getServerDir()));
                    if (profile != null) {
                        PerfilPlataformaServidor.aplicar(server, profile);
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
                .append("\nNo se mostrarán, pero se conservan en " + ArchivoListaServidores.NOMBRE_ARCHIVO
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
        ServerPlatformProfile profile = PerfilPlataformaServidor.detectar(dir);
        ServerPlatformAdapter adapter = ServerPlatformAdapters.forPlatform(profile == null ? server.getPlatform() : profile.platform());
        ServerValidationResult validation = adapter.validate(dir);
        if (validation.valid() && profile != null) {
            PerfilPlataformaServidor.aplicar(server, profile);
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
        AsistenteCreacionServidor.ServerCreationWizardResult wizardResult = AsistenteCreacionServidor.mostrar();
        if (wizardResult == null) return null;
        return crearServidorAutomatizado(
                wizardResult.adapter(),
                wizardResult.option(),
                wizardResult.targetDirectory().toPath()
        );
    }

    public Server convertirServidorAPlataformaCompatible(Server server) {
        return conversor.convertirServidorAPlataformaCompatible(server);
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
        PerfilPlataformaServidor.aplicar(server, PerfilPlataformaServidor.detectar(targetDirectory));
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

    static Path resolverIconoPorDefecto() {
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

        ServerPlatformProfile profile = PerfilPlataformaServidor.detectar(directorioNormalizadoPath);
        if (profile == null) {
            System.out.println("No se ha podido detectar una plataforma válida para el servidor importado.");
            return null;
        }

        Server server = new Server();
        server.setServerDir(directorioNormalizado);
        PerfilPlataformaServidor.aplicar(server, profile);
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
        // Los no disponibles se guardan también: solo están ocultos, no eliminados.
        List<Server> persistidos = copiarListaServidoresOrdenada();
        persistidos.addAll(servidoresNoDisponibles);
        archivoServidores.guardar(persistidos);
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

        MetadatosServidor.asegurarPersistentes(server);

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
            puertoElegido = DialogoPuertoOcupado.solicitar(
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
        MetadatosServidor.sincronizarPuertoDesdeProperties(server);
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
