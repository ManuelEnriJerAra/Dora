/*
 * Fichero: PuertosServidor.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Utilidades para comprobar y elegir el puerto con el que arranca un servidor.
 * */

package controlador;

import modelo.Server;
import modelo.ServerConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

final class PuertosServidor {
    static final int PUERTO_MINECRAFT_DEFECTO = 25565;
    static final int PUERTO_MAXIMO = 65535;

    private PuertosServidor() {
    }

    static boolean isPortAvailableForStart(String bindHost, int port, Set<Integer> reservados) {
        return !reservados.contains(port) && isPortAvailable(bindHost, port);
    }

    static int puertoConfigurado(ServerConfig serverConfig) {
        if (serverConfig == null || !isValidPortNumber(serverConfig.getPuerto())) {
            return PUERTO_MINECRAFT_DEFECTO;
        }
        return serverConfig.getPuerto();
    }

    static String resolveServerBindHost(Server server) {
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

    static Integer buscarSiguientePuertoDisponible(String bindHost, int desde) {
        for(int p = Math.max(1, desde); p <= PUERTO_MAXIMO; p++){
            if(isPortAvailable(bindHost, p)){
                return p;
            }
        }
        return null;
    }

    // comprobamos si hay libre un puerto
    static boolean isPortAvailable(String bindHost, int port){
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

    static Integer parsePort(String text) {
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
}
