package vista;

import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.extras.components.FlatComboBox;
import com.formdev.flatlaf.extras.components.FlatScrollPane;
import com.formdev.flatlaf.extras.components.FlatSpinner;
import com.formdev.flatlaf.extras.components.FlatTextField;
import controlador.GestorServidores;
import controlador.automation.AutomationRuleValidation;
import modelo.Server;
import modelo.automation.AutomationActionType;
import modelo.automation.AutomationIntervalUnit;
import modelo.automation.AutomationTriggerType;
import modelo.automation.ServerAutomationRule;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

final class PanelAutomatizacion extends JPanel {
    private static final int LIFECYCLE_LIST_HEIGHT = 150;
    private static final int COMMAND_TABLE_HEIGHT = 270;
    private static final String DETAILS_APP = "APP";
    private static final String DETAILS_DAILY = "DAILY";
    private static final String DETAILS_INTERVAL = "INTERVAL";
    private static final String DETAILS_RELATIVE = "RELATIVE";
    private static final TriggerOption[] LIFECYCLE_TRIGGER_OPTIONS = {
            new TriggerOption(AutomationTriggerType.DAILY_TIME, "Hora diaria"),
            new TriggerOption(AutomationTriggerType.INTERVAL, "Intervalo"),
            new TriggerOption(AutomationTriggerType.APP_START, "Al iniciar Dora")
    };
    private static final TriggerOption[] START_TRIGGER_OPTIONS = {
            new TriggerOption(AutomationTriggerType.DAILY_TIME, "Hora diaria"),
            new TriggerOption(AutomationTriggerType.APP_START, "Al iniciar Dora")
    };
    private static final TriggerOption[] COMMAND_TRIGGER_OPTIONS = {
            new TriggerOption(AutomationTriggerType.DAILY_TIME, "Hora diaria"),
            new TriggerOption(AutomationTriggerType.INTERVAL, "Intervalo"),
            new TriggerOption(AutomationTriggerType.APP_START, "Al iniciar Dora"),
            new TriggerOption(AutomationTriggerType.BEFORE_STOP, "Antes de apagado"),
            new TriggerOption(AutomationTriggerType.BEFORE_RESTART, "Antes de reinicio"),
            new TriggerOption(AutomationTriggerType.AFTER_START, "Después de encendido")
    };
    private static final IntervalUnitOption[] INTERVAL_UNIT_OPTIONS = {
            new IntervalUnitOption(AutomationIntervalUnit.SECONDS, "s"),
            new IntervalUnitOption(AutomationIntervalUnit.MINUTES, "min"),
            new IntervalUnitOption(AutomationIntervalUnit.HOURS, "h")
    };
    private static final IntervalUnitOption[] START_OFFSET_UNIT_OPTIONS = {
            new IntervalUnitOption(AutomationIntervalUnit.SECONDS, "s"),
            new IntervalUnitOption(AutomationIntervalUnit.MINUTES, "min")
    };

    private final GestorServidores gestorServidores;
    private final Server server;
    private final EnumMap<AutomationActionType, JPanel> lifecycleListPanels = new EnumMap<>(AutomationActionType.class);
    private final CommandRuleTableModel commandTableModel = new CommandRuleTableModel();
    private final JTable commandTable = new JTable(commandTableModel);
    private final JButton addCommandButton = new FlatButton();
    private final JButton editCommandButton = new FlatButton();
    private final JButton removeCommandButton = new FlatButton();
    private final PropertyChangeListener automationListener;
    private boolean listenerRegistered;

    PanelAutomatizacion(GestorServidores gestorServidores, Server server) {
        super(new BorderLayout());
        this.gestorServidores = gestorServidores;
        this.server = server;
        this.automationListener = evt -> {
            if (!"automatizacionesServidor".equals(evt.getPropertyName())) {
                return;
            }
            Object value = evt.getNewValue();
            if (value instanceof Server updatedServer && isSameServer(updatedServer)) {
                SwingUtilities.invokeLater(this::refreshRules);
            }
        };
        setOpaque(false);
        build();
        refreshRules();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        registerListener();
    }

    @Override
    public void removeNotify() {
        unregisterListener();
        super.removeNotify();
    }

    private void build() {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(createLifecycleCard());
        content.add(Box.createVerticalStrut(10));
        content.add(createCommandsCard());

        FlatScrollPane scroll = new FlatScrollPane();
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setViewportView(content);
        AppTheme.applyStandardScrollSpeed(scroll);
        add(scroll, BorderLayout.CENTER);
    }

    private CardPanel createLifecycleCard() {
        CardPanel card = new CardPanel("Automatización de servidor");
        card.setBorder(BorderFactory.createEmptyBorder());

        JPanel columns = new JPanel(new GridLayout(1, 3, 10, 0));
        columns.setOpaque(false);
        columns.add(createLifecycleColumn(AutomationActionType.STOP));
        columns.add(createLifecycleColumn(AutomationActionType.START));
        columns.add(createLifecycleColumn(AutomationActionType.RESTART));
        card.getContentPanel().add(columns, BorderLayout.CENTER);
        return card;
    }

