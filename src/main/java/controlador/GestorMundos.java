/*
 * Fichero: GestorMundos.java
 *
 * Autor: Manuel Enrique Jeronimo Aragon
 *
 * Descripcion:
 * Esta clase se encarga de la gestion de mundos del servidor, incluyendo su sincronización,
 * importacion, exportación, cambio y preparación de nuevos mundos.
 * */

package controlador;

import controlador.world.WorldFilesService;
import modelo.MinecraftConstants;
import modelo.Server;
import modelo.ServerProperties;
import modelo.World;

import javax.swing.*;
import java.awt.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

public final class GestorMundos {
    public static final String DIRECTORIO_MUNDOS = "dora-worlds";
    private static final String META_ARCHIVO = WorldFilesService.WORLD_METADATA_FILE;
    private static final String SUFIJO_NETHER = "_nether";
    private static final String SUFIJO_END = "_the_end";

    // Evita instancias de esta clase utilitaria.
    private GestorMundos() {
    }

    // Sincroniza la estructura de mundos del servidor con el sistema gestionado por la aplicacion.
    public static boolean sincronizarMundosServidor(Server server) {
        if(server == null || server.getServerDir() == null || server.getServerDir().isBlank()) return false;

        try {
            Path serverDir = Path.of(server.getServerDir());
            if(!Files.isDirectory(serverDir)) return false;

            boolean cambios = false;
            Files.createDirectories(getDirectorioMundos(server));
            Properties props = cargarServerProperties(serverDir, true);

            String levelNameBruto = props.getProperty("level-name", MinecraftConstants.DEFAULT_WORLD_NAME).trim();
            if(levelNameBruto.isBlank()) levelNameBruto = MinecraftConstants.DEFAULT_WORLD_NAME;

            String mundoActivoNombre = normalizarNombreMundo(levelNameBruto);
            if(mundoActivoNombre.isBlank()) mundoActivoNombre = MinecraftConstants.DEFAULT_WORLD_NAME;

            World mundoActivo = new World(getDirectorioMundo(server, mundoActivoNombre).toString(), mundoActivoNombre);

            if(!esRutaGestionada(levelNameBruto)) {
                cambios |= migrarMundoRaizASistemaGestionado(serverDir, mundoActivo);
                props.setProperty("level-name", construirLevelNameGestionado(mundoActivo));
                cambios = true;
            }

            Path mundoActivoDir = getDirectorioMundo(server, mundoActivo);
            if(!Files.exists(mundoActivoDir)) {
                Files.createDirectories(mundoActivoDir);
                cambios = true;
            }

            if(listarMundos(server).isEmpty()) {
                World defaultWorld = new World(getDirectorioMundo(server, MinecraftConstants.DEFAULT_WORLD_NAME).toString(), MinecraftConstants.DEFAULT_WORLD_NAME);
                Files.createDirectories(getDirectorioMundo(server, defaultWorld));
                props.setProperty("level-name", construirLevelNameGestionado(defaultWorld));
                cambios = true;
            }

            if(cambios) {
                guardarServerProperties(serverDir, props);
            }
            return cambios;
        } catch (IOException e) {
            throw new RuntimeException("No se ha podido sincronizar la carpeta de mundos: " + e.getMessage(), e);
        }
    }

