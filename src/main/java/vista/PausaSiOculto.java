package vista;

import javax.swing.JComponent;
import java.awt.event.HierarchyEvent;

/**
 * Avisa cuando un componente empieza o deja de verse en pantalla.
 *
 * <p>Las páginas de la ventana principal viven en un CardLayout: al cambiar de página la anterior se
 * oculta pero sigue montada, así que addNotify/removeNotify no se llaman. Sin esto, los temporizadores y
 * la consola de una página oculta seguían trabajando en el hilo de la interfaz.</p>
 */
final class PausaSiOculto {
    private PausaSiOculto() {
    }

    static void instalar(JComponent componente, Runnable alMostrar, Runnable alOcultar) {
        componente.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }
            if (componente.isShowing()) {
                if (alMostrar != null) alMostrar.run();
            } else if (alOcultar != null) {
                alOcultar.run();
            }
        });
    }
}
