package controlador;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import modelo.DoraConfig;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class GestorConfiguracion {
    private static final String JSON_FILE = "dora-config.json";
    private static final String DEFAULT_THEME_CLASS =
            "com.formdev.flatlaf.intellijthemes.FlatArcIJTheme";
    private static final int DEFAULT_STATS_RANGE_SECONDS = 60;
    private static final boolean DEFAULT_STATS_PERSISTENCE_ENABLED = true;
    private static final int DEFAULT_STATS_RECENT_WINDOW_SECONDS = 24 * 60 * 60;
    private static final int DEFAULT_STATS_HISTORICAL_RESOLUTION_SECONDS = 60;
    private static final boolean DEFAULT_PLAYER_LIST_COMPACT = false;
    private static final boolean DEFAULT_EXTENSION_LIST_COMPACT = false;
    private static final boolean DEFAULT_CONSOLE_SIMPLE_VIEW = true;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GestorConfiguracion() {
    }

    public static DoraConfig cargarConfiguracion() {
        File file = getJsonFile();
        if (!file.exists()) {
            DoraConfig config = crearConfiguracionPorDefecto();
            guardarConfiguracion(config);
            return config;
        }

        try {
            DoraConfig config = MAPPER.readValue(file, DoraConfig.class);
            if (config == null || config.getTemaClassName() == null || config.getTemaClassName().isBlank()) {
                config = crearConfiguracionPorDefecto();
                guardarConfiguracion(config);
            } else if (normalizarConfiguracion(config)) {
                guardarConfiguracion(config);
            }
            return config;
        } catch (JacksonException e) {
            System.err.println("Error al cargar " + JSON_FILE + ": " + e.getMessage());
            // Guardamos una copia antes de restablecer los valores por defecto para no perder la configuración ilegible.
            Utilidades.respaldarArchivoIlegible(file.toPath());
            DoraConfig config = crearConfiguracionPorDefecto();
            guardarConfiguracion(config);
            return config;
        }
    }

    public static void guardarConfiguracion(DoraConfig config) {
        if (config == null) return;
        if (config.getTemaClassName() == null || config.getTemaClassName().isBlank()) {
            config.setTemaClassName(DEFAULT_THEME_CLASS);
        }
        if (config.getEstadisticasRangoSegundos() == null || config.getEstadisticasRangoSegundos() <= 0) {
            config.setEstadisticasRangoSegundos(DEFAULT_STATS_RANGE_SECONDS);
        }
        if (config.getEstadisticasPersistenciaActiva() == null) {
            config.setEstadisticasPersistenciaActiva(DEFAULT_STATS_PERSISTENCE_ENABLED);
        }
        if (config.getEstadisticasVentanaRecienteSegundos() == null || config.getEstadisticasVentanaRecienteSegundos() <= 0) {
            config.setEstadisticasVentanaRecienteSegundos(DEFAULT_STATS_RECENT_WINDOW_SECONDS);
        }
        if (config.getEstadisticasResolucionHistoricaSegundos() == null || config.getEstadisticasResolucionHistoricaSegundos() <= 0) {
            config.setEstadisticasResolucionHistoricaSegundos(DEFAULT_STATS_HISTORICAL_RESOLUTION_SECONDS);
        }
        if (config.getJugadoresListaCompacta() == null) {
            config.setJugadoresListaCompacta(DEFAULT_PLAYER_LIST_COMPACT);
        }
        if (config.getExtensionesListaCompacta() == null) {
            config.setExtensionesListaCompacta(DEFAULT_EXTENSION_LIST_COMPACT);
        }
        if (config.getConsolaVistaSimple() == null) {
            config.setConsolaVistaSimple(DEFAULT_CONSOLE_SIMPLE_VIEW);
        }

        try {
            File file = getJsonFile();
            Utilidades.escribirArchivoAtomico(
                    file.toPath(),
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(config)
            );
        } catch (Exception e) {
            System.err.println("Error al guardar " + JSON_FILE + ": " + e.getMessage());
        }
    }

    public static void guardarTema(String temaClassName) {
        DoraConfig config = cargarConfiguracion();
        config.setTemaClassName(temaClassName);
        guardarConfiguracion(config);
    }

    public static String getTemaGuardado() {
        return cargarConfiguracion().getTemaClassName();
    }

    public static String getTemaPorDefecto() {
        return DEFAULT_THEME_CLASS;
    }

    public static int getEstadisticasRangoSegundos() {
        Integer seconds = cargarConfiguracion().getEstadisticasRangoSegundos();
        return seconds == null || seconds <= 0 ? DEFAULT_STATS_RANGE_SECONDS : seconds;
    }

    public static void guardarEstadisticasRangoSegundos(int seconds) {
        DoraConfig config = cargarConfiguracion();
        config.setEstadisticasRangoSegundos(seconds > 0 ? seconds : DEFAULT_STATS_RANGE_SECONDS);
        guardarConfiguracion(config);
    }

    public static boolean isEstadisticasPersistenciaActiva() {
        Boolean enabled = cargarConfiguracion().getEstadisticasPersistenciaActiva();
        return enabled == null ? DEFAULT_STATS_PERSISTENCE_ENABLED : enabled;
    }

    public static int getEstadisticasVentanaRecienteSegundos() {
        Integer seconds = cargarConfiguracion().getEstadisticasVentanaRecienteSegundos();
        return seconds == null || seconds <= 0 ? DEFAULT_STATS_RECENT_WINDOW_SECONDS : seconds;
    }

    public static int getEstadisticasResolucionHistoricaSegundos() {
        Integer seconds = cargarConfiguracion().getEstadisticasResolucionHistoricaSegundos();
        return seconds == null || seconds <= 0 ? DEFAULT_STATS_HISTORICAL_RESOLUTION_SECONDS : seconds;
    }

    public static void guardarEstadisticasPersistenciaActiva(boolean enabled) {
        DoraConfig config = cargarConfiguracion();
        config.setEstadisticasPersistenciaActiva(enabled);
        guardarConfiguracion(config);
    }

    public static void guardarEstadisticasVentanaRecienteSegundos(int seconds) {
        DoraConfig config = cargarConfiguracion();
        config.setEstadisticasVentanaRecienteSegundos(seconds > 0 ? seconds : DEFAULT_STATS_RECENT_WINDOW_SECONDS);
        guardarConfiguracion(config);
    }

    public static void guardarEstadisticasResolucionHistoricaSegundos(int seconds) {
        DoraConfig config = cargarConfiguracion();
        config.setEstadisticasResolucionHistoricaSegundos(seconds > 0 ? seconds : DEFAULT_STATS_HISTORICAL_RESOLUTION_SECONDS);
        guardarConfiguracion(config);
    }

    public static boolean isJugadoresListaCompacta() {
        Boolean compacta = cargarConfiguracion().getJugadoresListaCompacta();
        return compacta == null ? DEFAULT_PLAYER_LIST_COMPACT : compacta;
    }

    public static void guardarJugadoresListaCompacta(boolean compacta) {
        DoraConfig config = cargarConfiguracion();
        config.setJugadoresListaCompacta(compacta);
        guardarConfiguracion(config);
    }

    public static boolean isExtensionesListaCompacta() {
        Boolean compacta = cargarConfiguracion().getExtensionesListaCompacta();
        return compacta == null ? DEFAULT_EXTENSION_LIST_COMPACT : compacta;
    }

    public static void guardarExtensionesListaCompacta(boolean compacta) {
        DoraConfig config = cargarConfiguracion();
        config.setExtensionesListaCompacta(compacta);
        guardarConfiguracion(config);
    }

    public static boolean isConsolaVistaSimple() {
        Boolean vistaSimple = cargarConfiguracion().getConsolaVistaSimple();
        return vistaSimple == null ? DEFAULT_CONSOLE_SIMPLE_VIEW : vistaSimple;
    }

    public static void guardarConsolaVistaSimple(boolean vistaSimple) {
        DoraConfig config = cargarConfiguracion();
        config.setConsolaVistaSimple(vistaSimple);
    }

    public static String getCurseForgeApiKey() {
        String apiKey = cargarConfiguracion().getCurseForgeApiKey();
        return apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
    }

    public static void guardarCurseForgeApiKey(String apiKey) {
        DoraConfig config = cargarConfiguracion();
        config.setCurseForgeApiKey(apiKey == null || apiKey.isBlank() ? null : apiKey.trim());
        guardarConfiguracion(config);
    }

    public static Path getBaseDirectory() {
        return AppPaths.rootDirectory();
    }

    private static DoraConfig crearConfiguracionPorDefecto() {
        return new DoraConfig(
                DEFAULT_THEME_CLASS,
                DEFAULT_STATS_RANGE_SECONDS,
                DEFAULT_STATS_PERSISTENCE_ENABLED,
                DEFAULT_STATS_RECENT_WINDOW_SECONDS,
                DEFAULT_STATS_HISTORICAL_RESOLUTION_SECONDS,
                DEFAULT_PLAYER_LIST_COMPACT,
                DEFAULT_CONSOLE_SIMPLE_VIEW
        );
    }

    private static File getJsonFile() {
        return AppPaths.configDirectory().resolve(JSON_FILE).toFile();
    }

    private static boolean normalizarConfiguracion(DoraConfig config) {
        boolean changed = false;
        if (config.getEstadisticasRangoSegundos() == null || config.getEstadisticasRangoSegundos() <= 0) {
            config.setEstadisticasRangoSegundos(DEFAULT_STATS_RANGE_SECONDS);
            changed = true;
        }
        if (config.getEstadisticasPersistenciaActiva() == null) {
            config.setEstadisticasPersistenciaActiva(DEFAULT_STATS_PERSISTENCE_ENABLED);
            changed = true;
        }
        if (config.getEstadisticasVentanaRecienteSegundos() == null || config.getEstadisticasVentanaRecienteSegundos() <= 0) {
            config.setEstadisticasVentanaRecienteSegundos(DEFAULT_STATS_RECENT_WINDOW_SECONDS);
            changed = true;
        }
        if (config.getEstadisticasResolucionHistoricaSegundos() == null || config.getEstadisticasResolucionHistoricaSegundos() <= 0) {
            config.setEstadisticasResolucionHistoricaSegundos(DEFAULT_STATS_HISTORICAL_RESOLUTION_SECONDS);
            changed = true;
        }
        if (config.getJugadoresListaCompacta() == null) {
            config.setJugadoresListaCompacta(DEFAULT_PLAYER_LIST_COMPACT);
            changed = true;
        }
        if (config.getExtensionesListaCompacta() == null) {
            config.setExtensionesListaCompacta(DEFAULT_EXTENSION_LIST_COMPACT);
            changed = true;
        }
        if (config.getConsolaVistaSimple() == null) {
            config.setConsolaVistaSimple(DEFAULT_CONSOLE_SIMPLE_VIEW);
            changed = true;
        }
        return changed;
    }
}
