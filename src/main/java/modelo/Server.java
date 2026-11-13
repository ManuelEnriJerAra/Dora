/*
* Fichero: Server.java
*
* Autor: Manuel Enrique Jerónimo Aragón
* Fecha: 16/01/2026
*
* Descripción:
* Define la clase Server, que representa un servidor de Minecraft. Desde el objeto servidor podemos acceder a todos
* sus datos. Tiene los atributos ServerConfig y ServerProperties, que son otras clases de este mismo proyecto.
* El servidor contiene toda la información del servidor, sin embargo, no realiza ninguna gestión, de eso se encarga la
* clase GestorServidores.
* */


package modelo;

import java.io.*;
import java.awt.image.BufferedImage;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import controlador.Utilidades;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Setter;
import lombok.Getter;
import modelo.automation.ServerAutomationRule;
import modelo.extensions.ServerCapability;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerLoader;
import modelo.extensions.ServerPlatform;

import javax.swing.*;
import javax.imageio.ImageIO;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)

public class Server {
    // Muestras de estadísticas a resolución de 1 s: más allá se agregan en el histórico.
    // Guardar semanas a 1 s generaba ficheros de cientos de MB reescritos cada pocos segundos.
    public static final int VENTANA_RECIENTE_POR_DEFECTO_SEGUNDOS = 6 * 60 * 60;
    public static final int VENTANA_RECIENTE_MAXIMA_SEGUNDOS = 72 * 60 * 60;

    // ===== DATOS PERSISTENTES =====
    private String id; // identificador único del servidor, es persistente, lo guardamos en el JSON
    private String displayName; // nombre que el usuario le da al servidor, es persistente, lo guardamos en el JSON
    private String version; // versión del servidor
    private String tipo; // tipo del servidor (Vanilla, Forge, Fabric, etc.)
    private ServerPlatform platform; // plataforma interna del servidor
    private ServerLoader loader; // loader/ecosistema técnico principal
    private String loaderVersion; // versión del loader, si se conoce
    private Set<ServerCapability> capabilities; // capacidades detectadas o soportadas por la app
    private ServerEcosystemType ecosystemType; // agrupa el servidor en vanilla/mods/plugins
    private List<ServerExtension> extensions; // extensiones detectadas o gestionadas localmente
    private String serverDir; // carpeta del servidor
    private ServerConfig serverConfig; // contiene XMS RAM, XMX RAM y el puerto
    private Integer ordenLista; // orden manual/base de la lista, preparado para drag and drop futuro
    private Boolean favorito; // indica si el servidor se muestra en el bloque superior de favoritos
    private Integer ordenFavorito; // orden estable entre favoritos, según cuando se marcaron por primera vez
    private Integer estadisticasRangoSegundos; // rango visible preferido en el panel de estadísticas
    private Boolean estadisticasPersistenciaActiva; // si el histórico se guarda en disco para este servidor
    private Integer estadisticasVentanaRecienteSegundos; // muestras recientes conservadas a resolución de 1 segúndo
    private Integer estadisticasResolucionHistoricaSegundos; // bucket para compactar histórico antiguo
    private Boolean estadisticasCpuActiva;
    private Boolean estadisticasCpuHistorial;
    private Boolean estadisticasRamActiva;
    private Boolean estadisticasRamHistorial;
    private Boolean estadisticasDiscoActiva;
    private Boolean estadisticasDiscoHistorial;
    private Boolean estadisticasJugadoresActiva;
    private Boolean estadisticasJugadoresHistorial;
    private String previewRenderProfileId;
    private Boolean previewRenderRealtime;
    private Boolean previewShowSpawn;
    private Boolean previewShowPlayers;
    private Boolean previewShowChunkGrid;
    private Boolean previewUseWholeMap;
    private Integer previewRenderLimitPixels;
    private String previewRenderCenterId;
    private List<ServerAutomationRule> automationRules;

    // ===== DATOS DE EJECUCIÓN =====
    // lo que sea transient no se va a guardar en el JSON
    @JsonIgnore private transient Process serverProcess; // proceso del servidor NUNCA DEBE GUARDARSE, ES DE TIPO RUNTIME
    @JsonIgnore transient StringBuilder consoleBuffer = new StringBuilder(); // buffer de consola NO LO GUARDO
    @JsonIgnore transient List<Consumer<String>> consoleListeners = new ArrayList<>(); // es transient, no se guarda
    @JsonIgnore transient Boolean logReaderIniciado = false; // NO LO GUARDO
    @JsonIgnore transient Deque<String> rawLogLineQueue = new ArrayDeque<>();
    @JsonIgnore transient Boolean restartPending = false; // reinicio pendiente
    @JsonIgnore transient Boolean iniciando = false; // arranque en progreso
    @JsonIgnore transient Boolean deteniendo = false; // parada solicitada, el proceso sigue vivo
    @JsonIgnore transient long paradaSolicitadaMillis = 0L; // momento en que se pidió la parada

