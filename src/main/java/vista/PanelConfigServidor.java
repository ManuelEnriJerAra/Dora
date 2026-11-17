package vista;

import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.extras.components.FlatComboBox;
import com.formdev.flatlaf.extras.components.FlatScrollPane;
import com.formdev.flatlaf.extras.components.FlatSlider;
import com.formdev.flatlaf.extras.components.FlatSpinner;
import com.formdev.flatlaf.extras.components.FlatTextArea;
import com.formdev.flatlaf.extras.components.FlatTextField;
import controlador.GestorServidores;
import controlador.Utilidades;
import modelo.Server;
import modelo.ServerConfig;
import modelo.MinecraftConstants;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@SuppressWarnings("unused")
public class PanelConfigServidor extends JPanel {
    private static final String[] DIFFICULTY_OPTIONS = MinecraftConstants.DIFFICULTIES.toArray(String[]::new);
    private static final String[] GAMEMODE_OPTIONS = MinecraftConstants.GAMEMODES.toArray(String[]::new);
    private static final String[] LEVEL_TYPE_OPTIONS = MinecraftConstants.WORLD_TYPES_NAMESPACED.toArray(String[]::new);

    private static final String SECTION_ACCESS_NETWORK = "Acceso y red";
    private static final String SECTION_WORLD_GENERATION = "Mundo y generación";
    private static final String SECTION_GAMEPLAY = "Jugabilidad";
    private static final String SECTION_PERFORMANCE = "Rendimiento";
    private static final String SECTION_MESSAGES_ADVANCED = "Mensajes y avanzado";
    private static final String SECTION_OTHER = "Otros ajustes";

    private static final int RAM_MIN_MB = 512;
    private static final int RAM_MAX_MB = 16384;
    private static final int RAM_STEP_MB = 256;

    private static final Map<String, String> FIELD_LABELS = createFieldLabels();
    private static final Map<String, String> FIELD_DESCRIPTIONS = createFieldDescriptions();

    private static final java.util.List<String> SECTION_ORDER = java.util.List.of(
            SECTION_ACCESS_NETWORK,
            SECTION_WORLD_GENERATION,
            SECTION_GAMEPLAY,
            SECTION_PERFORMANCE,
            SECTION_MESSAGES_ADVANCED,
            SECTION_OTHER
    );

    private enum FieldKind {
        BOOLEAN,
        SELECT,
        NUMBER,
        TEXT,
        TEXTAREA
    }

    private record FieldSpec(String key, String value, FieldKind kind) {}

    private final GestorServidores gestorServidores;
    private final JPanel formPanel;
    private final Map<String, JComponent> editors = new LinkedHashMap<>();
    private Path propertiesPath;
    private JSlider xmsSlider;
    private JSlider xmxSlider;
    private JSpinner xmsSpinner;
    private JSpinner xmxSpinner;
    private JLabel xmsValueLabel;
    private JLabel xmxValueLabel;
    private boolean updatingRamControls;
    private final Map<String, String> persistedValues = new LinkedHashMap<>();
    private int persistedXms;
    private int persistedXmx;
    private final JButton saveButton;
    private final PropertyChangeListener configuracionServidorListener;
    private boolean configuracionServidorListenerRegistrado;

    PanelConfigServidor(GestorServidores gestorServidores){
        this.gestorServidores = gestorServidores;
        this.configuracionServidorListener = evt -> SwingUtilities.invokeLater(() -> aplicarConfiguracionServidorExterna(evt.getNewValue()));
        this.setLayout(new BorderLayout());
        this.setOpaque(false);

        CardPanel section = new CardPanel("Configuración del servidor");
        section.setBorder(BorderFactory.createEmptyBorder());
        this.add(section, BorderLayout.CENTER);

        JButton recargar = new FlatButton();
        recargar.setText(tr("panel.config.reload", "Recargar"));
        AppTheme.applyRefreshIconButtonStyle(recargar);
        recargar.setToolTipText(tr("panel.config.reload", "Recargar"));
        saveButton = new FlatButton();
        saveButton.setText(tr("panel.config.save", "Guardar"));
        applyDefaultSaveButtonStyle();
        section.getHeaderActionsPanel().add(recargar);
        JLabel avisoReinicio = new JLabel("Los cambios se aplican al reiniciar el servidor.");
        avisoReinicio.setForeground(AppTheme.getMutedForeground());
        avisoReinicio.setFont(avisoReinicio.getFont().deriveFont(Font.PLAIN, Math.max(11f, avisoReinicio.getFont().getSize2D() - 1f)));
        section.getActionsPanel().add(avisoReinicio);
        section.getActionsPanel().add(saveButton);

        formPanel = new JPanel();
        formPanel.setOpaque(false);
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        FlatScrollPane scroll = new FlatScrollPane();
        scroll.setViewportView(formPanel);
        scroll.setBorder(null);
        scroll.setOpaque(true);
        scroll.setBackground(AppTheme.getPanelBackground());
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(AppTheme.getPanelBackground());
        try{
            JScrollBar v = scroll.getVerticalScrollBar();
            if(v != null){
                v.setBackground(AppTheme.getPanelBackground());
                v.setUnitIncrement(24);
                v.setBlockIncrement(90);
            }
            JScrollBar h = scroll.getHorizontalScrollBar();
            if(h != null){
                h.setBackground(AppTheme.getPanelBackground());
            }
        } catch (Exception ignored){
        }
        section.getContentPanel().add(scroll, BorderLayout.CENTER);

        recargar.addActionListener(e -> reloadWithConfirmation());
        saveButton.addActionListener(e -> save(false));

        SwingUtilities.invokeLater(this::reload);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if(!configuracionServidorListenerRegistrado){
            gestorServidores.addPropertyChangeListener("configuracionServidor", configuracionServidorListener);
            configuracionServidorListenerRegistrado = true;
        }
    }

    @Override
    public void removeNotify() {
        if(configuracionServidorListenerRegistrado){
            gestorServidores.removePropertyChangeListener("configuracionServidor", configuracionServidorListener);
            configuracionServidorListenerRegistrado = false;
        }
        super.removeNotify();
    }

