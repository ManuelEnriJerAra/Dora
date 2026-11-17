/*
 * Fichero: DialogoTareaEnCurso.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Ejecuta una tarea larga en segundo plano mostrando un diálogo modal de espera (o directamente si no hay interfaz).
 * El diálogo puede mostrar el avance real (porcentaje y detalle) y, si la tarea lo admite, un botón Cancelar.
 * */

package controlador;

import vista.AppTheme;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DialogoTareaEnCurso {
    /** Avance de una tarea en curso. Se puede llamar desde cualquier hilo. */
    public interface Progreso {
        /**
         * @param fraccion entre 0 y 1, o negativo si no se conoce el total
         * @param detalle texto corto bajo la descripción (archivo, MB...), o null para dejar el actual
         */
        void actualizar(double fraccion, String detalle);

        /** true cuando el usuario ha pulsado Cancelar; la tarea debe parar y lanzar {@link InterruptedIOException}. */
        boolean cancelado();
    }

    @FunctionalInterface
    public interface TareaConProgreso<T> {
        T ejecutar(Progreso progreso) throws Exception;
    }

    private static final Progreso SIN_PROGRESO = new Progreso() {
        @Override
        public void actualizar(double fraccion, String detalle) {
        }

        @Override
        public boolean cancelado() {
            return false;
        }
    };

    private static final ThreadLocal<Progreso> PROGRESO_ACTUAL = new ThreadLocal<>();

    private DialogoTareaEnCurso() {
    }

    static <T> T ejecutar(String titulo, String descripcion, Callable<T> task) throws IOException {
        return ejecutarConProgreso(titulo, descripcion, false, 0, progreso -> task.call());
    }

    /**
     * Ejecuta la tarea fuera del hilo de la interfaz con un diálogo modal de progreso.
     *
     * @param cancelable muestra un botón Cancelar; la tarea debe consultar {@link Progreso#cancelado()}
     * @param retrasoMs  espera este tiempo antes de mostrar el diálogo, para no hacerlo parpadear en tareas rápidas
     * @throws InterruptedIOException si el usuario cancela
     */
    public static <T> T ejecutarConProgreso(String titulo,
                                            String descripcion,
                                            boolean cancelable,
                                            long retrasoMs,
                                            TareaConProgreso<T> task) throws IOException {
        if (GraphicsEnvironment.isHeadless() || !SwingUtilities.isEventDispatchThread()) {
            try {
                return conProgresoActual(SIN_PROGRESO, () -> task.ejecutar(SIN_PROGRESO));
            } catch (Exception e) {
                throw comoIOException(e);
            }
        }

        Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        final JDialog dialog = new JDialog(owner, titulo, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel contenido = new JPanel(new BorderLayout(10, 10));
        contenido.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel label = new JLabel(descripcion == null ? "Procesando..." : descripcion);
        JLabel detalle = new JLabel("Por favor espera. La ventana se cerrará al terminar.");
        detalle.setForeground(AppTheme.getMutedForeground());
        JProgressBar barra = AppTheme.createLoadingProgressBar(true);
        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 4));
        textos.setOpaque(false);
        textos.add(label);
        textos.add(detalle);
        contenido.add(textos, BorderLayout.CENTER);

        JPanel sur = new JPanel(new BorderLayout(10, 0));
        sur.setOpaque(false);
        sur.add(barra, BorderLayout.CENTER);
        AtomicBoolean cancelado = new AtomicBoolean(false);
        if (cancelable) {
            JButton cancelar = new JButton("Cancelar");
            cancelar.addActionListener(e -> {
                cancelado.set(true);
                cancelar.setEnabled(false);
                detalle.setText("Cancelando...");
            });
            sur.add(cancelar, BorderLayout.EAST);
        }
        contenido.add(sur, BorderLayout.SOUTH);
        dialog.setContentPane(contenido);
        dialog.setSize(480, 140);
        dialog.setLocationRelativeTo(owner);

        Progreso progreso = new Progreso() {
            private volatile long ultimoAvisoNanos;

            @Override
            public void actualizar(double fraccion, String textoDetalle) {
                long ahora = System.nanoTime();
                // Como mucho unas 20 actualizaciones por segundo, para no saturar el hilo de la interfaz.
                if (fraccion < 1d && ahora - ultimoAvisoNanos < 50_000_000L) return;
                ultimoAvisoNanos = ahora;
                SwingUtilities.invokeLater(() -> {
                    if (fraccion < 0d) {
                        barra.setIndeterminate(true);
                    } else {
                        barra.setIndeterminate(false);
                        barra.setValue((int) Math.round(Math.min(1d, fraccion) * 100d));
                    }
                    if (textoDetalle != null && !cancelado.get()) {
                        detalle.setText(textoDetalle);
                    }
                });
            }

            @Override
            public boolean cancelado() {
                return cancelado.get();
            }
        };

        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return conProgresoActual(progreso, () -> task.ejecutar(progreso));
            }

            @Override
            protected void done() {
                dialog.dispose();
            }
        };

        worker.execute();
        if (retrasoMs > 0) {
            try {
                return worker.get(retrasoMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                // Sigue en curso: ahora sí se muestra el diálogo.
            } catch (Exception e) {
                throw comoIOException(e);
            }
        }
        if (!worker.isDone()) {
            dialog.setVisible(true);
        }

        try {
            return worker.get();
        } catch (Exception e) {
            throw comoIOException(e);
        }
    }

    /**
     * Progreso de la tarea que se está ejecutando en este hilo, o uno que no hace nada. Permite que el código
     * de descarga informe del avance sin tener que pasar el progreso por todas las capas.
     */
    public static Progreso progresoActual() {
        Progreso progreso = PROGRESO_ACTUAL.get();
        return progreso == null ? SIN_PROGRESO : progreso;
    }

    /** Listener de descarga que informa del avance al diálogo de la tarea en curso de este hilo. */
    public static MojangAPI.DownloadProgressListener listenerDescargaActual(String nombreArchivo) {
        Progreso progreso = progresoActual();
        String nombre = nombreArchivo == null || nombreArchivo.isBlank() ? "archivo" : nombreArchivo;
        return (leidos, total) -> progreso.actualizar(
                total > 0 ? leidos / (double) total : -1d,
                textoDescarga(nombre, leidos, total)
        );
    }

    static String textoDescarga(String nombre, long leidos, long total) {
        if (total > 0) {
            return String.format(Locale.ROOT, "Descargando %s: %.1f de %.1f MB", nombre, mb(leidos), mb(total));
        }
        return String.format(Locale.ROOT, "Descargando %s: %.1f MB", nombre, mb(leidos));
    }

    private static double mb(long bytes) {
        return bytes / (1024d * 1024d);
    }

    private static <T> T conProgresoActual(Progreso progreso, Callable<T> accion) throws Exception {
        Progreso anterior = PROGRESO_ACTUAL.get();
        PROGRESO_ACTUAL.set(progreso);
        try {
            return accion.call();
        } finally {
            if (anterior == null) {
                PROGRESO_ACTUAL.remove();
            } else {
                PROGRESO_ACTUAL.set(anterior);
            }
        }
    }

    private static IOException comoIOException(Exception e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        if (e instanceof IOException io && e.getCause() == null) {
            return io;
        }
        if (cause instanceof IOException ioException) {
            return ioException;
        }
        return new IOException(cause.getMessage(), cause);
    }
}
