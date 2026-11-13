/*
 * Fichero: PanelControlServidor.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * ----------------------
 * */

package vista;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.Timer;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.components.FlatButton;

import controlador.GestorServidores;
import controlador.AppErrorReporter;
import modelo.Server;

public class PanelControlServidor extends JPanel {
    private static final int DEFAULT_SIDE_PX_X = 146;
    // Tiempo de espera tras pedir la parada antes de ofrecer el cierre forzado.
    static final long FORCE_STOP_DELAY_MS = 10_000L;
    private final PropertyChangeListener listenerEstadoServidor;
    private JPanel panelBotones;
    private JButton btnIniciarServidor;
    private JButton btnPararServidor;
    private JButton btnReiniciarServidor;
    private FlatButton btnForzarCierreServidor;
    private JPanel panelBotonesIniciado;
    private JPanel panelBotonesDeteniendo;
    private FlatButton btnDeteniendo;
    private final Timer temporizadorParada;

    PanelControlServidor(GestorServidores gestorServidores) {
        Color colorVerde = AppTheme.getSuccessColor();
        Color colorRojo = AppTheme.getDangerColor();
        Color colorAmarillo = AppTheme.getWarningColor();

        Server server = gestorServidores.getServidorSeleccionado();
        this.setLayout(new BorderLayout());
        this.setOpaque(false);
        this.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        panelBotones = new JPanel(new BorderLayout());
        panelBotones.setOpaque(false);
        btnIniciarServidor = new FlatButton();
        btnIniciarServidor.setText("Iniciar");
        btnIniciarServidor.putClientProperty("fullText", "Iniciar");
        btnIniciarServidor.setBackground(colorVerde);
        btnIniciarServidor.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnPararServidor = new FlatButton();
        btnPararServidor.setText("Parar");
        btnPararServidor.putClientProperty("fullText", "Parar");
        btnPararServidor.setBackground(colorRojo);
        btnPararServidor.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnReiniciarServidor = new FlatButton();
        btnReiniciarServidor.setText("Reiniciar");
        btnReiniciarServidor.putClientProperty("fullText", "Reiniciar");
        btnReiniciarServidor.setBackground(colorAmarillo);
        btnReiniciarServidor.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnForzarCierreServidor = new FlatButton();
        btnForzarCierreServidor.setText("Forzar cierre");
        btnForzarCierreServidor.putClientProperty("fullText", "Forzar cierre");
        btnForzarCierreServidor.setBackground(AppTheme.getDestructiveColor());
        btnForzarCierreServidor.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnDeteniendo = new FlatButton();
        btnDeteniendo.setText(textoParada("Deteniendo...", 0));
        btnDeteniendo.setBackground(colorRojo);
        btnDeteniendo.setFocusable(false);
        btnDeteniendo.setRolloverEnabled(false);
        btnDeteniendo.setToolTipText("Guardando el mundo y cerrando el servidor");

        FlatSVGIcon iconPlay = new FlatSVGIcon("doraicons/play.svg", 32, 32);
        FlatSVGIcon iconPause = new FlatSVGIcon("doraicons/stop.svg", 32, 32);
        FlatSVGIcon iconReset = new FlatSVGIcon("doraicons/reset.svg", 32, 32);

        configurarBotonAccion(btnIniciarServidor, iconPlay);
        configurarBotonAccion(btnPararServidor, iconPause);
        configurarBotonAccion(btnReiniciarServidor, iconReset);
        java.awt.Font fuenteBase = btnDeteniendo.getFont();
        AppTheme.applyLargeControlButtonStyle(btnDeteniendo, null);
        btnDeteniendo.setFont(fuenteBase.deriveFont(java.awt.Font.BOLD, fuenteBase.getSize2D() + 3f));
        AppTheme.applyLargeControlButtonStyle(btnForzarCierreServidor, null);
        btnForzarCierreServidor.setFont(fuenteBase.deriveFont(java.awt.Font.BOLD, fuenteBase.getSize2D() + 1f));

        panelBotonesIniciado = new JPanel(new GridBagLayout());
        panelBotonesIniciado.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        // Parar ocupa 3/4 del alto
        gbc.gridy = 0;
        gbc.weighty = 3.0;
        panelBotonesIniciado.add(btnPararServidor, gbc);
        // Reiniciar ocupa 1/4 del alto
        gbc.gridy = 1;
        gbc.weighty = 1.0;
        panelBotonesIniciado.add(btnReiniciarServidor, gbc);

        panelBotonesDeteniendo = new JPanel(new GridBagLayout());
        panelBotonesDeteniendo.setOpaque(false);
        GridBagConstraints gbcParada = new GridBagConstraints();
        gbcParada.gridx = 0;
        gbcParada.fill = GridBagConstraints.BOTH;
        gbcParada.weightx = 1.0;
        gbcParada.gridy = 0;
        gbcParada.weighty = 3.0;
        panelBotonesDeteniendo.add(btnDeteniendo, gbcParada);
        gbcParada.gridy = 1;
        gbcParada.weighty = 1.0;
        gbcParada.insets = new Insets(6, 0, 0, 0);
        panelBotonesDeteniendo.add(btnForzarCierreServidor, gbcParada);

        // Mientras se detiene, refresca el texto con los segundos y habilita el cierre forzado tras la espera.
        temporizadorParada = new Timer(1000, e -> actualizarEstadoParada(server));
        temporizadorParada.setRepeats(true);

        btnIniciarServidor.addActionListener((ActionEvent e) -> {
            try {
                gestorServidores.iniciarServidor(server);
            } catch (IOException | RuntimeException ex) {
                AppErrorReporter.reportSwing("No se ha podido iniciar el servidor.", ex);
                if (server != null) {
                    server.appendConsoleLinea("[ERROR] No se ha podido iniciar el servidor: " + ex.getMessage());
                }
            }
        });
        btnPararServidor.addActionListener((ActionEvent e) -> {
            gestorServidores.safePararServidor(server);
        });
        btnReiniciarServidor.addActionListener((ActionEvent e) -> {
            server.setRestartPending(true);
            gestorServidores.safePararServidor(server);
        });
        btnForzarCierreServidor.addActionListener((ActionEvent e) -> {
            String nombre = server == null || server.getDisplayName() == null ? "el servidor" : "'" + server.getDisplayName() + "'";
            int opcion = JOptionPane.showConfirmDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "El servidor " + nombre + " no ha terminado de cerrarse.\n"
                            + "Forzar el cierre puede corromper el mundo o perder los últimos cambios.\n\n"
                            + "¿Quieres forzar el cierre?",
                    "Forzar cierre",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (opcion != JOptionPane.YES_OPTION) {
                return;
            }
            btnForzarCierreServidor.setEnabled(false);
            server.setRestartPending(false);
            // El cierre forzado espera a que muera el árbol de procesos: nunca en el EDT.
            Thread hilo = new Thread(() -> gestorServidores.forzarPararServidor(server), "force-stop-" + server.getId());
            hilo.setDaemon(true);
            hilo.start();
        });

        this.listenerEstadoServidor = evt -> {
            if(!"estadoServidor".equals(evt.getPropertyName())) return;
            Server serverEvento = (Server) evt.getNewValue();
            if(serverEvento == null || !serverEvento.equals(server)) return;
            SwingUtilities.invokeLater(()->{
                actualizarBotonesSegunEstado(server);
            });
        };

        gestorServidores.addPropertyChangeListener("estadoServidor", listenerEstadoServidor);
        this.add(panelBotones, BorderLayout.CENTER);

        // Sincroniza el estado inicial (si el servidor ya estaba iniciado, no llega ningún evento)
        actualizarBotonesSegunEstado(server);

        // Ellipsize del texto cuando el panel sea pequeño
        panelBotones.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    ellipsizeButtonText(btnIniciarServidor);
                    ellipsizeButtonText(btnPararServidor);
                    ellipsizeButtonText(btnReiniciarServidor);
                });
            }
        });

        // Evita fugas de listeners al cambiar de servidor/panel (solo cuando el componente deja de ser displayable)
        this.addHierarchyListener(e -> {
            if((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
            if(!isDisplayable()){
                gestorServidores.removePropertyChangeListener("estadoServidor", listenerEstadoServidor);
                temporizadorParada.stop();
            }
        });

        this.setVisible(true);
    }

    private void actualizarBotonesSegunEstado(Server server){
        panelBotones.removeAll();
        boolean vivo = server.getServerProcess() != null && server.getServerProcess().isAlive();
        if(vivo && Boolean.TRUE.equals(server.getDeteniendo())){
            // se ha pedido la parada y el servidor sigue guardando
            panelBotones.add(panelBotonesDeteniendo, BorderLayout.CENTER);
            actualizarEstadoParada(server);
            if(!temporizadorParada.isRunning()) temporizadorParada.start();
        }
        else if(vivo){
            // el servidor está vivo
            temporizadorParada.stop();
            panelBotones.add(panelBotonesIniciado, BorderLayout.CENTER);
        }
        else{
            // el servidor no está vivo
            temporizadorParada.stop();
            panelBotones.add(btnIniciarServidor, BorderLayout.CENTER);
        }
        panelBotones.revalidate();
        panelBotones.repaint();
        revalidate();
        repaint();

        // Recalcular ellipsize tras cambiar el layout y forzar un repintado completo,
        // porque los paneles transparentes/redondeados vecinos pueden dejar artefactos
        // hasta el siguiente repaint global de la ventana.
        SwingUtilities.invokeLater(() -> {
            ellipsizeButtonText(btnIniciarServidor);
            ellipsizeButtonText(btnPararServidor);
            ellipsizeButtonText(btnReiniciarServidor);
            ellipsizeButtonText(btnForzarCierreServidor);

            JRootPane root = SwingUtilities.getRootPane(PanelControlServidor.this);
            if(root != null){
                RepaintManager.currentManager(root).markCompletelyDirty(root);
                root.revalidate();
                root.repaint();
            }
        });
    }

    private void actualizarEstadoParada(Server server){
        if(server == null || !Boolean.TRUE.equals(server.getDeteniendo())){
            temporizadorParada.stop();
            return;
        }
        long transcurrido = Math.max(0L, System.currentTimeMillis() - server.getParadaSolicitadaMillis());
        long segundos = transcurrido / 1000L;
        String base = Boolean.TRUE.equals(server.getRestartPending()) ? "Reiniciando..." : "Deteniendo...";
        btnDeteniendo.setText(textoParada(base, segundos));
        boolean puedeForzar = permiteForzarCierre(transcurrido);
        if(btnForzarCierreServidor.isVisible() != puedeForzar){
            btnForzarCierreServidor.setVisible(puedeForzar);
            panelBotonesDeteniendo.revalidate();
            SwingUtilities.invokeLater(() -> ellipsizeButtonText(btnForzarCierreServidor));
        }
        if(puedeForzar && !btnForzarCierreServidor.isEnabled() && server.getServerProcess() != null){
            // se vuelve a habilitar si un intento anterior no consiguió cerrarlo
            btnForzarCierreServidor.setEnabled(true);
        }
    }

    // Dos líneas para que quepa en el ancho fijo del panel: el estado y los segundos transcurridos.
    static String textoParada(String base, long segundos){
        String detalle = segundos < 2 ? "Guardando mundo" : segundos + " s";
        return "<html><center>" + base + "<br><span style='font-size:90%;font-weight:normal'>" + detalle + "</span></center></html>";
    }

    static boolean permiteForzarCierre(long msDesdeParada){
        return msDesdeParada >= FORCE_STOP_DELAY_MS;
    }

    private void ellipsizeButtonText(AbstractButton button){
        if(button == null) return;
        Object fullObj = button.getClientProperty("fullText");
        String fullText = (fullObj instanceof String s) ? s : button.getText();
        if(fullText == null) fullText = "";
        button.setToolTipText(fullText);

        Insets ins = button.getInsets();
        int available = Math.max(0, button.getWidth() - ins.left - ins.right - 12);
        button.setText(TextEllipsizer.right(fullText, button.getFontMetrics(button.getFont()), available));
    }

@Override
public Dimension getPreferredSize() {
    return new Dimension(DEFAULT_SIDE_PX_X, 0);
}

@Override
public Dimension getMinimumSize() {
    return new Dimension(DEFAULT_SIDE_PX_X, 0);
}

@Override
public Dimension getMaximumSize() {
    return new Dimension(DEFAULT_SIDE_PX_X, Integer.MAX_VALUE);
}

    private void configurarBotonAccion(AbstractButton button, FlatSVGIcon flatIcon) {
        if (button == null) {
            return;
        }
        AppTheme.applyLargeControlButtonStyle(button, flatIcon);
        Color colorIcono = button.getForeground();
        flatIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> colorIcono));
    }

}