    void reload(){
        Server server = gestorServidores.getServidorSeleccionado();
        if(server == null || server.getServerDir() == null || server.getServerDir().isBlank()){
            mostrarVacio(tr("panel.config.empty.no_server", "No hay servidor seleccionado."));
            return;
        }

        propertiesPath = Path.of(server.getServerDir()).resolve("server.properties");
        if(!Files.exists(propertiesPath)){
            mostrarVacio(tr("panel.config.empty.missing_properties", "No existe server.properties en: ") + propertiesPath);
            return;
        }

        Properties props;
        try{
            props = Utilidades.cargarPropertiesUtf8(propertiesPath);
        } catch (IOException ex){
            mostrarVacio(tr("panel.config.empty.read_error", "Error leyendo server.properties: ") + ex.getMessage());
            return;
        }

        editors.clear();
        xmsSlider = null;
        xmxSlider = null;
        xmsSpinner = null;
        xmxSpinner = null;
        xmsValueLabel = null;
        xmxValueLabel = null;
        updatingRamControls = false;
        formPanel.removeAll();

        java.util.List<String> keys = new ArrayList<>();
        for(Object k : props.keySet()){
            if(k != null){
                String key = String.valueOf(k);
                if(!isManagedOutsideServerConfigPanel(key)){
                    keys.add(key);
                }
            }
        }
        Collections.sort(keys);

        Map<String, java.util.List<String>> groupedKeys = new LinkedHashMap<>();
        for(String sectionTitle : SECTION_ORDER){
            groupedKeys.put(sectionTitle, new ArrayList<>());
        }
        for(String key : keys){
            groupedKeys.computeIfAbsent(categorizeKey(key), ignored -> new ArrayList<>()).add(key);
        }

        boolean first = true;
        for(String sectionTitle : SECTION_ORDER){
            java.util.List<String> sectionKeys = groupedKeys.get(sectionTitle);
            boolean showSection = sectionKeys != null && !sectionKeys.isEmpty();
            if(SECTION_PERFORMANCE.equals(sectionTitle)){
                showSection = true;
            }
            if(!showSection) continue;

            if(!first){
                formPanel.add(Box.createVerticalStrut(12));
            }
            formPanel.add(crearSeccion(sectionTitle, sectionKeys == null ? java.util.List.of() : sectionKeys, props));
            first = false;
        }

        formPanel.add(Box.createVerticalGlue());
        formPanel.revalidate();
        formPanel.repaint();
        markCurrentStateAsPersisted();
        updateSaveButtonState();
    }

    boolean hasUnsavedChanges(){
        if(propertiesPath == null || editors.isEmpty()){
            return false;
        }
        if(!persistedValues.equals(captureCurrentValues())){
            return true;
        }
        return persistedXms != getCurrentXmsValue() || persistedXmx != getCurrentXmxValue();
    }

    boolean confirmDiscardOrSave(Component parent){
        if(!hasUnsavedChanges()){
            return true;
        }

        JOptionPane optionPane = new JOptionPane(
                "¿Quieres guardar los cambios?",
                JOptionPane.WARNING_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                new Object[0]
        );

        JButton yesButton = createAccentOptionButton("Sí");
        JButton noButton = createSecondaryOptionButton("No");
        JButton cancelButton = createSecondaryOptionButton("Cancelar");
        optionPane.setOptions(new Object[]{yesButton, noButton, cancelButton});
        optionPane.setInitialValue(yesButton);

        JDialog dialog = optionPane.createDialog(parent != null ? parent : this, tr("panel.config.dialog.title", "CONFIG"));
        dialog.setModal(true);

        yesButton.addActionListener(e -> {
            optionPane.setValue(yesButton);
            dialog.dispose();
        });
        noButton.addActionListener(e -> {
            optionPane.setValue(noButton);
            dialog.dispose();
        });
        cancelButton.addActionListener(e -> {
            optionPane.setValue(cancelButton);
            dialog.dispose();
        });

        dialog.setVisible(true);

        Object result = optionPane.getValue();
        if(result == yesButton){
            return save(false);
        }
        if(result == noButton){
            return true;
        }
        return false;
    }

    private JButton createAccentOptionButton(String text){
        JButton button = createOptionButton(text);
        button.setBackground(AppTheme.getMainAccent());
        button.setForeground(Color.WHITE);
        return button;
    }

    private JButton createSecondaryOptionButton(String text){
        JButton button = createOptionButton(text);
        button.setBackground(AppTheme.getSurfaceBackground());
        button.setForeground(AppTheme.getForeground());
        return button;
    }