    // Devuelve la lista de mundos principales disponibles para el servidor.
    public static List<World> listarMundos(Server server) {
        if(server == null || server.getServerDir() == null || server.getServerDir().isBlank()) return List.of();

        Path worldsDir = getDirectorioMundos(server);
        if(!Files.isDirectory(worldsDir)) return List.of();

        try(Stream<Path> paths = Files.list(worldsDir)) {
            List<World> mundos = paths
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().endsWith(SUFIJO_NETHER) && !path.getFileName().toString().endsWith(SUFIJO_END))
                    .map(GestorMundos::cargarMundo)
                    .sorted((a, b) -> a.getWorldName().compareToIgnoreCase(b.getWorldName()))
                    .toList();
            return new ArrayList<>(mundos);
        } catch (IOException e) {
            throw new RuntimeException("No se han podido listar los mundos: " + e.getMessage(), e);
        }
    }

    // Obtiene el mundo actualmente configurado como activo en server.properties.
    public static World getMundoActivo(Server server) {
        if(server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return new World("", MinecraftConstants.DEFAULT_WORLD_NAME);
        }

        try {
            Properties props = cargarServerProperties(Path.of(server.getServerDir()), false);
            String levelName = props.getProperty("level-name", MinecraftConstants.DEFAULT_WORLD_NAME);
            String normalizado = normalizarNombreMundo(levelName);
            if(normalizado.isBlank()) {
                normalizado = MinecraftConstants.DEFAULT_WORLD_NAME;
            }

            for(World mundo : listarMundos(server)) {
                if(mismoMundo(mundo, normalizado)) {
                    return mundo;
                }
            }

            Path mundoDir = getDirectorioMundo(server, normalizado);
            return cargarMundo(mundoDir);
        } catch (IOException e) {
            return new World(getDirectorioMundo(server, MinecraftConstants.DEFAULT_WORLD_NAME).toString(), MinecraftConstants.DEFAULT_WORLD_NAME);
        }
    }

    // Importa un mundo externo a la carpeta gestionada del servidor.
    public static boolean importarMundo(Server server, Component parent) {
        if(!puedeModificarMundos(server, parent)) return false;
        sincronizarMundosServidor(server);

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle("Selecciona la carpeta del mundo");
        if(chooser.showDialog(parent, "Importar") != JFileChooser.APPROVE_OPTION) return false;

        Path origen = chooser.getSelectedFile().toPath();
        if(!esMundoValido(origen)) {
            JOptionPane.showMessageDialog(parent,
                    "La carpeta seleccionada no parece ser un mundo válido de Minecraft. Debe contener level.dat.",
                    "Importar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        String nombreDestino = resolverNombreDisponible(parent, server, sanitizarNombreMundo(origen.getFileName().toString()));
        if(nombreDestino == null) return false;

        World mundoDestino = new World(getDirectorioMundo(server, nombreDestino).toString(), nombreDestino);

        try {
            copiarConDimensiones(origen, getDirectorioMundo(server, mundoDestino));
            JOptionPane.showMessageDialog(parent,
                    "Mundo importado en " + DIRECTORIO_MUNDOS + ": " + mundoDestino.getWorldName(),
                    "Importar mundo",
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                    "No se ha podido importar el mundo: " + e.getMessage(),
                    "Importar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    // Exporta un mundo gestionado del servidor a una carpeta elegida por el usuario.
    public static boolean exportarMundo(Server server, World mundo, Component parent) {
        if(server == null || mundo == null || mundo.getWorldName() == null || mundo.getWorldName().isBlank()) {
            JOptionPane.showMessageDialog(parent,
                    "No hay un mundo seleccionado para exportar.",
                    "Exportar mundo",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if(!puedeModificarMundos(server, parent)) return false;
        sincronizarMundosServidor(server);

        Path origen = getDirectorioMundo(server, mundo);
        if(!Files.isDirectory(origen)) {
            JOptionPane.showMessageDialog(parent,
                    "El mundo seleccionado no existe en " + DIRECTORIO_MUNDOS + ".",
                    "Exportar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle("Selecciona la carpeta de destino");
        if(chooser.showDialog(parent, "Exportar") != JFileChooser.APPROVE_OPTION) return false;

        Path destinoBase = chooser.getSelectedFile().toPath();
        Path destino = destinoBase.resolve(mundo.getWorldName());
        if(Files.exists(destino)) {
            JOptionPane.showMessageDialog(parent,
                    "Ya existe una carpeta con ese nombre en el destino seleccionado.",
                    "Exportar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            copiarConDimensiones(origen, destino);
            quitarArchivosDePrevisualizacion(destino);
            JOptionPane.showMessageDialog(parent,
                    "Mundo exportado en: " + destino,
                    "Exportar mundo",
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                    "No se ha podido exportar el mundo: " + e.getMessage(),
                    "Exportar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    // Marca un mundo existente como mundo activo para el siguiente inicio del servidor.
    public static boolean cambiarMundo(Server server, World mundo, Component parent) {
        if(server == null || mundo == null || mundo.getWorldName() == null || mundo.getWorldName().isBlank()) {
            JOptionPane.showMessageDialog(parent,
                    "Selecciona un mundo válido.",
                    "Cambiar mundo",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if(!puedeModificarMundos(server, parent)) return false;
        sincronizarMundosServidor(server);

        Path mundoDir = getDirectorioMundo(server, mundo);
        if(!Files.isDirectory(mundoDir)) {
            JOptionPane.showMessageDialog(parent,
                    "El mundo seleccionado no existe en " + DIRECTORIO_MUNDOS + ".",
                    "Cambiar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            Path serverDir = Path.of(server.getServerDir());
            Properties props = cargarServerProperties(serverDir, true);
            props.setProperty("level-name", construirLevelNameGestionado(mundo));
            aplicarMetadataMundo(mundoDir, props);
            guardarServerProperties(serverDir, props);
            JOptionPane.showMessageDialog(parent,
                    "El mundo activo ahora es: " + mundo.getWorldName() + ".\nSe aplicara al iniciar el servidor.",
                    "Cambiar mundo",
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                    "No se ha podido cambiar el mundo: " + e.getMessage(),
                    "Cambiar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    // Prepara un nuevo mundo y guarda su metadata de generacion para poder activarlo despues.
    public static boolean generarNuevoMundo(Server server, WorldGenerationSettings settings, Component parent) {
        if(settings == null) return false;
        if(!puedeModificarMundos(server, parent)) return false;
        sincronizarMundosServidor(server);

        String nombre = sanitizarNombreMundo(settings.getNombre());
        if(nombre.isBlank()) {
            JOptionPane.showMessageDialog(parent,
                    "El nombre del mundo no puede estar vacío.",
                    "Generar mundo",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }

        World mundo = crearWorld(server, nombre);
        Path mundoDir = getDirectorioMundo(server, mundo);
        if(Files.exists(mundoDir)) {
            JOptionPane.showMessageDialog(parent,
                    "Ya existe un mundo con ese nombre.",
                    "Generar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            Files.createDirectories(mundoDir);
            guardarMetadataMundo(mundoDir, settings);
            if(settings.isActivarAlCrear()) {
                return cambiarMundo(server, mundo, parent);
            }
            JOptionPane.showMessageDialog(parent,
                    "Mundo preparado en " + DIRECTORIO_MUNDOS + ": " + mundo.getWorldName() + ". Se generara al activarlo e iniciar el servidor.",
                    "Generar mundo",
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                    "No se ha podido preparar el nuevo mundo: " + e.getMessage(),
                    "Generar mundo",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    // Devuelve la ruta base donde la aplicacion almacena los mundos del servidor.
    public static Path getDirectorioMundos(Server server) {
        return Path.of(server.getServerDir()).resolve(DIRECTORIO_MUNDOS);
    }

    // Construye la ruta completa de un mundo concreto dentro del servidor.
    public static Path getDirectorioMundo(Server server, World mundo) {
        return getDirectorioMundos(server).resolve(mundo.getWorldName());
    }

    // Comprueba si el servidor permite operaciones de mundos y muestra avisos si no es asi.
    private static boolean puedeModificarMundos(Server server, Component parent) {
        if(server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            JOptionPane.showMessageDialog(parent,
                    "No hay un servidor seleccionado válido.",
                    "Mundos",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if(server.getServerProcess() != null && server.getServerProcess().isAlive()) {
            JOptionPane.showMessageDialog(parent,
                    "Detén el servidor antes de importar, exportar, generar o cambiar mundos.",
                    "Mundos",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    // Valida si una carpeta parece ser un mundo de Minecraft comprobando level.dat.
    private static boolean esMundoValido(Path directorio) {
        return directorio != null
                && Files.isDirectory(directorio)
                && Files.isRegularFile(directorio.resolve("level.dat"));
    }

    // Pide al usuario un nombre alternativo cuando ya existe un mundo con el mismo nombre.
    private static String resolverNombreDisponible(Component parent, Server server, String nombreBase) {
        String candidato = nombreBase == null || nombreBase.isBlank() ? MinecraftConstants.DEFAULT_WORLD_NAME : nombreBase;
        Set<String> existentes = new LinkedHashSet<>();
        for(World mundo : listarMundos(server)) {
            existentes.add(mundo.getWorldName());
        }
        while(existentes.contains(candidato)) {
            String nuevoNombre = JOptionPane.showInputDialog(parent,
                    "Ya existe un mundo con ese nombre. Escribe uno nuevo:",
                    candidato + "-importado");
            if(nuevoNombre == null) return null;
            candidato = sanitizarNombreMundo(nuevoNombre);
            if(candidato.isBlank()) {
                JOptionPane.showMessageDialog(parent,
                        "El nombre indicado no es válido.",
                        "Importar mundo",
                        JOptionPane.WARNING_MESSAGE);
                candidato = nombreBase;
            }
        }
        return candidato;
    }

    // Copia el mundo principal junto con sus dimensiones Nether y End si existen.
    // La previsualización del mapa es una caché de Dora: no forma parte del mundo y puede ocupar varios MB.
    static void quitarArchivosDePrevisualizacion(Path mundoExportado) throws IOException {
        if (mundoExportado == null) {
            return;
        }
        Files.deleteIfExists(mundoExportado.resolve("preview.png"));
        Files.deleteIfExists(mundoExportado.resolve(controlador.world.WorldFilesService.PREVIEW_METADATA_FILE));
    }

    private static void copiarConDimensiones(Path origenBase, Path destinoBase) throws IOException {
        Utilidades.copiarDirectorio(origenBase, destinoBase);
        copiarSiExiste(origenBase.resolveSibling(origenBase.getFileName() + SUFIJO_NETHER), destinoBase.resolveSibling(destinoBase.getFileName() + SUFIJO_NETHER));
        copiarSiExiste(origenBase.resolveSibling(origenBase.getFileName() + SUFIJO_END), destinoBase.resolveSibling(destinoBase.getFileName() + SUFIJO_END));
    }

    // Copia un directorio solo si la ruta de origen existe como carpeta.
    private static void copiarSiExiste(Path origen, Path destino) throws IOException {
        if(Files.isDirectory(origen)) {
            Utilidades.copiarDirectorio(origen, destino);
        }
    }

    // Migra un mundo ubicado en la raiz del servidor al directorio gestionado por la aplicacion.
    private static boolean migrarMundoRaizASistemaGestionado(Path serverDir, World mundo) throws IOException {
        Path origen = serverDir.resolve(mundo.getWorldName());
        Path destino = serverDir.resolve(DIRECTORIO_MUNDOS).resolve(mundo.getWorldName());
        boolean cambios = false;

        if(Files.isDirectory(origen) && !Files.exists(destino)) {
            Utilidades.moverDirectorio(origen, destino);
            cambios = true;
        } else if(!Files.exists(destino)) {
            Files.createDirectories(destino);
            cambios = true;
        }

        cambios |= moverDimensionSiHaceFalta(serverDir.resolve(mundo.getWorldName() + SUFIJO_NETHER), serverDir.resolve(DIRECTORIO_MUNDOS).resolve(mundo.getWorldName() + SUFIJO_NETHER));
        cambios |= moverDimensionSiHaceFalta(serverDir.resolve(mundo.getWorldName() + SUFIJO_END), serverDir.resolve(DIRECTORIO_MUNDOS).resolve(mundo.getWorldName() + SUFIJO_END));
        return cambios;
    }

    // Mueve una dimension asociada al mundo solo cuando el origen existe y el destino aun no.
    private static boolean moverDimensionSiHaceFalta(Path origen, Path destino) throws IOException {
        if(!Files.isDirectory(origen) || Files.exists(destino)) return false;
        Utilidades.moverDirectorio(origen, destino);
        return true;
    }

    // Carga server.properties y lo crea con valores por defecto si se solicita y no existe.
    private static Properties cargarServerProperties(Path serverDir, boolean crearSiNoExiste) throws IOException {
        Path propertiesPath = serverDir.resolve("server.properties");
        if(!Files.exists(propertiesPath) && crearSiNoExiste) {
            try {
                new ServerProperties().escribePropiedades(serverDir.toFile());
            } catch (IllegalAccessException e) {
                throw new IOException("No se ha podido crear server.properties", e);
            }
        }

        Properties props = new Properties();
        if(Files.exists(propertiesPath)) {
            props = Utilidades.cargarPropertiesUtf8(propertiesPath);
        }
        return props;
    }

    // Guarda en disco las propiedades del servidor recibidas.
    private static void guardarServerProperties(Path serverDir, Properties props) throws IOException {
        Path propertiesPath = serverDir.resolve("server.properties");
        Utilidades.guardarPropertiesUtf8(propertiesPath, props, null);
    }

    // Guarda en un archivo auxiliar la metadata necesaria para regenerar o activar un mundo.
    private static void guardarMetadataMundo(Path mundoDir, WorldGenerationSettings settings) throws IOException {
        Properties metadata = new Properties();
        metadata.setProperty("display-name", settings.getNombre());
        metadata.setProperty("level-seed", Objects.toString(settings.getSemilla(), ""));
        metadata.setProperty("level-type", settings.getTipoMundo());
        metadata.setProperty("generator-settings", Objects.toString(settings.getGeneratorSettings(), ""));
        metadata.setProperty("generate-structures", String.valueOf(settings.isGenerarEstructuras()));
        metadata.setProperty("hardcore", String.valueOf(settings.isHardcore()));
        metadata.setProperty("gamemode", settings.getGamemode());
        metadata.setProperty("difficulty", settings.getDificultad());
        metadata.setProperty("allow-nether", String.valueOf(settings.isPermitirNether()));
        metadata.setProperty("initial-enabled-packs", Objects.toString(settings.getInitialEnabledPacks(), ""));
        metadata.setProperty("initial-disabled-packs", Objects.toString(settings.getInitialDisabledPacks(), ""));
        metadata.setProperty("region-file-compression", Objects.toString(settings.getRegionFileCompression(), ""));

        try(FileOutputStream fos = new FileOutputStream(mundoDir.resolve(META_ARCHIVO).toFile())) {
            metadata.store(fos, "Dora managed world metadata");
        }
    }

    // Copia al server.properties la metadata persistida del mundo seleccionado.
    private static void aplicarMetadataMundo(Path mundoDir, Properties props) throws IOException {
        Path metadataPath = resolverMetadataMundo(mundoDir);
        if(!Files.exists(metadataPath)) return;

        Properties metadata = new Properties();
        try(FileInputStream fis = new FileInputStream(metadataPath.toFile())) {
            metadata.load(fis);
        }

        copiarPropiedad(metadata, props, "level-seed");
        copiarPropiedad(metadata, props, "level-type");
        copiarPropiedad(metadata, props, "generator-settings");
        copiarPropiedad(metadata, props, "generate-structures");
        copiarPropiedad(metadata, props, "hardcore");
        copiarPropiedad(metadata, props, "gamemode");
        copiarPropiedad(metadata, props, "difficulty");
        copiarPropiedad(metadata, props, "allow-nether");
        copiarPropiedad(metadata, props, "initial-enabled-packs");
        copiarPropiedad(metadata, props, "initial-disabled-packs");
        copiarPropiedad(metadata, props, "region-file-compression");
    }

    // Carga un mundo desde su carpeta usando solo su ruta completa y el nombre de la carpeta.
    private static World cargarMundo(Path mundoDir) {
        String nombreMundo = mundoDir != null && mundoDir.getFileName() != null
                ? mundoDir.getFileName().toString()
                : MinecraftConstants.DEFAULT_WORLD_NAME;
        return new World(mundoDir == null ? "" : mundoDir.toString(), nombreMundo);
    }

    // Crea un objeto World a partir del servidor y del nombre de carpeta del mundo.
    private static World crearWorld(Server server, String nombreMundo) {
        return new World(getDirectorioMundo(server, nombreMundo).toString(), nombreMundo);
    }

    // Comprueba si un World corresponde al nombre de mundo indicado.
    private static boolean mismoMundo(World mundo, String nombreMundo) {
        return mundo != null && Objects.equals(mundo.getWorldName(), nombreMundo);
    }

    // Copia una propiedad concreta entre dos conjuntos de propiedades si existe en el origen.
    private static void copiarPropiedad(Properties origen, Properties destino, String key) {
        String value = origen.getProperty(key);
        if(value != null) {
            destino.setProperty(key, value);
        }
    }

    private static Path resolverMetadataMundo(Path mundoDir) {
        return mundoDir.resolve(META_ARCHIVO);
    }

    // Indica si el level-name ya apunta al directorio gestionado de mundos.
    private static boolean esRutaGestionada(String levelName) {
        if(levelName == null) return false;
        String normalizado = levelName.replace('\\', '/');
        return normalizado.startsWith(DIRECTORIO_MUNDOS + "/");
    }

    // Construye el valor de level-name usando la ruta gestionada por la aplicacion.
    private static String construirLevelNameGestionado(World mundo) {
        return DIRECTORIO_MUNDOS + "/" + mundo.getWorldName();
    }

    // Construye la ruta completa de un mundo concreto a partir de su nombre interno.
    private static Path getDirectorioMundo(Server server, String nombreMundo) {
        return getDirectorioMundos(server).resolve(nombreMundo);
    }

    // Extrae y normaliza el nombre de mundo a partir del valor guardado en level-name.
    private static String normalizarNombreMundo(String levelName) {
        if(levelName == null) return MinecraftConstants.DEFAULT_WORLD_NAME;
        String normalizado = levelName.replace('\\', '/').trim();
        if(normalizado.startsWith(DIRECTORIO_MUNDOS + "/")) {
            normalizado = normalizado.substring((DIRECTORIO_MUNDOS + "/").length());
        }
        int ultimaBarra = normalizado.lastIndexOf('/');
        if(ultimaBarra >= 0 && ultimaBarra + 1 < normalizado.length()) {
            normalizado = normalizado.substring(ultimaBarra + 1);
        }
        return sanitizarNombreMundo(normalizado);
    }

    // Limpia un nombre de mundo para que sea valido como nombre de carpeta.
    private static String sanitizarNombreMundo(String nombre) {
        if(nombre == null) return "";
        return nombre.trim()
                .replaceAll("[\\/:*?\"<>|]", "-")
                .replaceAll("\\s+", " ");
    }

    public static final class WorldGenerationSettings {
        private final String nombre;
        private final String semilla;
        private final String tipoMundo;
        private final String generatorSettings;
        private final boolean generarEstructuras;
        private final boolean hardcore;
        private final String gamemode;
        private final String dificultad;
        private final boolean permitirNether;
        private final String initialEnabledPacks;
        private final String initialDisabledPacks;
        private final String regionFileCompression;
        private final boolean activarAlCrear;

        // Construye un conjunto inmutable de opciones para preparar un nuevo mundo.
        public WorldGenerationSettings(String nombre,
                                       String semilla,
                                       String tipoMundo,
                                       String generatorSettings,
                                       boolean generarEstructuras,
                                       boolean hardcore,
                                       String gamemode,
                                       String dificultad,
                                       boolean permitirNether,
                                       String initialEnabledPacks,
                                       String initialDisabledPacks,
                                       String regionFileCompression,
                                       boolean activarAlCrear) {
            this.nombre = nombre;
            this.semilla = semilla;
            this.tipoMundo = tipoMundo;
            this.generatorSettings = generatorSettings;
            this.generarEstructuras = generarEstructuras;
            this.hardcore = hardcore;
            this.gamemode = gamemode;
            this.dificultad = dificultad;
            this.permitirNether = permitirNether;
            this.initialEnabledPacks = initialEnabledPacks;
            this.initialDisabledPacks = initialDisabledPacks;
            this.regionFileCompression = regionFileCompression;
            this.activarAlCrear = activarAlCrear;
        }

        // Devuelve el nombre solicitado para el mundo.
        public String getNombre() {
            return nombre;
        }

        // Devuelve la semilla configurada para la generacion del mundo.
        public String getSemilla() {
            return semilla;
        }

        // Devuelve el tipo de mundo configurado.
        public String getTipoMundo() {
            return tipoMundo;
        }

        // Devuelve los parametros avanzados del generador del mundo.
        public String getGeneratorSettings() {
            return generatorSettings;
        }

        // Indica si el mundo debe generarse con estructuras.
        public boolean isGenerarEstructuras() {
            return generarEstructuras;
        }

        // Indica si el nuevo mundo se preparara en modo hardcore.
        public boolean isHardcore() {
            return hardcore;
        }

        // Devuelve el modo de juego por defecto del mundo.
        public String getGamemode() {
            return gamemode;
        }

        // Devuelve la dificultad configurada para el mundo.
        public String getDificultad() {
            return dificultad;
        }

        // Indica si el mundo permitira acceder al Nether.
        public boolean isPermitirNether() {
            return permitirNether;
        }

        // Devuelve los data packs activados en la creacion inicial del mundo.
        public String getInitialEnabledPacks() {
            return initialEnabledPacks;
        }

        // Devuelve los data packs desactivados en la creacion inicial del mundo.
        public String getInitialDisabledPacks() {
            return initialDisabledPacks;
        }

        // Devuelve el metodo de compresion de regiones asociado al mundo.
        public String getRegionFileCompression() {
            return regionFileCompression;
        }

        // Indica si el mundo debe quedar seleccionado justo despues de crearlo.
        public boolean isActivarAlCrear() {
            return activarAlCrear;
        }
    }
}
