/*
 * Fichero: ImportadorModpacks.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Importa modpacks de CurseForge y Modrinth en un servidor existente. Si se reemplazan los mods actuales,
 * se apartan antes a una copia de seguridad y se restauran si la importación falla a medias.
 * */

package controlador;

import controlador.extensions.CurseForgeModpackService;
import controlador.extensions.ExtensionDownloadPlan;
import controlador.extensions.ExtensionInstallResolution;
import controlador.extensions.ExtensionInstallResolutionState;
import controlador.extensions.ModrinthModpackService;
import controlador.extensions.ServerExtensionsService;
import controlador.platform.FileDownloader;
import modelo.Server;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

final class ImportadorModpacks {
    private static final String MODPACK_BACKUP_DIR = ".dora-backup";

    private final GestorServidores gestor;
    private final ServerExtensionsService serverExtensionsService;
    private final CurseForgeModpackService curseForgeModpackService;
    private final ModrinthModpackService modrinthModpackService;
    private final FileDownloader extensionDownloader;

    ImportadorModpacks(GestorServidores gestor,
                       ServerExtensionsService serverExtensionsService,
                       CurseForgeModpackService curseForgeModpackService,
                       ModrinthModpackService modrinthModpackService,
                       FileDownloader extensionDownloader) {
        this.gestor = gestor;
        this.serverExtensionsService = serverExtensionsService;
        this.curseForgeModpackService = curseForgeModpackService;
        this.modrinthModpackService = modrinthModpackService;
        this.extensionDownloader = extensionDownloader;
    }

    int importarModpackCurseForge(Server server,
                                         Path sourceZip,
                                         CurseForgeModpackService.ImportManifest manifest,
                                         GestorServidores.ModpackImportConflictPolicy conflictPolicy,
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
        if (conflictPolicy == GestorServidores.ModpackImportConflictPolicy.REPLACE_EXISTING) {
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
        gestor.guardarServidor(server);
        return installed;
    }

    int importarModpackModrinth(Server server,
                                       Path sourcePack,
                                       ModrinthModpackService.ImportIndex index,
                                       ModrinthModpackService.ImportOptions options,
                                       GestorServidores.ModpackImportConflictPolicy conflictPolicy,
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
            if (conflictPolicy == GestorServidores.ModpackImportConflictPolicy.REPLACE_EXISTING) {
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
            gestor.guardarServidor(server);
            return installed;
        }
    }

    private void prepararImportacionModpack(Server server,
                                            boolean refreshExtensions) throws IOException {
        if (refreshExtensions) {
            gestor.sincronizarExtensionesServidor(server);
        }
    }

    private boolean debeOmitirsePlanModpack(Server server,
                                            ExtensionDownloadPlan plan,
                                            GestorServidores.ModpackImportConflictPolicy conflictPolicy,
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
        if (conflictPolicy == GestorServidores.ModpackImportConflictPolicy.REPLACE_EXISTING) {
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
                                                        GestorServidores.ModpackImportConflictPolicy conflictPolicy,
                                                        List<String> warnings) throws IOException {
        if (conflictPolicy == GestorServidores.ModpackImportConflictPolicy.REPLACE_EXISTING
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