    private JButton createOptionButton(String text){
        JButton button = new FlatButton();
        button.setText(text);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void reloadWithConfirmation(){
        if(!confirmDiscardOrSave(this)){
            return;
        }
        reload();
    }

    private boolean save(boolean showSuccessMessage){
        if(propertiesPath == null){
            JOptionPane.showMessageDialog(this, tr("panel.config.save.no_properties", "No hay server.properties cargado."), tr("panel.config.dialog.title", "CONFIG"), JOptionPane.WARNING_MESSAGE);
            return false;
        }

        Map<String, String> currentValues = captureCurrentValues();
        String serverPortText = currentValues.get("server-port");
        Integer serverPort = serverPortText == null ? 25565 : parsePortValue(serverPortText);
        if(serverPort == null){
            JOptionPane.showMessageDialog(this, "El puerto del servidor debe estar entre 1 y 65535.", tr("panel.config.dialog.title", "CONFIG"), JOptionPane.WARNING_MESSAGE);
            return false;
        }
        currentValues.putIfAbsent("server-port", String.valueOf(serverPort));
        for(String portKey : java.util.List.of("query.port", "rcon.port")){
            String value = currentValues.get(portKey);
            if(value != null && parsePortValue(value) == null){
                JOptionPane.showMessageDialog(this, formatLabel(portKey) + " debe estar entre 1 y 65535.", tr("panel.config.dialog.title", "CONFIG"), JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
        Properties out;
        try{
            out = Utilidades.cargarPropertiesUtf8(propertiesPath);
        } catch (IOException ex){
            JOptionPane.showMessageDialog(this, tr("panel.config.save.error", "Error guardando: ") + ex.getMessage(), tr("panel.config.dialog.title", "CONFIG"), JOptionPane.ERROR_MESSAGE);
            return false;
        }
        for(Map.Entry<String, String> entry : currentValues.entrySet()){
            out.setProperty(entry.getKey(), entry.getValue());
        }

        try{
            Utilidades.guardarPropertiesUtf8(propertiesPath, out, "Edited by Dora");
        } catch (IOException ex){
            JOptionPane.showMessageDialog(this, tr("panel.config.save.error", "Error guardando: ") + ex.getMessage(), tr("panel.config.dialog.title", "CONFIG"), JOptionPane.ERROR_MESSAGE);
            return false;
        }

        Server server = gestorServidores.getServidorSeleccionado();
        if(server != null){
            ServerConfig config = server.getServerConfig();
            if(config == null){
                config = new ServerConfig();
                server.setServerConfig(config);
            }
            int xms = getCurrentXmsValue(config.getRamInit());
            int xmx = getCurrentXmxValue(config.getRamMax());
            if(xmx < xms){
                xmx = xms;
            }
            config.setRamMax(xmx);
            config.setRamInit(xms);
            config.setPuerto(serverPort);
            gestorServidores.guardarServidor(server);
            gestorServidores.notificarConfiguracionServidor(server);
            gestorServidores.notificarEstadoServidor(server);
        }

        markCurrentStateAsPersisted();
        updateSaveButtonState();
        return true;
    }

    private void aplicarConfiguracionServidorExterna(Object eventValue){
        if(!(eventValue instanceof Server changedServer)){
            return;
        }
        Server selectedServer = gestorServidores.getServidorSeleccionado();
        if(selectedServer == null || changedServer.getId() == null || !changedServer.getId().equals(selectedServer.getId())){
            return;
        }
        if(!hasUnsavedChanges()){
            reload();
            return;
        }

        Integer puerto = obtenerPuertoActualServidor(changedServer);
        if(puerto == null){
            return;
        }
        String nuevoPuerto = String.valueOf(puerto);
        JComponent editor = editors.get("server-port");
        if(editor == null){
            return;
        }

        String puertoPersistidoAnterior = persistedValues.get("server-port");
        boolean puertoEditadoPorUsuario = puertoPersistidoAnterior != null
                && !puertoPersistidoAnterior.equals(readComponentValue(editor));
        persistedValues.put("server-port", nuevoPuerto);
        if(!puertoEditadoPorUsuario){
            setComponentValue(editor, nuevoPuerto);
        }
        updateSaveButtonState();
    }

    private Integer obtenerPuertoActualServidor(Server server){
        if(server == null || server.getServerConfig() == null){
            return null;
        }
        int puerto = server.getServerConfig().getPuerto();
        return puerto > 0 && puerto <= 65535 ? puerto : null;
    }

    private Map<String, String> captureCurrentValues(){
        Map<String, String> values = new LinkedHashMap<>();
        for(Map.Entry<String, JComponent> entry : editors.entrySet()){
            values.put(entry.getKey(), readComponentValue(entry.getValue()));
        }
        return values;
    }

    private String readComponentValue(JComponent comp){
        String value;
        if(comp instanceof JCheckBox cb){
            value = cb.isSelected() ? "true" : "false";
        } else if(comp instanceof JComboBox<?> combo){
            Object selected = combo.getSelectedItem();
            value = selected == null ? "" : String.valueOf(selected);
        } else if(comp instanceof JSpinner spinner){
            Object spinnerValue = spinner.getValue();
            value = spinnerValue == null ? "" : String.valueOf(spinnerValue);
        } else if(comp instanceof JTextArea ta){
            value = ta.getText();
        } else if(comp instanceof JTextField tf){
            value = tf.getText();
        } else {
            value = "";
        }
        return value == null ? "" : value;
    }

    private void setComponentValue(JComponent comp, String value){
        String safeValue = value == null ? "" : value;
        if(comp instanceof JCheckBox cb){
            cb.setSelected(Boolean.parseBoolean(safeValue));
        } else if(comp instanceof JComboBox<?> combo){
            combo.setSelectedItem(safeValue);
        } else if(comp instanceof JSpinner spinner){
            int nuevo = parseIntegerValue(safeValue);
            if(spinner.getModel() instanceof SpinnerNumberModel m){
                Comparable<?> min = m.getMinimum(); Comparable<?> max = m.getMaximum();
                if(min instanceof Integer mi) nuevo = Math.max(mi, nuevo);
                if(max instanceof Integer ma) nuevo = Math.min(ma, nuevo);
            }
            spinner.setValue(nuevo);
        } else if(comp instanceof JTextArea ta){
            ta.setText(safeValue);
        } else if(comp instanceof JTextField tf){
            tf.setText(safeValue);
        }
    }

    private void markCurrentStateAsPersisted(){
        persistedValues.clear();
        persistedValues.putAll(captureCurrentValues());
        persistedXms = getCurrentXmsValue();
        persistedXmx = getCurrentXmxValue();
    }

    private void updateSaveButtonState(){
        if(saveButton == null) return;
        boolean hasUnsavedChanges = hasUnsavedChanges();
        applyDefaultSaveButtonStyle();
        saveButton.setEnabled(hasUnsavedChanges);
        if(hasUnsavedChanges){
            AppTheme.applyAccentButtonStyle(saveButton);
        }
        saveButton.revalidate();
        saveButton.repaint();
    }

    private void applyDefaultSaveButtonStyle(){
        if(saveButton == null) return;
        AppTheme.applyActionButtonStyle(saveButton);
    }


    private void attachDirtyTracking(JCheckBox checkBox){
        checkBox.addActionListener(e -> updateSaveButtonState());
    }

    private void attachDirtyTracking(JComboBox<?> combo){
        combo.addActionListener(e -> updateSaveButtonState());
        if(combo.isEditable()){
            Component editor = combo.getEditor().getEditorComponent();
            if(editor instanceof JTextField textField){
                attachDirtyTracking(textField);
            }
        }
    }

    private void attachDirtyTracking(JSpinner spinner){
        spinner.addChangeListener(e -> updateSaveButtonState());
    }

    private void attachDirtyTracking(JTextComponent textComponent){
        textComponent.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateSaveButtonState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateSaveButtonState();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateSaveButtonState();
            }
        });
    }

    private int getCurrentXmsValue(){
        return getCurrentXmsValue(RAM_MIN_MB);
    }

    private int getCurrentXmxValue(){
        return getCurrentXmxValue(RAM_MIN_MB);
    }

    private int getCurrentXmsValue(int fallback){
        if(xmsSlider != null){
            return xmsSlider.getValue();
        }
        if(xmsSpinner != null){
            return normalizeRamValue(((Number) xmsSpinner.getValue()).intValue());
        }
        return normalizeRamValue(fallback);
    }

    private int getCurrentXmxValue(int fallback){
        if(xmxSlider != null){
            return xmxSlider.getValue();
        }
        if(xmxSpinner != null){
            return normalizeRamValue(((Number) xmxSpinner.getValue()).intValue());
        }
        return normalizeRamValue(fallback);
    }

    private JComponent crearSeccion(String title, java.util.List<String> keys, Properties props){
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setBorder(BorderFactory.createEmptyBorder());
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel sectionPanel = new JPanel();
        sectionPanel.setOpaque(false);
        sectionPanel.setLayout(new BoxLayout(sectionPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(tr(sectionTranslationKey(title), title));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        sectionPanel.add(titleLabel);

        JSeparator separator = new JSeparator();
        separator.setForeground(AppTheme.getSubtleBorderColor());
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionPanel.add(separator);
        sectionPanel.add(Box.createVerticalStrut(8));

        java.util.List<FieldSpec> booleanFields = new ArrayList<>();
        java.util.List<FieldSpec> otherFields = new ArrayList<>();

        for(String key : keys){
            String value = props.getProperty(key, "");
            FieldSpec spec = new FieldSpec(key, value, detectFieldKind(key, value));
            if(spec.kind() == FieldKind.BOOLEAN){
                booleanFields.add(spec);
            } else {
                otherFields.add(spec);
            }
        }

        boolean needsGap = false;
        needsGap = addBooleanGroup(sectionPanel, booleanFields, needsGap);
        if(SECTION_PERFORMANCE.equals(title)){
            needsGap = addRamGroup(sectionPanel, needsGap);
        }
        addMixedFieldGroup(sectionPanel, otherFields, needsGap);

        wrapper.add(sectionPanel, BorderLayout.NORTH);
        return wrapper;
    }

    private boolean addBooleanGroup(JPanel parent, java.util.List<FieldSpec> specs, boolean needsGapBefore){
        if(specs.isEmpty()) return needsGapBefore;
        if(needsGapBefore){
            parent.add(Box.createVerticalStrut(8));
        }

        JPanel grid = new JPanel(new GridLayout(0, 3, 8, 6));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        for(FieldSpec spec : specs){
            grid.add(crearTarjetaBoolean(spec));
        }

        while(specs.size() % 3 != 0 && grid.getComponentCount() % 3 != 0){
            JPanel filler = new JPanel();
            filler.setOpaque(false);
            grid.add(filler);
        }

        parent.add(grid);
        return true;
    }

    private boolean addRamGroup(JPanel parent, boolean needsGapBefore){
        if(needsGapBefore){
            parent.add(Box.createVerticalStrut(8));
        }
        parent.add(crearTarjetaRam());
        return true;
    }

    private boolean addMixedFieldGroup(JPanel parent, java.util.List<FieldSpec> specs, boolean needsGapBefore){
        if(specs.isEmpty()) return needsGapBefore;
        if(needsGapBefore){
            parent.add(Box.createVerticalStrut(8));
        }

        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        for(int i = 0; i < specs.size(); i += 2){
            JComponent left = crearTarjetaParaCampo(specs.get(i));
            JComponent right = (i + 1) < specs.size() ? crearTarjetaParaCampo(specs.get(i + 1)) : null;
            group.add(createTwoColumnRow(left, right));
            if(i + 2 < specs.size()){
                group.add(Box.createVerticalStrut(6));
            }
        }

        parent.add(group);
        return true;
    }


    private JPanel createTwoColumnRow(JComponent left, JComponent right){
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        if(right == null){
            row.add(left, BorderLayout.CENTER);
            return row;
        }

        JPanel pair = new JPanel(new GridLayout(1, 2, 8, 0));
        pair.setOpaque(false);
        pair.add(left);
        pair.add(right);
        row.add(pair, BorderLayout.CENTER);
        return row;
    }

    private void applyTooltip(JComponent component, String key){
        if(component == null || key == null) return;
        String tooltip = getFieldDescription(key);
        if(tooltip == null || tooltip.isBlank()) return;
        component.setToolTipText(tooltip);
    }

    private String buildFallbackDescription(String key){
        if(key == null || key.isBlank()) return null;
        return tr("field." + key.toLowerCase(Locale.ROOT) + ".description", "Ajuste avanzado del servidor. Consulta la documentación de Minecraft antes de modificarlo.");
    }

    private String getFieldDescription(String key){
        if(key == null) return null;
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        String fallback = FIELD_DESCRIPTIONS.get(normalizedKey);
        if(fallback == null){
            return buildFallbackDescription(normalizedKey);
        }
        return tr("field." + normalizedKey + ".description", fallback);
    }

    private static String tr(String key, String fallback){
        // Punto unico para futuras traducciones: aquí podra consultarse un ResourceBundle o servicio i18n.
        return fallback;
    }

    private static String sectionTranslationKey(String title){
        if(title == null || title.isBlank()) return "section.unknown";
        return "section." + title.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static Map<String, String> createFieldLabels(){
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("motd", "MOTD");
        labels.put("server-ip", "IP del servidor");
        labels.put("server-port", "Puerto del servidor");
        labels.put("level-type", "Tipo de mundo");
        labels.put("level-name", "Nombre del mundo");
        labels.put("level-seed", "Semilla del mundo");
        labels.put("max-players", "Jugadores máximos");
        labels.put("view-distance", "Distancia de visión");
        labels.put("simulation-distance", "Distancia de simulación");
        labels.put("white-list", "Whitelist");
        labels.put("enable-rcon", "RCON");
        labels.put("enable-query", "Query");
        labels.put("resource-pack", "Resource pack");
        labels.put("difficulty", "Dificultad");
        labels.put("gamemode", "Modo de juego");
        labels.put("force-gamemode", "Forzar modo de juego");
        labels.put("hardcore", "Modo extremo (hardcore)");
        labels.put("pvp", "PvP");
        labels.put("allow-flight", "Permitir vuelo");
        labels.put("allow-nether", "Permitir Nether");
        labels.put("spawn-protection", "Protección del spawn");
        labels.put("max-world-size", "Tamaño máximo del mundo");
        labels.put("generate-structures", "Generar estructuras");
        labels.put("generator-settings", "Ajustes del generador");
        labels.put("enable-command-block", "Bloques de comandos");
        labels.put("enforce-whitelist", "Forzar whitelist");
        labels.put("player-idle-timeout", "Expulsar inactivos (minutos)");
        labels.put("op-permission-level", "Nivel de permisos de OP");
        labels.put("function-permission-level", "Nivel de permisos de funciones");
        labels.put("hide-online-players", "Ocultar jugadores conectados");
        labels.put("enable-status", "Mostrar estado en la lista de servidores");
        labels.put("require-resource-pack", "Exigir resource pack");
        labels.put("network-compression-threshold", "Umbral de compresión de red");
        labels.put("sync-chunk-writes", "Escritura síncrona de chunks");
        labels.put("enforce-secure-profile", "Exigir perfil seguro");
        labels.put("log-ips", "Registrar IPs");
        labels.put("broadcast-console-to-ops", "Mostrar consola a los OP");
        labels.put("broadcast-rcon-to-ops", "Mostrar RCON a los OP");
        labels.put("entity-broadcast-range-percentage", "Distancia de envío de entidades (%)");
        labels.put("max-chained-neighbor-updates", "Máx. actualizaciones encadenadas");
        labels.put("max-tick-time", "Tiempo máximo por tick (ms)");
        labels.put("rate-limit", "Límite de paquetes");
        labels.put("region-file-compression", "Compresión de regiones");
        labels.put("initial-enabled-packs", "Packs activados al inicio");
        labels.put("initial-disabled-packs", "Packs desactivados al inicio");
        labels.put("accepts-transfers", "Aceptar transferencias");
        labels.put("query.port", "Puerto de Query");
        labels.put("rcon.port", "Puerto de RCON");
        labels.put("rcon.password", "Contraseña de RCON");
        labels.put("online-mode", "Modo online (cuentas premium)");
        return labels;
    }

    private static Map<String, String> createFieldDescriptions(){
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("motd", "Mensaje del servidor que verán los jugadores en la lista multijugador. Minecraft solo muestra dos líneas.");
        descriptions.put("difficulty", "Dificultad general del mundo: peaceful, easy, normal o hard.");
        descriptions.put("gamemode", "Modo de juego por defecto para los nuevos jugadores que entren al servidor.");
        descriptions.put("level-type", "Tipo de generación del mundo. En versiones actuales suele usarse normal, flat, large_biomes, amplified o single_biome_surface.");
        descriptions.put("level-name", "Nombre de la carpeta del mundo principal del servidor.");
        descriptions.put("level-seed", "Semilla usada para generar el mundo. Déjala vacía para una semilla aleatoria.");
        descriptions.put("generator-settings", "Parámetros adicionales para ciertos tipos de generación de mundo.");
        descriptions.put("generate-structures", "Activa o desactiva la generación de aldeas, templos, fortalezas y otras estructuras.");
        descriptions.put("allow-nether", "Permite viajar y generar el Nether en el servidor.");
        descriptions.put("spawn-protection", "Radio de protección alrededor del spawn donde solo los operadores pueden construir.");
        descriptions.put("max-world-size", "Límite máximo del tamaño del mundo en bloques.");
        descriptions.put("initial-enabled-packs", "Data packs que se activan al crear el mundo por primera vez.");
        descriptions.put("initial-disabled-packs", "Data packs que permanecen desactivados al crear el mundo.");
        descriptions.put("region-file-compression", "Método de compresión usado en los archivos de región del mundo.");

        descriptions.put("server-ip", "IP concreta a la que se enlaza el servidor. Normalmente se deja vacía.");
        descriptions.put("server-port", "Puerto principal del servidor para que los jugadores se conecten.");
        descriptions.put("query.port", "Puerto usado por el sistema de consultas del servidor.");
        descriptions.put("rcon.port", "Puerto de escucha para RCON.");
        descriptions.put("rcon.password", "Contraseña para acceder a RCON.");
        descriptions.put("online-mode", "Comprueba con Mojang que las cuentas sean premium. Desactivarlo reduce la seguridad.");
        descriptions.put("prevent-proxy-connections", "Endurece las comprobaciones para conexiones que usen proxy o VPN.");
        descriptions.put("enable-query", "Activa el protocolo GameSpy4 para consultar estado del servidor.");
        descriptions.put("enable-rcon", "Activa el control remoto del servidor mediante RCON.");
        descriptions.put("hide-online-players", "Oculta el número real de jugadores conectados en la lista multijugador.");
        descriptions.put("white-list", "Solo podrán entrar los jugadores incluidos en la whitelist.");
        descriptions.put("enforce-whitelist", "Expulsa a jugadores no incluidos en la whitelist cuando está activa.");
        descriptions.put("max-players", "Número máximo de jugadores conectados al mismo tiempo.");
        descriptions.put("accepts-transfers", "Permite aceptar transferencias de jugadores desde otros servidores compatibles.");
        descriptions.put("log-ips", "Guarda u oculta las IPs de los jugadores en los registros del servidor.");

        descriptions.put("hardcore", "Si está activado, los jugadores pasan a espectador al morir.");
        descriptions.put("force-gamemode", "Obliga a que los jugadores usen siempre el gamemode configurado al entrar.");
        descriptions.put("pvp", "Permite o bloquea el daño entre jugadores.");
        descriptions.put("allow-flight", "Evita que el servidor expulse jugadores por volar.");
        descriptions.put("spawn-monsters", "Permite que aparezcan monstruos hostiles en el mundo.");
        descriptions.put("spawn-animals", "Permite que aparezcan animales pasivos.");
        descriptions.put("spawn-npcs", "Permite aldeanos y otros NPCs.");
        descriptions.put("player-idle-timeout", "Minutos de inactividad antes de expulsar a un jugador. 0 lo desactiva.");
        descriptions.put("op-permission-level", "Nivel de permisos por defecto para operadores.");
        descriptions.put("function-permission-level", "Nivel de permisos con el que se ejecutan las funciones de data packs.");

        descriptions.put("view-distance", "Distancia de chunks que el servidor envía al cliente.");
        descriptions.put("simulation-distance", "Distancia de chunks donde se actualizan entidades y mecánicas.");
        descriptions.put("network-compression-threshold", "Tamaño mínimo a partir del que la red comprime paquetes.");
        descriptions.put("max-tick-time", "Tiempo máximo de un tick antes de considerar que el servidor se ha colgado.");
        descriptions.put("rate-limit", "Límite de paquetes por segúndo por conexión. 0 desactiva el límite.");
        descriptions.put("use-native-transport", "Permite usar las optimizaciones nativas de red de Java cuando están disponibles.");
        descriptions.put("sync-chunk-writes", "Fuerza escrituras síncronas de chunks. Más seguro, pero puede ser más lento.");
        descriptions.put("entity-broadcast-range-percentage", "Ajusta el alcance al que se envían entidades a los clientes.");
        descriptions.put("max-chained-neighbor-updates", "Límite de actualizaciones encadenadas de bloques para evitar cascadas excesivas.");
        descriptions.put("pause-when-empty-seconds", "Tiempo que espera el servidor antes de pausarse si no hay jugadores conectados.");

        descriptions.put("resource-pack", "URL del paquete de recursos que el servidor sugiere o exige descargar.");
        descriptions.put("resource-pack-sha1", "Hash SHA-1 del resource pack para validar su integridad.");
        descriptions.put("resource-pack-id", "Identificador del resource pack usado por versiones modernas.");
        descriptions.put("resource-pack-prompt", "Mensaje mostrado al jugador al pedirle que descargue el resource pack.");
        descriptions.put("require-resource-pack", "Obliga a aceptar el paquete de recursos para entrar.");
        descriptions.put("text-filtering-config", "Configuración externa para filtrar texto y chat.");
        descriptions.put("broadcast-console-to-ops", "Env\u00EDa los mensajes de consola a los operadores conectados.");
        descriptions.put("broadcast-rcon-to-ops", "Env\u00EDa a los operadores los comandos ejecutados mediante RCON.");
        descriptions.put("enable-status", "Permite que el servidor responda a pings de estado desde la lista multijugador.");
        descriptions.put("enable-command-block", "Activa el uso de command blocks en el servidor.");
        descriptions.put("enable-jmx-monitoring", "Activa el monitoreo JMX para herramientas externas.");
        descriptions.put("bug-report-link", "Enlace que el servidor puede mostrar para reportar problemas o incidencias.");
        descriptions.put("debug", "Ajuste avanzado del servidor. Revisa la documentación de Minecraft antes de modificarlo.");
        descriptions.put("announce-player-achievements", "Muestra en el chat determinados logros o avances de los jugadores cuando el servidor los anuncia.");
        descriptions.put("snooper-enabled", "Permite el envío de métricas de uso del servidor.");
        return descriptions;
    }

    private void makeWidthFlexible(JComponent component){
        if(component == null) return;
        Dimension preferred = component.getPreferredSize();
        int preferredHeight = preferred != null ? preferred.height : 24;
        component.setMinimumSize(new Dimension(0, Math.max(preferredHeight, 24)));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(preferredHeight, 24)));
    }

    private JComponent crearTarjetaParaCampo(FieldSpec spec){
        return spec.kind() == FieldKind.NUMBER ? crearTarjetaNumero(spec) : crearTarjetaCampoVertical(spec);
    }

    private JComponent crearTarjetaCampoVertical(FieldSpec spec){
        JComponent editor = crearEditor(spec.key(), spec.value());
        return BoxCategory.createFieldCard(
                formatLabel(spec.key()),
                editor,
                6,
                new Insets(7, 8, 7, 8),
                label -> applyTooltip(label, spec.key())
        );
    }

    private JComponent crearTarjetaBoolean(FieldSpec spec){
        JCheckBox checkBox = new JCheckBox(formatLabel(spec.key()));
        checkBox.setOpaque(false);
        checkBox.setSelected("true".equalsIgnoreCase(spec.value()));
        applyTooltip(checkBox, spec.key());
        attachDirtyTracking(checkBox);
        editors.put(spec.key(), checkBox);
        return BoxCategory.createBooleanCard(formatLabel(spec.key()), checkBox);
    }

    private JComponent crearTarjetaNumero(FieldSpec spec){
        JComponent editor = crearEditor(spec.key(), spec.value());
        return BoxCategory.createFieldCard(
                formatLabel(spec.key()),
                editor,
                4,
                new Insets(6, 8, 6, 8),
                label -> applyTooltip(label, spec.key())
        );
    }

    private JComponent crearTarjetaRam(){
        JPanel card = BoxCategory.createSurfacePanel(new BorderLayout(), new Insets(7, 8, 7, 8));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        Server server = gestorServidores.getServidorSeleccionado();
        ServerConfig config = server != null ? server.getServerConfig() : null;
        int currentXms = normalizeRamValue(config != null ? config.getRamInit() : 1024);
        int currentXmx = normalizeRamValue(config != null ? config.getRamMax() : 2048);
        if(currentXmx < currentXms){
            currentXmx = currentXms;
        }
        xmsValueLabel = new JLabel();
        xmxValueLabel = new JLabel();
        xmsSlider = createRamSlider(currentXms);
        xmxSlider = createRamSlider(currentXmx);
        xmsSpinner = createRamSpinner(currentXms);
        xmxSpinner = createRamSpinner(currentXmx);

        xmsSlider.addChangeListener(e -> syncRamFromSliders(true));
        xmxSlider.addChangeListener(e -> syncRamFromSliders(false));
        xmsSpinner.addChangeListener(e -> syncRamFromSpinners(true));
        xmxSpinner.addChangeListener(e -> syncRamFromSpinners(false));
        updateRamLabels();

        card.add(crearFilaSlider(tr("panel.config.ram.initial", "RAM Inicial"), xmsSlider, xmsSpinner, xmsValueLabel));
        card.add(Box.createVerticalStrut(6));
        card.add(crearFilaSlider("RAM Máxima", xmxSlider, xmxSpinner, xmxValueLabel));
        return card;
    }

    private JPanel crearFilaSlider(String labelText, JSlider slider, JSpinner spinner, JLabel valueLabel){
        JPanel wrapper = new JPanel(new BorderLayout(12, 6));
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setPreferredSize(new Dimension(120, 24));
        wrapper.add(label, BorderLayout.WEST);

        JPanel center = new JPanel(new BorderLayout(8, 0));
        center.setOpaque(false);
        center.add(slider, BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(6, 0));
        controls.setOpaque(false);
        spinner.setPreferredSize(new Dimension(92, 24));
        controls.add(spinner, BorderLayout.WEST);
        valueLabel.setPreferredSize(new Dimension(28, 24));
        valueLabel.setHorizontalAlignment(SwingConstants.LEFT);
        controls.add(valueLabel, BorderLayout.CENTER);
        center.add(controls, BorderLayout.EAST);
        wrapper.add(center, BorderLayout.CENTER);
        makeWidthFlexible(wrapper);
        return wrapper;
    }

    private JSlider createRamSlider(int value){
        FlatSlider slider = new FlatSlider();
        slider.setMinimum(RAM_MIN_MB);
        slider.setMaximum(RAM_MAX_MB);
        slider.setValue(normalizeRamValue(value));
        slider.setOpaque(false);
        slider.setMajorTickSpacing(2048);
        slider.setMinorTickSpacing(RAM_STEP_MB);
        slider.setSnapToTicks(true);
        return slider;
    }

    private JSpinner createRamSpinner(int value){
        FlatSpinner spinner = new FlatSpinner();
        spinner.setModel(new SpinnerNumberModel(normalizeRamValue(value), RAM_MIN_MB, RAM_MAX_MB, RAM_STEP_MB));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
        spinner.setRoundRect(true);
        return spinner;
    }

    private void syncRamFromSliders(boolean changedMin){
        if(updatingRamControls || xmsSlider == null || xmxSlider == null || xmsSpinner == null || xmxSpinner == null) return;
        updatingRamControls = true;
        try{
            int xms = xmsSlider.getValue();
            int xmx = xmxSlider.getValue();
            if(xms > xmx){
                if(changedMin){
                    xmx = xms;
                    xmxSlider.setValue(xmx);
                } else {
                    xms = xmx;
                    xmsSlider.setValue(xms);
                }
            }
            xmsSpinner.setValue(xms);
            xmxSpinner.setValue(xmx);
            updateRamLabels();
            updateSaveButtonState();
        } finally {
            updatingRamControls = false;
        }
    }

    private void syncRamFromSpinners(boolean changedMin){
        if(updatingRamControls || xmsSlider == null || xmxSlider == null || xmsSpinner == null || xmxSpinner == null) return;
        updatingRamControls = true;
        try{
            int xms = normalizeRamValue(((Number) xmsSpinner.getValue()).intValue());
            int xmx = normalizeRamValue(((Number) xmxSpinner.getValue()).intValue());
            if(xms > xmx){
                if(changedMin){
                    xmx = xms;
                    xmxSpinner.setValue(xmx);
                } else {
                    xms = xmx;
                    xmsSpinner.setValue(xms);
                }
            }
            xmsSlider.setValue(xms);
            xmxSlider.setValue(xmx);
            updateRamLabels();
            updateSaveButtonState();
        } finally {
            updatingRamControls = false;
        }
    }

    private void updateRamLabels(){
        if(xmsValueLabel != null){
            xmsValueLabel.setText(tr("panel.config.ram.unit", "MB"));
        }
        if(xmxValueLabel != null){
            xmxValueLabel.setText(tr("panel.config.ram.unit", "MB"));
        }
    }

    private int normalizeRamValue(int value){
        int bounded = Math.max(RAM_MIN_MB, Math.min(RAM_MAX_MB, value));
        int remainder = bounded % RAM_STEP_MB;
        if(remainder == 0) return bounded;
        int lower = bounded - remainder;
        int upper = lower + RAM_STEP_MB;
        if(upper > RAM_MAX_MB) return RAM_MAX_MB;
        return (bounded - lower) < (upper - bounded) ? lower : upper;
    }

    private JComponent crearEditor(String key, String value){
        if("difficulty".equalsIgnoreCase(key)){
            FlatComboBox<String> combo = new FlatComboBox<>();
            combo.setModel(new DefaultComboBoxModel<>(DIFFICULTY_OPTIONS));
            combo.setRenderer(new EtiquetaValorRenderer());
            combo.setRoundRect(true);
            combo.setSelectedItem(normalizeDifficulty(value));
            attachDirtyTracking(combo);
            editors.put(key, combo);
            makeWidthFlexible(combo);
            return combo;
        }

        if("gamemode".equalsIgnoreCase(key)){
            FlatComboBox<String> combo = new FlatComboBox<>();
            combo.setModel(new DefaultComboBoxModel<>(GAMEMODE_OPTIONS));
            combo.setRenderer(new EtiquetaValorRenderer());
            combo.setRoundRect(true);
            combo.setSelectedItem(normalizeGamemode(value));
            attachDirtyTracking(combo);
            editors.put(key, combo);
            makeWidthFlexible(combo);
            return combo;
        }

        if("level-type".equalsIgnoreCase(key)){
            FlatComboBox<String> combo = new FlatComboBox<>();
            combo.setModel(new DefaultComboBoxModel<>(LEVEL_TYPE_OPTIONS));
            combo.setRoundRect(true);
            combo.setEditable(true);
            combo.setSelectedItem(normalizeLevelType(value));
            attachDirtyTracking(combo);
            editors.put(key, combo);
            makeWidthFlexible(combo);
            return combo;
        }

        if(detectFieldKind(key, value) == FieldKind.TEXTAREA){
            FlatTextArea ta = new FlatTextArea();
            ta.setText(value == null ? "" : value);
            ta.setRows(2);
            ta.setColumns(20);
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            attachDirtyTracking(ta);
            FlatScrollPane sp = new FlatScrollPane();
            sp.setViewportView(ta);
            sp.setBorder(BorderFactory.createLineBorder(AppTheme.getSubtleBorderColor(), 1, true));
            sp.setPreferredSize(new Dimension(200, 52));
            editors.put(key, ta);
            sp.setMinimumSize(new Dimension(0, 52));
            sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
            return sp;
        }

        if(detectFieldKind(key, value) == FieldKind.NUMBER){
            int numericValue = parseIntegerValue(value);
            FlatSpinner spinner = new FlatSpinner();
            spinner.setModel(crearModeloNumerico(key, numericValue));
            spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
            spinner.setRoundRect(true);
            attachDirtyTracking(spinner);
            editors.put(key, spinner);
            makeWidthFlexible(spinner);
            return spinner;
        }

        FlatTextField tf = new FlatTextField();
        tf.setText(value == null ? "" : value);
        tf.setRoundRect(true);
        attachDirtyTracking(tf);
        editors.put(key, tf);
        makeWidthFlexible(tf);
        return tf;
    }

    private FieldKind detectFieldKind(String key, String value){
        if("difficulty".equalsIgnoreCase(key) || "gamemode".equalsIgnoreCase(key) || "level-type".equalsIgnoreCase(key)){
            return FieldKind.SELECT;
        }
        if("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)){
            return FieldKind.BOOLEAN;
        }
        if("motd".equalsIgnoreCase(key) || (value != null && value.length() > 32)){
            return FieldKind.TEXTAREA;
        }
        if(isNumericKey(key) || isNumericValue(value)){
            return FieldKind.NUMBER;
        }
        return FieldKind.TEXT;
    }

    private boolean isNumericKey(String key){
        if(key == null) return false;
        return switch(key.toLowerCase(Locale.ROOT)){
            case "server-port", "query.port", "rcon.port", "max-players", "spawn-protection",
                 "max-world-size", "player-idle-timeout", "op-permission-level", "function-permission-level",
                 "view-distance", "simulation-distance", "network-compression-threshold", "max-tick-time",
                 "rate-limit", "entity-broadcast-range-percentage" -> true;
            default -> false;
        };
    }

    private boolean isNumericValue(String value){
        if(value == null) return false;
        String trimmed = value.strip();
        return !trimmed.isEmpty() && trimmed.matches("-?\\d+([.]\\d+)?");
    }

    private int parseIntegerValue(String value){
        if(value == null) return 0;
        try{
            if(value.contains(".")){
                return (int) Math.round(Double.parseDouble(value.strip()));
            }
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException ex){
            return 0;
        }
    }

    private Integer parsePortValue(String value){
        if(value == null || value.isBlank()) return null;
        try{
            int port = Integer.parseInt(value.strip());
            return port > 0 && port <= 65535 ? port : null;
        } catch (NumberFormatException ex){
            return null;
        }
    }

    // Puertos entre 1 y 65535, contadores sin negativos; el resto admite cualquier entero (p. ej. -1).
    static SpinnerNumberModel crearModeloNumerico(String key, int valor){
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if(k.endsWith("port")){
            return new SpinnerNumberModel(Math.max(1, Math.min(65535, valor)), 1, 65535, 1);
        }
        if(k.equals("max-players") || k.equals("spawn-protection") || k.equals("view-distance")
                || k.equals("simulation-distance") || k.equals("player-idle-timeout") || k.equals("max-world-size")){
            return new SpinnerNumberModel(Math.max(0, valor), 0, Integer.MAX_VALUE, 1);
        }
        return new SpinnerNumberModel(valor, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);
    }

    // Valores de server.properties que se guardan en inglés pero se muestran en español.
    static String etiquetaValor(String valor){
        if(valor == null) return "";
        return switch (valor.toLowerCase(Locale.ROOT)) {
            case "peaceful" -> "Pacífico";
            case "easy" -> "Fácil";
            case "normal" -> "Normal";
            case "hard" -> "Difícil";
            case "survival" -> "Supervivencia";
            case "creative" -> "Creativo";
            case "adventure" -> "Aventura";
            case "spectator" -> "Espectador";
            default -> valor;
        };
    }

    private static final class EtiquetaValorRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            return super.getListCellRendererComponent(list, etiquetaValor(value == null ? null : value.toString()), index, isSelected, cellHasFocus);
        }
    }

