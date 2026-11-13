/*
 * Fichero: DialogoPuertoOcupado.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Diálogo que se muestra al iniciar un servidor cuyo puerto guardado está ocupado, para elegir otro puerto.
 * */

package controlador;

import modelo.Server;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static controlador.PuertosServidor.PUERTO_MAXIMO;
import static controlador.PuertosServidor.PUERTO_MINECRAFT_DEFECTO;
import static controlador.PuertosServidor.buscarSiguientePuertoDisponible;
import static controlador.PuertosServidor.isPortAvailable;
import static controlador.PuertosServidor.parsePort;

final class DialogoPuertoOcupado {
    private DialogoPuertoOcupado() {
    }

    static Integer solicitar(Server server,
                                               String bindHost,
                                               int puertoOcupado,
                                               boolean puertoDefectoDisponible,
                                               int puertoRecomendado) {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }

        AtomicReference<Integer> resultado = new AtomicReference<>();
        Runnable dialogTask = () -> resultado.set(mostrarDialogoPuertoOcupado(
                server,
                bindHost,
                puertoOcupado,
                puertoDefectoDisponible,
                puertoRecomendado
        ));

        if (SwingUtilities.isEventDispatchThread()) {
            dialogTask.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(dialogTask);
            } catch (Exception e) {
                return null;
            }
        }
        return resultado.get();
    }

    private static Integer mostrarDialogoPuertoOcupado(Server server,
                                                String bindHost,
                                                int puertoOcupado,
                                                boolean puertoDefectoDisponible,
                                                int puertoRecomendadoInicial) {
        Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        JDialog dialog = new JDialog(owner, "Puerto del servidor ocupado", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 14, 16));

        String nombreServidor = server == null || server.getDisplayName() == null || server.getDisplayName().isBlank()
                ? "este servidor"
                : server.getDisplayName();
        JLabel message = new JLabel("<html><body style='width:560px'>No se ha podido usar el puerto guardado "
                + puertoOcupado + " para " + escapeHtml(nombreServidor)
                + ". Puedes cancelar el inicio o guardar otro puerto antes de arrancar.</body></html>");
        root.add(message, BorderLayout.NORTH);

        ButtonGroup group = new ButtonGroup();
        JRadioButton defaultRadio = new JRadioButton(
                puertoDefectoDisponible ? "Puerto por defecto (recomendado)" : "Puerto por defecto",
                puertoDefectoDisponible
        );
        JRadioButton recommendedRadio = new JRadioButton(
                puertoDefectoDisponible ? "Siguiente puerto disponible" : "Siguiente puerto disponible (recomendado)",
                !puertoDefectoDisponible
        );
        JRadioButton customRadio = new JRadioButton("Puerto personalizado");
        defaultRadio.setEnabled(puertoDefectoDisponible);
        group.add(defaultRadio);
        group.add(recommendedRadio);
        group.add(customRadio);

        JLabel defaultValueLabel = new JLabel(puertoDefectoDisponible ? String.valueOf(PUERTO_MINECRAFT_DEFECTO) : "No disponible");
        defaultValueLabel.setFont(defaultValueLabel.getFont().deriveFont(Font.BOLD, 22f));
        JLabel recommendedValueLabel = new JLabel(String.valueOf(puertoRecomendadoInicial));
        recommendedValueLabel.setFont(recommendedValueLabel.getFont().deriveFont(Font.BOLD, 22f));
        JTextField customPortField = new JTextField(String.valueOf(puertoRecomendadoInicial), 8);
        customPortField.setEnabled(false);
        JLabel statusLabel = new JLabel(" ");

        JPanel defaultPanel = new JPanel(new BorderLayout(0, 8));
        defaultPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 14));
        defaultPanel.add(defaultRadio, BorderLayout.NORTH);
        defaultPanel.add(new JLabel("Puerto 25565:"), BorderLayout.CENTER);
        defaultPanel.add(defaultValueLabel, BorderLayout.SOUTH);

        JPanel recommendedPanel = new JPanel(new BorderLayout(0, 8));
        recommendedPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 14));
        recommendedPanel.add(recommendedRadio, BorderLayout.NORTH);
        recommendedPanel.add(new JLabel("Puerto sugerido:"), BorderLayout.CENTER);
        recommendedPanel.add(recommendedValueLabel, BorderLayout.SOUTH);

        JPanel customPanel = new JPanel(new BorderLayout(0, 8));
        customPanel.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 10));
        customPanel.add(customRadio, BorderLayout.NORTH);
        customPanel.add(new JLabel("Elige un puerto entre 1 y " + PUERTO_MAXIMO + ":"), BorderLayout.CENTER);
        customPanel.add(customPortField, BorderLayout.SOUTH);

        JPanel optionsPanel = new JPanel(new GridLayout(1, 3, 0, 0));
        optionsPanel.add(defaultPanel);
        optionsPanel.add(recommendedPanel);
        optionsPanel.add(customPanel);
        Color dividerColor = UIManager.getColor("Component.borderColor");
        optionsPanel.setBorder(BorderFactory.createLineBorder(dividerColor == null ? Color.GRAY : dividerColor));
        root.add(optionsPanel, BorderLayout.CENTER);

        JButton cancelButton = new JButton("Cancelar inicio");
        JButton saveButton = new JButton("Guardar");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelButton);
        buttons.add(saveButton);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);

        AtomicReference<Integer> selectedPort = new AtomicReference<>();
        AtomicInteger recommendedPort = new AtomicInteger(puertoRecomendadoInicial);

        defaultRadio.addActionListener(e -> customPortField.setEnabled(false));
        recommendedRadio.addActionListener(e -> customPortField.setEnabled(false));
        customRadio.addActionListener(e -> {
            customPortField.setEnabled(true);
            customPortField.requestFocusInWindow();
            customPortField.selectAll();
        });

        cancelButton.addActionListener(e -> {
            selectedPort.set(null);
            dialog.dispose();
        });
        saveButton.addActionListener(e -> {
            if (defaultRadio.isSelected()) {
                if (isPortAvailable(bindHost, PUERTO_MINECRAFT_DEFECTO)) {
                    selectedPort.set(PUERTO_MINECRAFT_DEFECTO);
                    dialog.dispose();
                    return;
                }
                defaultRadio.setEnabled(false);
                recommendedRadio.setSelected(true);
                statusLabel.setText("El puerto por defecto ya no está disponible.");
                return;
            }
            if (recommendedRadio.isSelected()) {
                int candidate = recommendedPort.get();
                if (isPortAvailable(bindHost, candidate)) {
                    selectedPort.set(candidate);
                    dialog.dispose();
                    return;
                }
                Integer refreshed = buscarSiguientePuertoDisponible(bindHost, candidate + 1);
                if (refreshed == null) {
                    statusLabel.setText("No hay puertos disponibles.");
                    return;
                }
                recommendedPort.set(refreshed);
                recommendedValueLabel.setText(String.valueOf(refreshed));
                statusLabel.setText("El puerto sugerido se ha ocupado. Nueva sugerencia: " + refreshed + ".");
                return;
            }

            Integer customPort = parsePort(customPortField.getText());
            if (customPort == null) {
                statusLabel.setText("Introduce un puerto válido entre 1 y " + PUERTO_MAXIMO + ".");
                customRadio.setSelected(true);
                customPortField.setEnabled(true);
                customPortField.requestFocusInWindow();
                customPortField.selectAll();
                return;
            }
            if (!isPortAvailable(bindHost, customPort)) {
                statusLabel.setText("El puerto " + customPort + " ya está ocupado por otro proceso.");
                customRadio.setSelected(true);
                customPortField.setEnabled(true);
                customPortField.requestFocusInWindow();
                customPortField.selectAll();
                return;
            }
            selectedPort.set(customPort);
            dialog.dispose();
        });

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(640, dialog.getHeight()));
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        return selectedPort.get();
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
