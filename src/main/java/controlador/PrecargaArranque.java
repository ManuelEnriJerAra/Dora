/*
 * Fichero: PrecargaArranque.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Adelanta en hilos aparte el trabajo de arranque que no toca la interfaz (leer la configuración, cargar Jackson
 * y las clases del tema y de las ventanas) para que el hilo de la interfaz no tenga que hacerlo todo en serie.
 * */

package controlador;

import modelo.DoraConfig;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

final class PrecargaArranque {
    // Clases pesadas que se cargan al abrir la ventana principal. Solo se cargan (sin inicializar), lo que es
    // seguro fuera del hilo de la interfaz.
    private static final List<String> CLASES_INTERFAZ = List.of(
            "com.formdev.flatlaf.FlatLaf",
            "com.formdev.flatlaf.FlatLightLaf",
            "com.formdev.flatlaf.intellijthemes.FlatArcIJTheme",
            "com.formdev.flatlaf.extras.FlatSVGIcon",
            "com.formdev.flatlaf.ui.FlatButtonUI",
            "com.formdev.flatlaf.ui.FlatLabelUI",
            "com.formdev.flatlaf.ui.FlatComboBoxUI",
            "com.formdev.flatlaf.ui.FlatScrollBarUI",
            "com.formdev.flatlaf.ui.FlatTextPaneUI",
            "vista.VentanaPrincipal",
            "vista.VentanaPrincipalNavigationBuilder",
            "vista.VentanaPrincipalRightContentBuilder",
            "vista.PanelServidores",
            "vista.PanelTotalServidor",
            "vista.PanelControlServidor",
            "vista.PanelConsola",
            "vista.PanelIndicadoresRecursos",
            "vista.RoundedBackgroundPanel",
            "vista.AppTheme",
            "vista.SvgIconFactory",
            "vista.PanelMundo",
            "vista.PanelEstadisticas",
            "vista.PanelJugadores"
    );

    private static volatile CompletableFuture<DoraConfig> configuracion;

    private PrecargaArranque() {
    }

    static void iniciar() {
        configuracion = CompletableFuture.supplyAsync(GestorConfiguracion::cargarConfiguracion, PrecargaArranque::enHilo);
        enHilo(() -> {
            cargarFuentes();
            cargarClasesInterfaz();
        });
    }

    /** La configuración leída en segundo plano, o se lee ahora si no se lanzó la precarga o falló. */
    static DoraConfig configuracion() {
        CompletableFuture<DoraConfig> futura = configuracion;
        configuracion = null;
        if (futura != null) {
            try {
                DoraConfig config = futura.get();
                if (config != null) return config;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException ignored) {
                // se vuelve a intentar aquí para que el error se gestione como siempre
            }
        }
        return GestorConfiguracion.cargarConfiguracion();
    }

    // La primera vez que se usa una fuente (sobre todo la negrita) Windows carga su archivo, unos 0,5 s que
    // antes caían en el hilo de la interfaz al crear los botones grandes de Home.
    private static void cargarFuentes() {
        FontRenderContext contexto = new FontRenderContext(null, true, true);
        for (String familia : List.of("SegoeUI", "Segoe UI", Font.DIALOG)) {
            for (int estilo : new int[]{Font.PLAIN, Font.BOLD}) {
                try {
                    new Font(familia, estilo, 14).getStringBounds("Iniciar servidor 0123456789 áéíóú", contexto);
                } catch (RuntimeException ignored) {
                    // solo es una optimización
                }
            }
        }
    }

    private static void cargarClasesInterfaz() {
        ClassLoader loader = PrecargaArranque.class.getClassLoader();
        for (String clase : CLASES_INTERFAZ) {
            try {
                Class.forName(clase, false, loader);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // solo es una optimización
            }
        }
    }

    private static void enHilo(Runnable tarea) {
        Thread hilo = new Thread(tarea, "dora-precarga");
        hilo.setDaemon(true);
        hilo.start();
    }
}