    private String formatLabel(String key){
        if(key == null || key.isBlank()) return "Propiedad";
        String traducida = FIELD_LABELS.get(key.toLowerCase(Locale.ROOT));
        if(traducida != null) return tr("field." + key.toLowerCase(Locale.ROOT) + ".label", traducida);
        if("motd".equalsIgnoreCase(key)) return "MOTD";
        if("server-ip".equalsIgnoreCase(key)) return "IP del servidor";
        if("server-port".equalsIgnoreCase(key)) return "Puerto del servidor";

        String[] parts = key.replace('-', ' ').replace('.', ' ').split("\\s+");
        StringBuilder label = new StringBuilder();
        for(String part : parts){
            if(part.isBlank()) continue;
            if(!label.isEmpty()) label.append(' ');
            label.append(Character.toUpperCase(part.charAt(0)));
            if(part.length() > 1){
                label.append(part.substring(1));
            }
        }
        return label.toString();
    }

    private String categorizeKey(String key){
        if(key == null || key.isBlank()) return SECTION_OTHER;

        return switch(key.toLowerCase(Locale.ROOT)){
            case "server-ip", "server-port", "query.port", "rcon.port", "rcon.password", "online-mode",
                 "prevent-proxy-connections", "enable-query", "enable-rcon", "hide-online-players",
                 "white-list", "enforce-whitelist", "max-players", "accepts-transfers" -> SECTION_ACCESS_NETWORK;

            case "level-name", "level-seed", "level-type", "generator-settings", "generate-structures",
                 "allow-nether", "spawn-protection", "max-world-size", "initial-enabled-packs",
                 "initial-disabled-packs", "region-file-compression" -> SECTION_WORLD_GENERATION;

            case "difficulty", "gamemode", "hardcore", "force-gamemode", "pvp", "allow-flight",
                 "spawn-monsters", "spawn-animals", "spawn-npcs", "player-idle-timeout",
                 "op-permission-level", "function-permission-level" -> SECTION_GAMEPLAY;

            case "view-distance", "simulation-distance", "network-compression-threshold", "max-tick-time",
                 "rate-limit", "use-native-transport", "sync-chunk-writes", "entity-broadcast-range-percentage" -> SECTION_PERFORMANCE;

            case "motd", "resource-pack", "resource-pack-sha1", "resource-pack-id", "resource-pack-prompt",
                 "require-resource-pack", "text-filtering-config", "broadcast-console-to-ops",
                 "broadcast-rcon-to-ops", "enable-status", "enable-command-block", "enable-jmx-monitoring", "bug-report-link" -> SECTION_MESSAGES_ADVANCED;

            default -> SECTION_OTHER;
        };
    }

