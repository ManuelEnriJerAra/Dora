/*
 * Fichero: ParadaServidor.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Detiene servidores en marcha: parada segura con "stop", reinicio (parar y volver a arrancar al terminar),
 * cierre forzado de todo el árbol de procesos y la parada de todos los servidores al salir de Dora.
 * */

package controlador;

import modelo.Server;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ParadaServidor {
    private final GestorServidores gestor;

    ParadaServidor(GestorServidores gestor) {
        this.gestor = gestor;
    }

    // Pedir una parada segura ("stop") a todos los servidores activos para salir del programa.
    // Devuelve los servidores a los que se ha pedido la parada.
    List<Server> pedirParadaServidoresActivosParaSalir() {
        List<Server> activos = gestor.getServidoresActivos();
        for (Server server : activos) {
            try {
                gestor.safePararServidor(server);
            } catch (RuntimeException ignored) {
            }
        }
        return activos;
    }

    // Esperar hasta "espera" a que los servidores indicados terminen (incluidos procesos hijos).
    // Devuelve los que siguen vivos. No fuerza ningún cierre: guardar el mundo puede tardar.
    List<Server> esperarParadaServidores(List<Server> servidores, Duration espera) throws InterruptedException {
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
    void forzarPararServidores(List<Server> servidores) {
        for (Server server : servidores) {
            if (server == null || !ProcessTreeTerminator.isTreeAlive(server.getServerProcess())) continue;
            try {
                gestor.forzarPararServidor(server);
            } catch (RuntimeException ignored) {
            }
        }
    }

    // Eliminar el proceso de servidor, a evitar, puede provocar corrupción de mundos
    void forzarPararServidor(Server server){
        Process proceso = server.getServerProcess();

        if(!ProcessTreeTerminator.isTreeAlive(proceso)) {
            server.appendConsoleLinea("[INFO] El servidor no está en ejecución");
            server.setServerProcess(null);
            server.setIniciando(false);
            gestor.notificarEstadoServidor(server);
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
            gestor.notificarEstadoServidor(server);
        }
    }

    void safePararServidor(Server server){
        gestor.mandarComando(server, "stop");
    }

    void reiniciarServidor(Server server) throws IOException {
        if (server == null) {
            return;
        }
        Process proceso = server.getServerProcess();
        if (proceso != null && proceso.isAlive()) {
            server.setRestartPending(true);
            gestor.safePararServidor(server);
            gestor.notificarEstadoServidor(server);
            return;
        }
        gestor.iniciarServidor(server);
    }
}