    // ===== CONSTRUCTORES =====

    // constructor por defecto
    public Server(){
        id = UUID.randomUUID().toString();
        this.platform = ServerPlatform.UNKNOWN;
        this.loader = ServerLoader.UNKNOWN;
        this.loaderVersion = null;
        this.capabilities = EnumSet.noneOf(ServerCapability.class);
        this.ecosystemType = ServerEcosystemType.UNKNOWN;
        this.extensions = new ArrayList<>();
        this.serverConfig = new ServerConfig();
        this.estadisticasRangoSegundos = 300;
        this.estadisticasPersistenciaActiva = true;
        this.estadisticasVentanaRecienteSegundos = VENTANA_RECIENTE_POR_DEFECTO_SEGUNDOS;
        this.estadisticasResolucionHistoricaSegundos = 60;
        this.estadisticasCpuActiva = true;
        this.estadisticasCpuHistorial = true;
        this.estadisticasRamActiva = true;
        this.estadisticasRamHistorial = true;
        this.estadisticasDiscoActiva = true;
        this.estadisticasDiscoHistorial = true;
        this.estadisticasJugadoresActiva = false;
        this.estadisticasJugadoresHistorial = true;
        this.previewRenderProfileId = "quality";
        this.previewRenderRealtime = false;
        this.previewShowSpawn = false;
        this.previewShowPlayers = false;
        this.previewShowChunkGrid = false;
        this.previewUseWholeMap = false;
        this.previewRenderLimitPixels = 256;
        this.previewRenderCenterId = "spawn";
        this.automationRules = new ArrayList<>();
    }

    public String getTipo() {
        if (tipo != null && !tipo.isBlank()) {
            return tipo;
        }
        if (platform != null && platform != ServerPlatform.UNKNOWN) {
            return platform.getLegacyTypeName();
        }
        return null;
    }

    public void setTipo(String tipo) {
        this.tipo = normalizarTexto(tipo);
        ServerPlatform migratedPlatform = ServerPlatform.fromLegacyType(this.tipo);
        if (migratedPlatform != ServerPlatform.UNKNOWN || this.platform == null || this.platform == ServerPlatform.UNKNOWN) {
            this.platform = migratedPlatform;
        }
        aplicarMetadatosCompatiblesConPlataforma(false);
    }

    public void setPlatform(ServerPlatform platform) {
        this.platform = platform == null ? ServerPlatform.UNKNOWN : platform;
        if (this.platform != ServerPlatform.UNKNOWN) {
            this.tipo = this.platform.getLegacyTypeName();
        } else if (this.tipo != null && this.tipo.isBlank()) {
            this.tipo = null;
        }
        aplicarMetadatosCompatiblesConPlataforma(false);
    }