    private boolean isManagedOutsideServerConfigPanel(String key){
        if(key == null) return false;
        return switch(key.toLowerCase(Locale.ROOT)){
            case "level-name", "level-seed", "level-type", "generator-settings", "generate-structures",
                 "allow-nether", "spawn-protection", "max-world-size", "initial-enabled-packs",
                 "initial-disabled-packs", "region-file-compression", "motd" -> true;
            default -> false;
        };
    }

    private static String normalizeLevelType(String value){
        if(value == null || value.isBlank()) return LEVEL_TYPE_OPTIONS[0];
        return switch(value.strip().toLowerCase(Locale.ROOT)){
            case "default", MinecraftConstants.WORLD_TYPE_NORMAL, MinecraftConstants.WORLD_TYPE_NORMAL_NAMESPACED -> MinecraftConstants.WORLD_TYPE_NORMAL_NAMESPACED;
            case MinecraftConstants.WORLD_TYPE_FLAT, MinecraftConstants.WORLD_TYPE_FLAT_NAMESPACED -> MinecraftConstants.WORLD_TYPE_FLAT_NAMESPACED;
            case "largebiomes", MinecraftConstants.WORLD_TYPE_LARGE_BIOMES, MinecraftConstants.WORLD_TYPE_LARGE_BIOMES_NAMESPACED -> MinecraftConstants.WORLD_TYPE_LARGE_BIOMES_NAMESPACED;
            case MinecraftConstants.WORLD_TYPE_AMPLIFIED, MinecraftConstants.WORLD_TYPE_AMPLIFIED_NAMESPACED -> MinecraftConstants.WORLD_TYPE_AMPLIFIED_NAMESPACED;
            case "singlebiomesurface", MinecraftConstants.WORLD_TYPE_SINGLE_BIOME_SURFACE, MinecraftConstants.WORLD_TYPE_SINGLE_BIOME_SURFACE_NAMESPACED -> MinecraftConstants.WORLD_TYPE_SINGLE_BIOME_SURFACE_NAMESPACED;
            default -> value.strip();
        };
    }