    private JPanel createLifecycleColumn(AutomationActionType actionType) {
        JPanel column = new JPanel(new BorderLayout(0, 8));
        column.setOpaque(false);

        JLabel title = new JLabel(lifecycleTitle(actionType), SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setForeground(AppTheme.getCardTitleColor());
        column.add(title, BorderLayout.NORTH);

        JPanel listPanel = new JPanel();
        listPanel.setOpaque(false);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        lifecycleListPanels.put(actionType, listPanel);

        JPanel surface = new JPanel(new BorderLayout());
        surface.setBackground(AppTheme.getSurfaceBackground());
        surface.setBorder(AppTheme.createRoundedBorder(new Insets(8, 8, 8, 8), AppTheme.getSubtleBorderColor(), 1f));
        surface.add(listPanel, BorderLayout.NORTH);

        FlatScrollPane scroll = new FlatScrollPane();
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(0, LIFECYCLE_LIST_HEIGHT));
        scroll.setViewportView(surface);
        AppTheme.applyStandardScrollSpeed(scroll, 18, 72);
        column.add(scroll, BorderLayout.CENTER);

        JButton addButton = new FlatButton();
        addButton.setText(lifecycleButtonText(actionType));
        addButton.setIcon(SvgIconFactory.create("doraicons/plus.svg", 14, 14, AppTheme::getForeground));
        addButton.setHorizontalAlignment(SwingConstants.CENTER);
        AppTheme.applyActionButtonStyle(addButton);
        addButton.setEnabled(server != null);
        addButton.addActionListener(e -> openRuleEditor(null, actionType));
        column.add(addButton, BorderLayout.SOUTH);

        return column;
    }

    private CardPanel createCommandsCard() {
        CardPanel card = new CardPanel("Automatización de comandos");
        card.setBorder(BorderFactory.createEmptyBorder());

        configureHeaderButton(addCommandButton, "Añadir comando", "doraicons/plus.svg");
        configureHeaderButton(editCommandButton, "Editar comando", "doraicons/settings.svg");
        configureHeaderButton(removeCommandButton, "Eliminar comando", "doraicons/trash-unselected.svg");
        addCommandButton.addActionListener(e -> openRuleEditor(null, AutomationActionType.COMMAND));
        editCommandButton.addActionListener(e -> editSelectedCommandRule());
        removeCommandButton.addActionListener(e -> removeSelectedCommandRule());
        card.getHeaderActionsPanel().add(addCommandButton);
        card.getHeaderActionsPanel().add(editCommandButton);
        card.getHeaderActionsPanel().add(removeCommandButton);

        configureCommandTable();
        FlatScrollPane scroll = new FlatScrollPane();
        scroll.setBorder(AppTheme.createRoundedBorder(new Insets(0, 0, 0, 0), AppTheme.getSubtleBorderColor(), 1f));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(AppTheme.getPanelBackground());
        scroll.setPreferredSize(new Dimension(0, COMMAND_TABLE_HEIGHT));
        scroll.setViewportView(commandTable);
        AppTheme.applyStandardScrollSpeed(scroll);
        card.getContentPanel().add(scroll, BorderLayout.CENTER);
        return card;
    }

