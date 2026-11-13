/*
 * Fichero: ArranqueServidor.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Arranca el proceso de un servidor: avisa si el Java del sistema no encaja con su versión, elige un puerto libre
 * (preguntando al usuario si el suyo está ocupado), lanza el proceso, empieza a leer su consola y, cuando termina,
 * limpia el estado y lo vuelve a arrancar si había un reinicio pendiente.
 * */

package controlador;

import controlador.platform.JavaRuntimeCompatibility;
import controlador.platform.ServerPlatformAdapter;
import modelo.Server;
import modelo.ServerConfig;
import modelo.extensions.ServerPlatform;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static controlador.PuertosServidor.PUERTO_MAXIMO;
import static controlador.PuertosServidor.PUERTO_MINECRAFT_DEFECTO;
import static controlador.PuertosServidor.isPortAvailableForStart;
import static controlador.PuertosServidor.puertoConfigurado;
import static controlador.PuertosServidor.resolveServerBindHost;

final class ArranqueServidor {
    private final GestorServidores gestor;

    ArranqueServidor(GestorServidores gestor) {
        this.gestor = gestor;
    }

    // Cerrojo de arranque: evita dobles arranques y que dos servidores elijan el mismo puerto.
    // No se mantiene mientras se muestra un diálogo: si un hilo de fondo (automatizaciones,
    // reinicio) lo tuviera esperando al EDT y el EDT intentara arrancar otro servidor, la app se congelaría.
    private final Object inicioLock = new Object();

    private record SolicitudPuertoInicio(String bindHost,
                                         int puertoOcupado,
                                         boolean puertoDefectoDisponible,
                                         int puertoRecomendado) {
    }

    void iniciarServidor(Server server) throws IOException {
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
    private final Set<String> avisoJavaAceptado = ConcurrentHashMap.newKeySet();

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

        ServerPlatformAdapter adapter = gestor.resolverAdaptador(server);
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
            gestor.guardarServidor(server);
            gestor.notificarConfiguracionServidor(server);
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
            gestor.notificarEstadoServidor(server);

            // comienzo a leer la consola
            startLogReader(server);
            gestor.notificarEstadoServidor(server);

            // detecto cuando el proceso finaliza
            proceso.onExit().thenRun(()->{
               try {
                   if(server.getServerProcess() == proceso){
                       server.setServerProcess(null);
                   }
                   server.setIniciando(false);
                   server.setLogReaderIniciado(false);
                   server.appendConsoleLinea("[INFO] El servidor se ha detenido.");
                   gestor.notificarEstadoServidor(server);
                   // si el usuario ha pedido un reinicio y hemos parado el servidor entonces lo iniciamos de nuevo
                   if(server.getRestartPending()){
                       server.setRestartPending(false);
                       try {
                           gestor.iniciarServidor(server);
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

    private void startLogReader(Server server){
        if (server.getLogReaderIniciado()) return;
        if (server.getServerProcess() == null) return;

        server.setLogReaderIniciado(true);

        Thread lector = new Thread(
            new ServerLogReader(
                    server,
                    server.getServerProcess().getInputStream(),
                    () -> SwingUtilities.invokeLater(() -> gestor.notificarEstadoServidor(server))
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
        for (Server otro : gestor.getServidoresActivos()) {
            if (otro == server || otro.getServerConfig() == null) continue;
            puertos.add(otro.getServerConfig().getPuerto());
        }
        return puertos;
    }
}