    private static String normalizeDifficulty(String value){
        if(value == null) return MinecraftConstants.DEFAULT_DIFFICULTY;
        return switch(value.strip().toLowerCase(Locale.ROOT)){
            case "0", MinecraftConstants.DIFFICULTY_PEACEFUL -> MinecraftConstants.DIFFICULTY_PEACEFUL;
            case "1", MinecraftConstants.DIFFICULTY_EASY -> MinecraftConstants.DIFFICULTY_EASY;
            case "2", MinecraftConstants.DIFFICULTY_NORMAL -> MinecraftConstants.DIFFICULTY_NORMAL;
            case "3", MinecraftConstants.DIFFICULTY_HARD -> MinecraftConstants.DIFFICULTY_HARD;
            default -> MinecraftConstants.DEFAULT_DIFFICULTY;
        };
    }

    private static String normalizeGamemode(String value){
        if(value == null) return MinecraftConstants.DEFAULT_GAMEMODE;
        return switch(value.strip().toLowerCase(Locale.ROOT)){
            case "0", MinecraftConstants.GAMEMODE_SURVIVAL -> MinecraftConstants.GAMEMODE_SURVIVAL;
            case "1", MinecraftConstants.GAMEMODE_CREATIVE -> MinecraftConstants.GAMEMODE_CREATIVE;
            case "2", MinecraftConstants.GAMEMODE_ADVENTURE -> MinecraftConstants.GAMEMODE_ADVENTURE;
            case "3", MinecraftConstants.GAMEMODE_SPECTATOR -> MinecraftConstants.GAMEMODE_SPECTATOR;
            default -> MinecraftConstants.DEFAULT_GAMEMODE;
        };
    }

    private void mostrarVacio(String msg){
        editors.clear();
        persistedValues.clear();
        xmsSlider = null;
        xmxSlider = null;
        xmsSpinner = null;
        xmxSpinner = null;
        xmsValueLabel = null;
        xmxValueLabel = null;
        updatingRamControls = false;
        persistedXms = 0;
        persistedXmx = 0;
        formPanel.removeAll();
        updateSaveButtonState();
        JLabel label = new JLabel(msg);
        label.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        formPanel.add(label);
        formPanel.revalidate();
        formPanel.repaint();
    }
}
