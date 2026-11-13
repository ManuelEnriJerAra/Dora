/*
 * Fichero: ErroresDescarga.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Convierte los errores de descarga e instalación de servidores en mensajes legibles para el usuario.
 * */

package controlador;

import java.net.SocketTimeoutException;

final class ErroresDescarga {
    private ErroresDescarga() {
    }

    static String mensaje(Throwable throwable) {
        if (hasCause(throwable, SocketTimeoutException.class)) {
            return "La descarga ha tardado demasiado. Comprueba tu conexión e inténtalo de nuevo.";
        }
        Throwable root = rootCause(throwable);
        String message = root == null ? null : root.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable == null ? null : throwable.getMessage();
        }
        return message == null || message.isBlank() ? "Error desconocido." : message;
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        Throwable previous = null;
        while (current != null && current != previous) {
            previous = current;
            current = current.getCause();
        }
        return previous;
    }
}
