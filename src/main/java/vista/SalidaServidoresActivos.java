package vista;

import controlador.GestorServidores;
import modelo.Server;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cierra la aplicación esperando a que los servidores activos terminen de guardar.
 *
 * <p>Antes se forzaba el cierre a los 7 segundos, lo que podía cortar el guardado del mundo en servidores
 * grandes o con mods. Ahora se espera y, si algún servidor sigue en marcha, se pregunta al usuario si quiere
 * seguir esperando, forzar el cierre o cancelar la salida.</p>
 */
final class SalidaServidoresActivos {

    static final Duration ESPERA_POR_RONDA = Duration.ofSeconds(60);

    private static final int OPCION_ESPERAR = 0;
    private static final int OPCION_FORZAR = 1;
    private static final int OPCION_CANCELAR = 2;

    private SalidaServidoresActivos() {
    }

    /**
     * Debe llamarse en el EDT después de que el usuario confirme la salida.
     *
     * @param ventana ventana que se bloquea mientras se espera.
     * @param salir   se ejecuta en el EDT cuando se puede salir.
     */
    static void detenerYSalir(JFrame ventana, GestorServidores gestorServidores, Runnable salir) {
        ventana.setEnabled(false);
        ventana.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        Thread hilo = new Thread(() -> {
            boolean salirAlFinal = true;
            try {
                List<Server> pendientes = gestorServidores.pedirParadaServidoresActivosParaSalir();
                while (true) {
                    pendientes = gestorServidores.esperarParadaServidores(pendientes, ESPERA_POR_RONDA);
                    if (pendientes.isEmpty()) {
                        break;
                    }
                    int opcion = preguntar(ventana, pendientes);
                    if (opcion == OPCION_FORZAR) {
                        gestorServidores.forzarPararServidores(pendientes);
                        break;
                    }
                    if (opcion == OPCION_CANCELAR) {
                        salirAlFinal = false;
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                salirAlFinal = false;
            } finally {
                boolean salirFinal = salirAlFinal;
                SwingUtilities.invokeLater(() -> {
                    if (salirFinal) {
                        salir.run();
                    } else {
                        ventana.setCursor(Cursor.getDefaultCursor());
                        ventana.setEnabled(true);
                    }
                });
            }
        }, "shutdown-servidores");
        hilo.setDaemon(false);
        hilo.start();
    }

    private static int preguntar(JFrame ventana, List<Server> pendientes) throws InterruptedException {
        StringBuilder sb = new StringBuilder();
        sb.append("Estos servidores todavía se están deteniendo\n");
        sb.append("(puede que estén guardando el mundo):\n\n");
        for (Server s : pendientes) {
            String nombre = (s.getDisplayName() == null || s.getDisplayName().isBlank()) ? s.getId() : s.getDisplayName();
            sb.append("- ").append(nombre).append("\n");
        }
        sb.append("\nForzar el cierre puede corromper el mundo o perder cambios.");

        Object[] opciones = {"Seguir esperando", "Forzar cierre y salir", "Cancelar salida"};
        AtomicInteger respuesta = new AtomicInteger(OPCION_ESPERAR);
        try {
            SwingUtilities.invokeAndWait(() -> {
                ventana.setCursor(Cursor.getDefaultCursor());
                int res = JOptionPane.showOptionDialog(
                        ventana,
                        sb.toString(),
                        "Cerrando servidores",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        opciones,
                        opciones[0]
                );
                // Cerrar el diálogo con la X equivale a seguir esperando.
                respuesta.set(res < 0 ? OPCION_ESPERAR : res);
                ventana.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            });
        } catch (InvocationTargetException e) {
            return OPCION_ESPERAR;
        }
        return respuesta.get();
    }
}