    private void configureCommandTable() {
        commandTable.setFillsViewportHeight(true);
        commandTable.setRowHeight(34);
        commandTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        commandTable.setShowVerticalLines(false);
        commandTable.setShowHorizontalLines(true);
        commandTable.setGridColor(AppTheme.getSubtleBorderColor());
        commandTable.setIntercellSpacing(new Dimension(0, 1));
        commandTable.setOpaque(true);
        commandTable.setBackground(AppTheme.getPanelBackground());
        commandTable.setForeground(AppTheme.getForeground());
        commandTable.setSelectionBackground(AppTheme.getSoftSelectionBackground());
        commandTable.setSelectionForeground(AppTheme.getForeground());
        commandTable.getSelectionModel().addListSelectionListener(this::onCommandSelectionChanged);
        commandTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && commandTable.getSelectedRow() >= 0) {
                    editSelectedCommandRule();
                }
            }
        });

        JTableHeader header = commandTable.getTableHeader();
        header.setReorderingAllowed(false);
        header.setResizingAllowed(true);
        header.setBackground(AppTheme.getSurfaceBackground());
        header.setForeground(AppTheme.getCardTitleColor());
        header.setFont(header.getFont().deriveFont(Font.BOLD));

        DefaultTableCellRenderer centered = new DefaultTableCellRenderer();
        centered.setHorizontalAlignment(SwingConstants.CENTER);
        commandTable.getColumnModel().getColumn(2).setCellRenderer(centered);
        commandTable.getColumnModel().getColumn(3).setCellRenderer(centered);

        TableColumnModel columns = commandTable.getColumnModel();
        columns.getColumn(0).setMinWidth(62);
        columns.getColumn(0).setMaxWidth(72);
        columns.getColumn(0).setPreferredWidth(66);
        columns.getColumn(1).setPreferredWidth(360);
        columns.getColumn(2).setPreferredWidth(170);
        columns.getColumn(3).setPreferredWidth(150);
    }

    private void refreshRules() {
        List<ServerAutomationRule> rules = gestorServidores == null || server == null
                ? List.of()
                : gestorServidores.getReglasAutomatizacion(server);
        refreshLifecycleRules(rules);
        commandTableModel.setRules(rules.stream()
                .filter(rule -> rule != null && rule.getActionType() == AutomationActionType.COMMAND)
                .sorted(Comparator.comparing(PanelAutomatizacion::sortKeyForRule))
                .toList());
        addCommandButton.setEnabled(server != null);
        updateCommandButtons();
        revalidate();
        repaint();
    }

    private void refreshLifecycleRules(List<ServerAutomationRule> rules) {
        for (AutomationActionType actionType : List.of(
                AutomationActionType.STOP,
                AutomationActionType.START,
                AutomationActionType.RESTART
        )) {
            JPanel panel = lifecycleListPanels.get(actionType);
            if (panel == null) {
                continue;
            }
            panel.removeAll();
            List<ServerAutomationRule> actionRules = rules.stream()
                    .filter(rule -> rule != null && rule.getActionType() == actionType)
                    .sorted(Comparator.comparing(PanelAutomatizacion::sortKeyForRule))
                    .toList();
            if (actionRules.isEmpty()) {
                panel.add(createEmptyLifecycleRow());
            } else {
                for (ServerAutomationRule rule : actionRules) {
                    panel.add(createLifecycleRuleRow(rule));
                    panel.add(Box.createVerticalStrut(6));
                }
            }
            panel.revalidate();
            panel.repaint();
        }
    }

    private JComponent createEmptyLifecycleRow() {
        JLabel label = new JLabel("Sin horarios", SwingConstants.CENTER);
        label.setForeground(AppTheme.getMutedForeground());
        label.setBorder(BorderFactory.createEmptyBorder(18, 6, 18, 6));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    private JComponent createLifecycleRuleRow(ServerAutomationRule rule) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(true);
        row.setBackground(AppTheme.getPanelBackground());
        row.setBorder(AppTheme.createRoundedBorder(new Insets(6, 6, 6, 6), AppTheme.getSubtleBorderColor(), 1f));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JCheckBox enabled = new JCheckBox();
        enabled.setOpaque(false);
        enabled.setSelected(Boolean.TRUE.equals(rule.getEnabled()));
        enabled.setToolTipText("Activar o desactivar");
        enabled.addActionListener(e -> toggleRuleEnabled(rule, enabled.isSelected()));
        row.add(enabled, BorderLayout.WEST);

        JLabel label = new JLabel(lifecycleRuleText(rule));
        label.setForeground(Boolean.TRUE.equals(rule.getEnabled()) ? AppTheme.getForeground() : AppTheme.getMutedForeground());
        row.add(label, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        JButton editButton = createInlineIconButton("Editar", "doraicons/settings.svg");
        JButton deleteButton = createInlineIconButton("Eliminar", "doraicons/trash-unselected.svg");
        editButton.addActionListener(e -> openRuleEditor(rule, rule.getActionType()));
        deleteButton.addActionListener(e -> deleteRule(rule));
        actions.add(editButton);
        actions.add(deleteButton);
        row.add(actions, BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openRuleEditor(rule, rule.getActionType());
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(AppTheme.getSoftSelectionBackground());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                row.setBackground(AppTheme.getPanelBackground());
            }
        });
        return row;
    }

    private JButton createInlineIconButton(String tooltip, String iconPath) {
        JButton button = new FlatButton();
        AppTheme.applyHeaderIconButtonStyle(button);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(26, 26));
        button.setMinimumSize(new Dimension(26, 26));
        button.setMaximumSize(new Dimension(26, 26));
        button.setIcon(SvgIconFactory.create(iconPath, 16, 16, AppTheme::getForeground));
        return button;
    }

    private void configureHeaderButton(JButton button, String tooltip, String iconPath) {
        AppTheme.applyHeaderIconButtonStyle(button);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(30, 30));
        button.setMinimumSize(new Dimension(30, 30));
        button.setIcon(SvgIconFactory.create(iconPath, 18, 18, AppTheme::getForeground));
    }

    private void onCommandSelectionChanged(ListSelectionEvent event) {
        if (event == null || !event.getValueIsAdjusting()) {
            updateCommandButtons();
        }
    }

    private void updateCommandButtons() {
        boolean hasSelection = getSelectedCommandRule() != null;
        editCommandButton.setEnabled(hasSelection);
        removeCommandButton.setEnabled(hasSelection);
    }

    private ServerAutomationRule getSelectedCommandRule() {
        int selectedRow = commandTable.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        return commandTableModel.getRuleAt(commandTable.convertRowIndexToModel(selectedRow));
    }

    private void editSelectedCommandRule() {
        ServerAutomationRule selected = getSelectedCommandRule();
        if (selected != null) {
            openRuleEditor(selected, AutomationActionType.COMMAND);
        }
    }

    private void removeSelectedCommandRule() {
        ServerAutomationRule selected = getSelectedCommandRule();
        if (selected != null) {
            deleteRule(selected);
        }
    }

    private void openRuleEditor(ServerAutomationRule source, AutomationActionType actionType) {
        if (server == null || gestorServidores == null || actionType == null) {
            return;
        }

        ServerAutomationRule draft = source == null ? new ServerAutomationRule() : copyRule(source);
        draft.setActionType(actionType);
        if (draft.getTriggerType() == null) {
            draft.setTriggerType(actionType == AutomationActionType.COMMAND
                    ? AutomationTriggerType.INTERVAL
                    : AutomationTriggerType.DAILY_TIME);
        }
        if (draft.getIntervalUnit() == null) {
            draft.setIntervalUnit(AutomationIntervalUnit.MINUTES);
        }
        if (actionType != AutomationActionType.COMMAND && isRelativeTrigger(draft.getTriggerType())) {
            draft.setTriggerType(AutomationTriggerType.DAILY_TIME);
        }

        JDialog dialog = new JDialog(resolveOwner(), editorTitle(source, actionType));
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setModal(true);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        JCheckBox enabledCheckBox = new JCheckBox("Activa");
        enabledCheckBox.setOpaque(false);
        enabledCheckBox.setSelected(!Boolean.FALSE.equals(draft.getEnabled()));
        addFullWidth(form, enabledCheckBox, gbc, 0);

        FlatTextField commandField = new FlatTextField();
        commandField.setColumns(26);
        commandField.setText(stripCommandSlash(draft.getCommand()));
        if (actionType == AutomationActionType.COMMAND) {
            addLabeledField(form, "Comando", commandField, gbc, 1);
        }

        FlatComboBox<TriggerOption> triggerCombo = new FlatComboBox<>();
        TriggerOption[] triggerOptions = triggerOptionsFor(actionType);
        if (actionType == AutomationActionType.START && draft.getTriggerType() == AutomationTriggerType.INTERVAL) {
            draft.setTriggerType(AutomationTriggerType.DAILY_TIME);
        }
        triggerCombo.setModel(new DefaultComboBoxModel<>(triggerOptions));
        triggerCombo.setSelectedItem(triggerOptionFor(draft.getTriggerType(), triggerOptions));
        addLabeledField(form, "Cuando", triggerCombo, gbc, actionType == AutomationActionType.COMMAND ? 2 : 1);

        CardLayout detailsLayout = new CardLayout();
        JPanel detailsPanel = new JPanel(detailsLayout);
        detailsPanel.setOpaque(false);

        TimePickerPanel timePicker = new TimePickerPanel(draft.getTimeOfDay());
        JPanel dailyPanel = new JPanel(new BorderLayout(8, 0));
        dailyPanel.setOpaque(false);
        dailyPanel.add(new JLabel("Hora"), BorderLayout.WEST);
        dailyPanel.add(timePicker, BorderLayout.CENTER);
        detailsPanel.add(dailyPanel, DETAILS_DAILY);

        FlatSpinner intervalSpinner = new FlatSpinner();
        intervalSpinner.setModel(new SpinnerNumberModel(
                Math.max(1, draft.getIntervalAmount() == null ? 1 : draft.getIntervalAmount()),
                1,
                9999,
                1
        ));
        FlatComboBox<IntervalUnitOption> unitCombo = new FlatComboBox<>();
        unitCombo.setModel(new DefaultComboBoxModel<>(INTERVAL_UNIT_OPTIONS));
        unitCombo.setSelectedItem(intervalUnitOptionFor(draft.getIntervalUnit()));
        JPanel intervalPanel = new JPanel(new BorderLayout(8, 0));
        intervalPanel.setOpaque(false);
        intervalPanel.add(new JLabel("Cada"), BorderLayout.WEST);
        intervalPanel.add(intervalSpinner, BorderLayout.CENTER);
        intervalPanel.add(unitCombo, BorderLayout.EAST);
        detailsPanel.add(intervalPanel, DETAILS_INTERVAL);

        FlatSpinner relativeSpinner = new FlatSpinner();
        relativeSpinner.setModel(new SpinnerNumberModel(
                Math.max(1, draft.getIntervalAmount() == null ? 1 : draft.getIntervalAmount()),
                1,
                9999,
                1
        ));
        FlatComboBox<IntervalUnitOption> relativeUnitCombo = new FlatComboBox<>();
        relativeUnitCombo.setModel(new DefaultComboBoxModel<>(relativeUnitOptionsFor(draft.getTriggerType())));
        relativeUnitCombo.setSelectedItem(intervalUnitOptionFor(
                draft.getIntervalUnit(),
                relativeUnitOptionsFor(draft.getTriggerType())
        ));
        JLabel relativeLabel = new JLabel("Antes");
        JPanel relativePanel = new JPanel(new BorderLayout(8, 0));
        relativePanel.setOpaque(false);
        relativePanel.add(relativeLabel, BorderLayout.WEST);
        relativePanel.add(relativeSpinner, BorderLayout.CENTER);
        relativePanel.add(relativeUnitCombo, BorderLayout.EAST);
        detailsPanel.add(relativePanel, DETAILS_RELATIVE);

        JPanel appPanel = new JPanel(new BorderLayout());
        appPanel.setOpaque(false);
        detailsPanel.add(appPanel, DETAILS_APP);

        int detailRow = actionType == AutomationActionType.COMMAND ? 3 : 2;
        addFullWidth(form, detailsPanel, gbc, detailRow);

        JLabel errorLabel = new JLabel(" ");
        errorLabel.setForeground(AppTheme.getDangerColor());
        addFullWidth(form, errorLabel, gbc, detailRow + 1);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancelButton = new FlatButton();
        cancelButton.setText("Cancelar");
        AppTheme.applyActionButtonStyle(cancelButton);
        JButton saveButton = new FlatButton();
        saveButton.setText("Guardar");
        AppTheme.applyAccentButtonStyle(saveButton);
        actions.add(cancelButton);
        actions.add(saveButton);
        addFullWidth(form, actions, gbc, detailRow + 2);

        Runnable updateDetails = () -> {
            AutomationTriggerType selectedTrigger = selectedTrigger(triggerCombo);
            detailsLayout.show(detailsPanel, detailsCardName(selectedTrigger));
            boolean afterStart = selectedTrigger == AutomationTriggerType.AFTER_START;
            relativeLabel.setText(afterStart ? "Después" : "Antes");
            AutomationIntervalUnit selectedUnit = selectedIntervalUnit(relativeUnitCombo);
            IntervalUnitOption[] relativeOptions = relativeUnitOptionsFor(selectedTrigger);
            relativeUnitCombo.setModel(new DefaultComboBoxModel<>(relativeOptions));
            relativeUnitCombo.setSelectedItem(intervalUnitOptionFor(selectedUnit, relativeOptions));
        };
        triggerCombo.addActionListener(e -> updateDetails.run());
        updateDetails.run();

        cancelButton.addActionListener(e -> dialog.dispose());
        saveButton.addActionListener(e -> {
            ServerAutomationRule updated = copyRule(draft);
            updated.setEnabled(enabledCheckBox.isSelected());
            updated.setActionType(actionType);
            updated.setTriggerType(selectedTrigger(triggerCombo));
            updated.setCommand(actionType == AutomationActionType.COMMAND ? stripCommandSlash(commandField.getText()) : null);

            if (updated.getTriggerType() == AutomationTriggerType.DAILY_TIME) {
                updated.setTimeOfDay(timePicker.getTimeOfDay());
                updated.setIntervalAmount(null);
                updated.setIntervalUnit(AutomationIntervalUnit.MINUTES);
            } else if (updated.getTriggerType() == AutomationTriggerType.INTERVAL) {
                updated.setTimeOfDay(null);
                updated.setIntervalAmount(((Number) intervalSpinner.getValue()).intValue());
                updated.setIntervalUnit(selectedIntervalUnit(unitCombo));
            } else if (isRelativeTrigger(updated.getTriggerType())) {
                updated.setTimeOfDay(null);
                updated.setIntervalAmount(((Number) relativeSpinner.getValue()).intValue());
                updated.setIntervalUnit(selectedIntervalUnit(relativeUnitCombo));
            } else {
                updated.setTimeOfDay(null);
                updated.setIntervalAmount(null);
                updated.setIntervalUnit(AutomationIntervalUnit.MINUTES);
            }
            updated.setDisplayName(buildDisplayName(updated));

            AutomationRuleValidation validation = gestorServidores.getAutomationService().validateRule(server, updated);
            if (!validation.valid()) {
                errorLabel.setText(validation.message());
                return;
            }

            gestorServidores.guardarReglaAutomatizacion(server, updated);
            dialog.dispose();
            refreshRules();
        });

        dialog.setContentPane(form);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(Math.max(420, dialog.getWidth()), dialog.getHeight()));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void addLabeledField(JPanel form, String label, JComponent field, GridBagConstraints gbc, int row) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel labelComponent = new JLabel(label);
        labelComponent.setForeground(AppTheme.getMutedForeground());
        form.add(labelComponent, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, gbc);
    }

    private void addFullWidth(JPanel form, JComponent component, GridBagConstraints gbc, int row) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(component, gbc);
        gbc.gridwidth = 1;
    }

    private void toggleRuleEnabled(ServerAutomationRule rule, boolean enabled) {
        if (rule == null || gestorServidores == null || server == null) {
            return;
        }
        ServerAutomationRule copy = copyRule(rule);
        copy.setEnabled(enabled);
        gestorServidores.guardarReglaAutomatizacion(server, copy);
        refreshRules();
    }

    private void deleteRule(ServerAutomationRule rule) {
        if (rule == null || rule.getId() == null || gestorServidores == null || server == null) {
            return;
        }
        int result = JOptionPane.showConfirmDialog(
                this,
                "¿Eliminar esta automatización?",
                "Eliminar automatización",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result == JOptionPane.YES_OPTION && gestorServidores.eliminarReglaAutomatizacion(server, rule.getId())) {
            refreshRules();
        }
    }

    private void registerListener() {
        if (!listenerRegistered && gestorServidores != null) {
            gestorServidores.addPropertyChangeListener("automatizacionesServidor", automationListener);
            listenerRegistered = true;
        }
    }

    private void unregisterListener() {
        if (listenerRegistered && gestorServidores != null) {
            gestorServidores.removePropertyChangeListener("automatizacionesServidor", automationListener);
            listenerRegistered = false;
        }
    }

    private boolean isSameServer(Server candidate) {
        if (candidate == server) {
            return true;
        }
        if (candidate == null || server == null) {
            return false;
        }
        return server.getId() != null && Objects.equals(server.getId(), candidate.getId());
    }

    private Window resolveOwner() {
        return SwingUtilities.getWindowAncestor(this);
    }

    private String editorTitle(ServerAutomationRule source, AutomationActionType actionType) {
        String prefix = source == null ? "Nuevo" : "Editar";
        return prefix + " " + actionLabel(actionType).toLowerCase();
    }

    private static String lifecycleTitle(AutomationActionType actionType) {
        return switch (actionType) {
            case STOP -> "Apagados";
            case START -> "Encendidos";
            case RESTART -> "Reinicios";
            case COMMAND -> "Comandos";
        };
    }

    private static String lifecycleButtonText(AutomationActionType actionType) {
        return switch (actionType) {
            case STOP -> "Programar apagados";
            case START -> "Programar encendidos";
            case RESTART -> "Programar reinicios";
            case COMMAND -> "Programar comandos";
        };
    }

    private static String actionLabel(AutomationActionType actionType) {
        return switch (actionType) {
            case START -> "Encendido";
            case STOP -> "Apagado";
            case RESTART -> "Reinicio";
            case COMMAND -> "Comando";
        };
    }

    private static String lifecycleRuleText(ServerAutomationRule rule) {
        if (rule == null) {
            return "-";
        }
        if (rule.getTriggerType() == AutomationTriggerType.DAILY_TIME) {
            return firstNonBlank(rule.getTimeOfDay(), "HH:mm");
        }
        if (rule.getTriggerType() == AutomationTriggerType.INTERVAL) {
            return rule.getActionType() == AutomationActionType.STOP
                    ? "Tras " + describeRelativeOffset(rule) + " encendido"
                    : describeInterval(rule);
        }
        if (rule.getTriggerType() == AutomationTriggerType.APP_START) {
            return "Al iniciar Dora";
        }
        if (isRelativeTrigger(rule.getTriggerType())) {
            return describeWhen(rule) + " - " + describeRelativeOffset(rule);
        }
        return "-";
    }

    private static String describeWhen(ServerAutomationRule rule) {
        if (rule == null || rule.getTriggerType() == null) {
            return "-";
        }
        return switch (rule.getTriggerType()) {
            case DAILY_TIME -> firstNonBlank(rule.getTimeOfDay(), "-");
            case INTERVAL -> "-";
            case APP_START -> "Inicio de Dora";
            case BEFORE_STOP -> "Antes de apagado";
            case BEFORE_RESTART -> "Antes de reinicio";
            case AFTER_START -> "Después de encendido";
        };
    }

    private static String describeFrequency(ServerAutomationRule rule) {
        if (rule == null || rule.getTriggerType() == null) {
            return "-";
        }
        return switch (rule.getTriggerType()) {
            case DAILY_TIME -> "Diariamente";
            case INTERVAL -> rule.getActionType() == AutomationActionType.STOP
                    ? "Tras " + describeRelativeOffset(rule) + " encendido"
                    : describeInterval(rule);
            case APP_START -> "Una vez";
            case BEFORE_STOP, BEFORE_RESTART, AFTER_START -> describeRelativeOffset(rule);
        };
    }

    private static String describeInterval(ServerAutomationRule rule) {
        int amount = rule == null || rule.getIntervalAmount() == null ? 1 : Math.max(1, rule.getIntervalAmount());
        AutomationIntervalUnit unit = rule == null ? null : rule.getIntervalUnit();
        return "Cada " + amount + " " + intervalUnitLabel(unit);
    }

    private static String describeRelativeOffset(ServerAutomationRule rule) {
        int amount = rule == null || rule.getIntervalAmount() == null ? 1 : Math.max(1, rule.getIntervalAmount());
        AutomationIntervalUnit unit = rule == null ? null : rule.getIntervalUnit();
        return amount + " " + intervalUnitLabel(unit);
    }

    private static String sortKeyForRule(ServerAutomationRule rule) {
        if (rule == null) {
            return "";
        }
        if (rule.getTriggerType() == AutomationTriggerType.DAILY_TIME) {
            return "0:" + firstNonBlank(rule.getTimeOfDay(), "");
        }
        if (rule.getTriggerType() == AutomationTriggerType.INTERVAL) {
            long amount = rule.getIntervalAmount() == null ? 0L : Math.max(0, rule.getIntervalAmount());
            return "1:" + String.format("%012d", amount * unitWeightInSeconds(rule.getIntervalUnit()));
        }
        if (isRelativeTrigger(rule.getTriggerType())) {
            long amount = rule.getIntervalAmount() == null ? 0L : Math.max(0, rule.getIntervalAmount());
            return "2:" + rule.getTriggerType().name() + ":" + String.format("%012d", amount * unitWeightInSeconds(rule.getIntervalUnit()));
        }
        return "2:" + firstNonBlank(rule.getDisplayName(), rule.getId());
    }

    private static TriggerOption[] triggerOptionsFor(AutomationActionType actionType) {
        if (actionType == AutomationActionType.COMMAND) {
            return COMMAND_TRIGGER_OPTIONS;
        }
        if (actionType == AutomationActionType.START) {
            return START_TRIGGER_OPTIONS;
        }
        return LIFECYCLE_TRIGGER_OPTIONS;
    }

    private static TriggerOption triggerOptionFor(AutomationTriggerType triggerType, TriggerOption[] options) {
        TriggerOption[] resolvedOptions = options == null || options.length == 0 ? LIFECYCLE_TRIGGER_OPTIONS : options;
        for (TriggerOption option : resolvedOptions) {
            if (option.type() == triggerType) {
                return option;
            }
        }
        return resolvedOptions[0];
    }

    private static IntervalUnitOption intervalUnitOptionFor(AutomationIntervalUnit unit) {
        return intervalUnitOptionFor(unit, INTERVAL_UNIT_OPTIONS);
    }

    private static IntervalUnitOption intervalUnitOptionFor(AutomationIntervalUnit unit, IntervalUnitOption[] options) {
        IntervalUnitOption[] resolvedOptions = options == null || options.length == 0 ? INTERVAL_UNIT_OPTIONS : options;
        for (IntervalUnitOption option : resolvedOptions) {
            if (option.unit() == unit) {
                return option;
            }
        }
        return defaultIntervalUnitOption(resolvedOptions);
    }

    private static IntervalUnitOption[] relativeUnitOptionsFor(AutomationTriggerType triggerType) {
        return triggerType == AutomationTriggerType.AFTER_START ? START_OFFSET_UNIT_OPTIONS : INTERVAL_UNIT_OPTIONS;
    }

    private static IntervalUnitOption defaultIntervalUnitOption(IntervalUnitOption[] options) {
        for (IntervalUnitOption option : options) {
            if (option.unit() == AutomationIntervalUnit.MINUTES) {
                return option;
            }
        }
        return options[0];
    }

    private static String intervalUnitLabel(AutomationIntervalUnit unit) {
        if (unit == AutomationIntervalUnit.SECONDS) {
            return "s";
        }
        if (unit == AutomationIntervalUnit.HOURS) {
            return "h";
        }
        return "min";
    }

    private static long unitWeightInSeconds(AutomationIntervalUnit unit) {
        if (unit == AutomationIntervalUnit.HOURS) {
            return 3600L;
        }
        if (unit == AutomationIntervalUnit.MINUTES) {
            return 60L;
        }
        return 1L;
    }

    private static AutomationTriggerType selectedTrigger(FlatComboBox<TriggerOption> combo) {
        Object selected = combo.getSelectedItem();
        return selected instanceof TriggerOption option ? option.type() : AutomationTriggerType.DAILY_TIME;
    }

    private static AutomationIntervalUnit selectedIntervalUnit(FlatComboBox<IntervalUnitOption> combo) {
        Object selected = combo.getSelectedItem();
        return selected instanceof IntervalUnitOption option ? option.unit() : AutomationIntervalUnit.MINUTES;
    }

    private static String detailsCardName(AutomationTriggerType triggerType) {
        if (triggerType == AutomationTriggerType.INTERVAL) {
            return DETAILS_INTERVAL;
        }
        if (triggerType == AutomationTriggerType.APP_START) {
            return DETAILS_APP;
        }
        if (isRelativeTrigger(triggerType)) {
            return DETAILS_RELATIVE;
        }
        return DETAILS_DAILY;
    }

    private static String buildDisplayName(ServerAutomationRule rule) {
        if (rule == null || rule.getActionType() == null) {
            return null;
        }
        if (rule.getActionType() == AutomationActionType.COMMAND) {
            return "Comando: " + stripCommandSlash(rule.getCommand());
        }
        return actionLabel(rule.getActionType()) + " " + lifecycleRuleText(rule);
    }

    private static boolean isRelativeTrigger(AutomationTriggerType triggerType) {
        return triggerType == AutomationTriggerType.BEFORE_STOP
                || triggerType == AutomationTriggerType.BEFORE_RESTART
                || triggerType == AutomationTriggerType.AFTER_START;
    }

    private static String stripCommandSlash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1).trim();
        }
        return trimmed;
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static ServerAutomationRule copyRule(ServerAutomationRule source) {
        ServerAutomationRule copy = new ServerAutomationRule();
        if (source == null) {
            return copy;
        }
        copy.setId(source.getId());
        copy.setDisplayName(source.getDisplayName());
        copy.setEnabled(source.getEnabled());
        copy.setTriggerType(source.getTriggerType());
        copy.setTimeOfDay(source.getTimeOfDay());
        copy.setIntervalAmount(source.getIntervalAmount());
        copy.setIntervalUnit(source.getIntervalUnit());
        copy.setActionType(source.getActionType());
        copy.setCommand(source.getCommand());
        return copy;
    }

    GestorServidores getGestorServidores() {
        return gestorServidores;
    }

    Server getServer() {
        return server;
    }

    private final class CommandRuleTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Activa", "Comando", "Cuando", "Frecuencia"};
        private final List<ServerAutomationRule> rules = new ArrayList<>();

        void setRules(List<ServerAutomationRule> nextRules) {
            rules.clear();
            if (nextRules != null) {
                rules.addAll(nextRules);
            }
            fireTableDataChanged();
        }

        ServerAutomationRule getRuleAt(int row) {
            if (row < 0 || row >= rules.size()) {
                return null;
            }
            return rules.get(row);
        }

        @Override
        public int getRowCount() {
            return rules.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ServerAutomationRule rule = getRuleAt(rowIndex);
            if (rule == null) {
                return "";
            }
            return switch (columnIndex) {
                case 0 -> Boolean.TRUE.equals(rule.getEnabled());
                case 1 -> stripCommandSlash(rule.getCommand());
                case 2 -> describeWhen(rule);
                case 3 -> describeFrequency(rule);
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex != 0) {
                return;
            }
            ServerAutomationRule rule = getRuleAt(rowIndex);
            if (rule != null) {
                toggleRuleEnabled(rule, Boolean.TRUE.equals(aValue));
            }
        }
    }

    private record TriggerOption(AutomationTriggerType type, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record IntervalUnitOption(AutomationIntervalUnit unit, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final class TimePickerPanel extends JPanel {
        private final FlatSpinner hourSpinner = new FlatSpinner();
        private final FlatSpinner minuteSpinner = new FlatSpinner();

        TimePickerPanel(String initialTime) {
            super(new FlowLayout(FlowLayout.LEFT, 6, 0));
            setOpaque(false);

            int[] parts = parseTime(initialTime);
            configureSpinner(hourSpinner, 0, 23, parts[0]);
            configureSpinner(minuteSpinner, 0, 59, parts[1]);

            JLabel separator = new JLabel(":");
            separator.setFont(separator.getFont().deriveFont(Font.BOLD));
            separator.setForeground(AppTheme.getMutedForeground());

            add(hourSpinner);
            add(separator);
            add(minuteSpinner);
        }

        String getTimeOfDay() {
            return "%02d:%02d".formatted(
                    ((Number) hourSpinner.getValue()).intValue(),
                    ((Number) minuteSpinner.getValue()).intValue()
            );
        }

        private static void configureSpinner(FlatSpinner spinner, int min, int max, int value) {
            spinner.setModel(new SpinnerNumberModel(value, min, max, 1));
            spinner.setEditor(new JSpinner.NumberEditor(spinner, "00"));
            spinner.setPreferredSize(new Dimension(70, 30));
            spinner.setMinimumSize(new Dimension(70, 30));
            if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
                editor.getTextField().setEditable(false);
                editor.getTextField().setHorizontalAlignment(SwingConstants.CENTER);
            }
            spinner.addMouseWheelListener(e -> {
                Object next = e.getWheelRotation() < 0 ? spinner.getNextValue() : spinner.getPreviousValue();
                if (next != null) {
                    spinner.setValue(next);
                }
            });
        }

        private static int[] parseTime(String value) {
            if (value == null || !value.matches("\\d{1,2}:\\d{1,2}")) {
                return new int[]{9, 0};
            }
            String[] parts = value.split(":", 2);
            try {
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                    return new int[]{9, 0};
                }
                return new int[]{hour, minute};
            } catch (NumberFormatException ex) {
                return new int[]{9, 0};
            }
        }
    }
}
