/*
 * Fichero: Main.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Es la clase principal que muestra las ventanas. Contiene la configuración global del programa.
 * */

package controlador;

import modelo.DoraConfig;
import vista.NoServerFrame;
import vista.AppTheme;
import vista.VentanaPrincipal;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

import com.formdev.flatlaf.util.SystemInfo;

public class Main {

    public static NoServerFrame noServerFrame;
    public static VentanaPrincipal ventanaPrincipal;
    private static ApplicationInstanceLock instanceLock;

    public static final int DEFAULT_ARC = 20;

    public static void aplicarPreferenciasUI() {
        UIManager.put("defaultFont", new Font("SegoeUI", Font.PLAIN, 12));
        UIManager.put("Button.arc", DEFAULT_ARC); // radio de los botones
        UIManager.put("Component.arc", DEFAULT_ARC); // radio de los componentes (RoundBorder)
        UIManager.put("ProgressBar.arc", DEFAULT_ARC);
        UIManager.put("TitlePane.background", AppTheme.getBackground());
        UIManager.put("TitlePane.inactiveBackground", AppTheme.getBackground());
        UIManager.put("TitlePane.foreground", AppTheme.getForeground());
        UIManager.put("TitlePane.inactiveForeground", AppTheme.getMutedForeground());
        UIManager.put("TitlePane.borderColor", AppTheme.getTransparentColor());
        UIManager.put("TitlePane.embeddedForeground", AppTheme.getForeground());
        UIManager.put("TitlePane.unifiedBackground", false);
        vista.TextosSwingEspanol.aplicar();
        aplicarPreferenciasTooltips();
    }

    private static void aplicarPreferenciasTooltips() {
        ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
        toolTipManager.setInitialDelay(450);
        toolTipManager.setReshowDelay(120);
        toolTipManager.setDismissDelay(10_000);
    }

    public static void main(String[] args){
        AppErrorReporter.installGlobalHandler();
        if (!adquirirBloqueoInstancia()) {
            return;
        }

        SwingUtilities.invokeLater(()->{
            aplicarTemaInicial();
            if (SystemInfo.isLinux) {
                JFrame.setDefaultLookAndFeelDecorated(true);
                JDialog.setDefaultLookAndFeelDecorated(true);
            }
            System.setProperty("flatlaf.uiScale", "1");
            System.setProperty("flatlaf.useWindowDecorations",  "true");
            System.setProperty("flatlaf.MenuBarEmbedded", "true");
            aplicarPreferenciasUI();

            GestorServidores gestorServidores = new GestorServidores();
            gestorServidores.iniciarAutomatizaciones();
            Runtime.getRuntime().addShutdownHook(new Thread(
                    gestorServidores::detenerAutomatizaciones,
                    "dora-automation-stop"
            ));
            if(gestorServidores.getListaServidores().isEmpty()){
                noServerFrame = new NoServerFrame(gestorServidores);
                noServerFrame.setVisible(true);
                gestorServidores.mostrarAvisoArranqueSiProcede(noServerFrame);
            }
            else{
                ventanaPrincipal = new VentanaPrincipal(gestorServidores);
                ventanaPrincipal.setVisible(true);
                gestorServidores.mostrarAvisoArranqueSiProcede(ventanaPrincipal);
            }
        });
    }

    private static boolean adquirirBloqueoInstancia() {
        try {
            instanceLock = ApplicationInstanceLock.tryAcquire();
            if (instanceLock == null) {
                if (!ApplicationInstanceLock.requestExistingInstanceFocus()) {
                    mostrarAvisoInstanciaEnEjecucion();
                }
                return false;
            }
            instanceLock.startHandoffServer(Main::enfocarVentanaAplicacion);
            Runtime.getRuntime().addShutdownHook(new Thread(Main::liberarBloqueoInstancia, "dora-lock-release"));
            return true;
        } catch (IOException e) {
            System.err.println("No se pudo preparar el bloqueo de instancia: " + e.getMessage());
            mostrarAvisoBloqueoNoDisponible();
            return false;
        }
    }

    private static void liberarBloqueoInstancia() {
        if (instanceLock == null) {
            return;
        }
        try {
            instanceLock.close();
        } catch (IOException e) {
            System.err.println("No se pudo liberar el bloqueo de instancia: " + e.getMessage());
        }
    }

    private static void mostrarAvisoInstanciaEnEjecucion() {
        mostrarAvisoArranque("Dora ya está en ejecución.");
    }

    private static void mostrarAvisoBloqueoNoDisponible() {
        mostrarAvisoArranque("No se pudo comprobar si Dora ya está en ejecución.");
    }

    private static void mostrarAvisoArranque(String mensaje) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println(mensaje);
            return;
        }
        JOptionPane.showMessageDialog(
                null,
                mensaje,
                "Dora",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private static void enfocarVentanaAplicacion() {
        SwingUtilities.invokeLater(() -> {
            Window target = buscarVentanaAplicacion();
            if (target == null || !target.isDisplayable()) {
                return;
            }
            target.setVisible(true);
            if (target instanceof Frame frame) {
                frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED);
            }
            target.toFront();
            target.requestFocus();
        });
    }

    private static Window buscarVentanaAplicacion() {
        if (ventanaPrincipal != null && ventanaPrincipal.isDisplayable()) {
            return ventanaPrincipal;
        }
        if (noServerFrame != null && noServerFrame.isDisplayable()) {
            return noServerFrame;
        }
        for (Window window : Window.getWindows()) {
            if ((window instanceof VentanaPrincipal || window instanceof NoServerFrame) && window.isDisplayable()) {
                return window;
            }
        }
        return null;
    }

    private static void aplicarTemaInicial() {
        DoraConfig config = GestorConfiguracion.cargarConfiguracion();
        String temaClassName = config.getTemaClassName();
        if (temaClassName == null || temaClassName.isBlank()) {
            temaClassName = GestorConfiguracion.getTemaPorDefecto();
        }

        try {
            UIManager.setLookAndFeel(temaClassName);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            System.err.println("No se pudo aplicar el tema guardado, se usara el tema por defecto: " + e.getMessage());
            UIManager.put("ClassLoader", Main.class.getClassLoader());
            try {
                UIManager.setLookAndFeel(GestorConfiguracion.getTemaPorDefecto());
                GestorConfiguracion.guardarTema(GestorConfiguracion.getTemaPorDefecto());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
                throw new RuntimeException("No se pudo aplicar el tema por defecto.", ex);
            }
        }
    }
}
