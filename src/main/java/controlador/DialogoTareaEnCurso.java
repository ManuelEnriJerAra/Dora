/*
 * Fichero: DialogoTareaEnCurso.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Ejecuta una tarea larga en segundo plano mostrando un diálogo modal de espera (o directamente si no hay interfaz).
 * */

package controlador;

import vista.AppTheme;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.concurrent.Callable;

final class DialogoTareaEnCurso {
    private DialogoTareaEnCurso() {
    }

    static <T> T ejecutar(String titulo, String descripcion, Callable<T> task) throws IOException {
        if (GraphicsEnvironment.isHeadless()) {
            try {
                return task.call();
            } catch (Exception e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof IOException ioException) {
                    throw ioException;
                }
                throw new IOException(cause.getMessage(), cause);
            }
        }

        final JDialog dialog = new JDialog((Frame) null, titulo, true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel contenido = new JPanel(new BorderLayout(10, 10));
        contenido.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel label = new JLabel(descripcion == null ? "Procesando..." : descripcion);
        JLabel detalle = new JLabel("Por favor espera. La ventana se cerrará al terminar.");
        detalle.setForeground(AppTheme.getMutedForeground());
        JProgressBar progreso = AppTheme.createLoadingProgressBar(true);
        contenido.add(label, BorderLayout.NORTH);
        contenido.add(detalle, BorderLayout.CENTER);
        contenido.add(progreso, BorderLayout.SOUTH);
        dialog.setContentPane(contenido);
        dialog.setSize(460, 120);
        dialog.setLocationRelativeTo(null);

        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                dialog.dispose();
            }
        };

        worker.execute();
        dialog.setVisible(true);

        try {
            return worker.get();
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException(cause.getMessage(), cause);
        }
    }
}
