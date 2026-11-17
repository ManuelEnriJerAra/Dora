/*
 * Fichero: MetadatosServidor.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Rellena los valores por defecto que necesita un servidor guardado (id, configuración, estadísticas, vista previa
 * del mapa...) y mantiene el puerto guardado en sintonía con server.properties.
 * */

package controlador;

import modelo.Server;
import modelo.ServerConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

import static controlador.PuertosServidor.parsePort;

final class MetadatosServidor {
    private MetadatosServidor() {
    }

    static boolean asegurarPersistentes(Server server) {
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
        if (sincronizarPuertoDesdeProperties(server)) {
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
        if (server.getEstadisticasVentanaRecienteSegundos() == null
                || server.getEstadisticasVentanaRecienteSegundos() <= 0
                || server.getEstadisticasVentanaRecienteSegundos() > Server.VENTANA_RECIENTE_MAXIMA_SEGUNDOS) {
            // Las versiones anteriores guardaban meses a 1 s; se reduce a la ventana por defecto.
            server.setEstadisticasVentanaRecienteSegundos(Server.VENTANA_RECIENTE_POR_DEFECTO_SEGUNDOS);
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

    static boolean sincronizarPuertoDesdeProperties(Server server) {
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
}