    public void setCapabilities(Set<ServerCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            this.capabilities = EnumSet.noneOf(ServerCapability.class);
            return;
        }
        this.capabilities = EnumSet.copyOf(capabilities);
    }

    @JsonIgnore
    public boolean isVanillaServer() {
        return resolvedPlatform().isVanillaPlatform();
    }

    @JsonIgnore
    public boolean isModServer() {
        return resolvedPlatform().isModPlatform();
    }

    @JsonIgnore
    public boolean isPluginServer() {
        return resolvedPlatform().isPluginPlatform();
    }

    @JsonIgnore
    public boolean supportsExtensions() {
        ServerPlatform resolved = resolvedPlatform();
        if (resolved.isKnownPlatform()) {
            return resolved.supportsExtensions();
        }
        ServerEcosystemType ecosystem = resolvedEcosystemType();
        return ecosystem == ServerEcosystemType.MODS || ecosystem == ServerEcosystemType.PLUGINS;
    }

    @JsonIgnore
    public boolean supportsModExtensions() {
        ServerPlatform resolved = resolvedPlatform();
        if (resolved.isKnownPlatform()) {
            return resolved.isModPlatform();
        }
        return resolvedEcosystemType() == ServerEcosystemType.MODS;
    }

    @JsonIgnore
    public boolean supportsPluginExtensions() {
        ServerPlatform resolved = resolvedPlatform();
        if (resolved.isKnownPlatform()) {
            return resolved.isPluginPlatform();
        }
        return resolvedEcosystemType() == ServerEcosystemType.PLUGINS;
    }

    @JsonIgnore
    public boolean hasCapability(ServerCapability capability) {
        return capability != null && capabilities != null && capabilities.contains(capability);
    }

    public boolean migrarModeloLegacy() {
        boolean cambios = false;

        if ((tipo == null || tipo.isBlank()) && platform != null && platform != ServerPlatform.UNKNOWN) {
            tipo = platform.getLegacyTypeName();
            cambios = true;
        }

        if ((platform == null || platform == ServerPlatform.UNKNOWN) && tipo != null && !tipo.isBlank()) {
            ServerPlatform migratedPlatform = ServerPlatform.fromLegacyType(tipo);
            if (migratedPlatform != ServerPlatform.UNKNOWN || platform == null) {
                platform = migratedPlatform;
                cambios = true;
            }
        }

        if (platform == null) {
            platform = ServerPlatform.UNKNOWN;
            cambios = true;
        }
        if (loader == null) {
            loader = platform.getDefaultLoader();
            cambios = true;
        }
        if (ecosystemType == null) {
            ecosystemType = platform.getDefaultEcosystemType();
            cambios = true;
        }
        if (capabilities == null) {
            capabilities = EnumSet.noneOf(ServerCapability.class);
            cambios = true;
        } else if (!(capabilities instanceof EnumSet<?>)) {
            capabilities = capabilities.isEmpty()
                    ? EnumSet.noneOf(ServerCapability.class)
                    : EnumSet.copyOf(capabilities);
            cambios = true;
        }
        if (extensions == null) {
            extensions = new ArrayList<>();
            cambios = true;
        }

        if (aplicarMetadatosCompatiblesConPlataforma(true)) {
            cambios = true;
        }
        if (normalizarExtensiones()) {
            cambios = true;
        }
        if (normalizarAutomatizaciones()) {
            cambios = true;
        }

        return cambios;
    }

    private boolean aplicarMetadatosCompatiblesConPlataforma(boolean soloSiFalta) {
        boolean cambios = false;
        ServerPlatform resolvedPlatform = platform == null ? ServerPlatform.UNKNOWN : platform;

        if (!soloSiFalta || loader == null || loader == ServerLoader.UNKNOWN) {
            ServerLoader targetLoader = resolvedPlatform.getDefaultLoader();
            if (targetLoader != null && loader != targetLoader) {
                loader = targetLoader;
                cambios = true;
            }
        }

        if (!soloSiFalta || ecosystemType == null || ecosystemType == ServerEcosystemType.UNKNOWN) {
            ServerEcosystemType targetEcosystem = resolvedPlatform.getDefaultEcosystemType();
            if (targetEcosystem != null && ecosystemType != targetEcosystem) {
                ecosystemType = targetEcosystem;
                cambios = true;
            }
        }

        Set<ServerCapability> targetCapabilities = resolvedPlatform.defaultCapabilities();
        if (capabilities == null) {
            capabilities = EnumSet.noneOf(ServerCapability.class);
            cambios = true;
        }
        if (!capabilities.equals(targetCapabilities)) {
            capabilities = targetCapabilities.isEmpty()
                    ? EnumSet.noneOf(ServerCapability.class)
                    : EnumSet.copyOf(targetCapabilities);
            cambios = true;
        }

        return cambios;
    }

    private boolean normalizarAutomatizaciones() {
        if (automationRules == null) {
            automationRules = new ArrayList<>();
            return true;
        }
        boolean cambios = false;
        Set<String> usedIds = new HashSet<>();
        List<ServerAutomationRule> normalizedRules = new ArrayList<>();
        for (ServerAutomationRule rule : automationRules) {
            if (rule == null) {
                cambios = true;
                continue;
            }
            String id = rule.getId();
            if (id == null || id.isBlank() || usedIds.contains(id)) {
                id = UUID.randomUUID().toString();
                rule.setId(id);
                cambios = true;
            }
            usedIds.add(id);
            if (rule.getEnabled() == null) {
                rule.setEnabled(false);
                cambios = true;
            }
            normalizedRules.add(rule);
        }
        if (normalizedRules.size() != automationRules.size()) {
            cambios = true;
        }
        if (cambios) {
            automationRules = normalizedRules;
        }
        return cambios;
    }

    private ServerPlatform resolvedPlatform() {
        return platform == null ? ServerPlatform.UNKNOWN : platform;
    }

    private ServerEcosystemType resolvedEcosystemType() {
        return ecosystemType == null ? resolvedPlatform().getDefaultEcosystemType() : ecosystemType;
    }

    private boolean normalizarExtensiones() {
        if (extensions == null) {
            extensions = new ArrayList<>();
            return true;
        }
        boolean cambios = false;
        for (ServerExtension extension : extensions) {
            if (extension == null) {
                continue;
            }
            if (extension.getId() == null || extension.getId().isBlank()) {
                extension.setId(UUID.randomUUID().toString());
                cambios = true;
            }
            if (extension.getSource() == null) {
                extension.setSource(new modelo.extensions.ExtensionSource());
                cambios = true;
            }
            if (extension.getExtensionType() == null) {
                extension.setExtensionType(ServerExtensionType.UNKNOWN);
                cambios = true;
            }
            if (extension.getPlatform() == null) {
                extension.setPlatform(ServerPlatform.UNKNOWN);
                cambios = true;
            }
            if (extension.getInstallState() == null) {
                extension.setInstallState(modelo.extensions.ExtensionInstallState.UNKNOWN);
                cambios = true;
            }
            if (extension.getLocalMetadata() == null) {
                extension.setLocalMetadata(new modelo.extensions.ExtensionLocalMetadata());
                cambios = true;
            } else {
                if (extension.getLocalMetadata().getEnabled() == null) {
                    extension.getLocalMetadata().setEnabled(Boolean.TRUE);
                    cambios = true;
                }
                if (extension.getLocalMetadata().getUpdateState() == null) {
                    extension.getLocalMetadata().setUpdateState(modelo.extensions.ExtensionUpdateState.UNKNOWN);
                    cambios = true;
                }
                if (extension.getLocalMetadata().getCategories() == null) {
                    extension.getLocalMetadata().setCategories(new ArrayList<>());
                    cambios = true;
                }
                if (extension.getLocalMetadata().getDependencies() == null) {
                    extension.getLocalMetadata().setDependencies(new ArrayList<>());
                    cambios = true;
                }
            }
        }
        return cambios;
    }

    private String normalizarTexto(String texto) {
        if (texto == null) return null;
        String trimmed = texto.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    // ===== CONSOLA =====

    @JsonIgnore static final Pattern HORA = Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2}]\\s*");

    private String conHoraSiFalta(String linea){
        if(linea==null) return null;
        if(HORA.matcher(linea).find()) return linea; // ya tiene la hora
        String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return "["+hora+"] "+linea;
    }

    public void appendConsoleLinea(String linea){
        List<Consumer<String>> listenersSnapshot;
        synchronized (this) {
            linea = Utilidades.limpiarSecuenciasConsola(linea);
            linea = conHoraSiFalta(linea);
            if(!Objects.equals(linea, "")){
                consoleBuffer.append(linea).append("\n");
            }
            // limitamos el tamaño
            if(consoleBuffer.length()>300_000){
                consoleBuffer.delete(0, consoleBuffer.length()-200_000);
            }
            appendRawLogLine(linea);
            listenersSnapshot = new ArrayList<>(consoleListeners);
        }
        for(var listener: listenersSnapshot) {
            try {
                listener.accept(linea);
            } catch (RuntimeException ex) {
                System.err.println("Error notificando linea de consola: " + ex.getMessage());
                ex.printStackTrace(System.err);
            }
        }
    }

    @JsonIgnore public synchronized String getConsoleText(){
        return consoleBuffer.toString();
    }

    public synchronized void addConsoleListener(Consumer<String> listener){
        consoleListeners.add(listener);
    }

    public synchronized void removeConsoleListener(Consumer<String> listener){
        consoleListeners.remove(listener);
    }

   @JsonIgnore synchronized int getConsoleListenerCount(){
        return consoleListeners.size();
   }

    public synchronized void clearConsoleListener(){
        consoleListeners.clear();
    }

    public synchronized void appendRawLogLine(String linea){
        if(rawLogLineQueue == null) {
            rawLogLineQueue = new ArrayDeque<>();
        }
        rawLogLineQueue.addLast(linea);

        // límite para no sobrecargar memoria
        if(rawLogLineQueue.size()>5000){
            rawLogLineQueue.removeFirst();
        }
    }

    public synchronized List<String> getRawLogLines(){
        if(rawLogLineQueue == null) {
            rawLogLineQueue = new ArrayDeque<>();
        }
        while(rawLogLineQueue.size()>5000) {
            rawLogLineQueue.removeFirst();
        }
        return new ArrayList<>(rawLogLineQueue);
    }

    // ===== GET =====

    // devuelve el icono, si no tiene usa el predeterminado
    @JsonIgnore
    public ImageIcon getServerIconOrUseDefault(){
        if(serverDir != null && !serverDir.isBlank()){ // si la carpeta existe intenta coger la imagen
            File iconFile = new File(serverDir, "server-icon.png");
            if (iconFile.exists()){
                try{
                    // lo leemos como imagen para comprobar que funciona correctamente
                    BufferedImage img = ImageIO.read(iconFile);
                    if(img != null) return new ImageIcon(img);
                } catch (IOException ignored){
                }
                return new ImageIcon(iconFile.getAbsolutePath());
            }
        }
        // si no se ha encontrado usamos la imagen por defecto
        ImageIcon porDefecto = new ImageIcon(getClass().getResource("/doraimages/default_image.png"));
        return porDefecto;
    }
}

