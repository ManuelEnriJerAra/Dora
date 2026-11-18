package vista;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public final class ProcessWizardDialog {
    private final JDialog dialog;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton backButton = new JButton();
    private final JButton nextButton = new JButton();
    private final Icon nextIcon = SvgIconFactory.create("doraicons/arrow-right.svg", 28, 28, AppTheme::getForeground);
    private final Icon finishIcon = SvgIconFactory.create("doraicons/rocket.svg", 28, 28, AppTheme::getForeground);
    private final List<Step> steps;
    private final Options options;
    private int stepIndex;
    private boolean accepted;

    private ProcessWizardDialog(Window owner, String title, List<Step> steps, Options options) {
        this.steps = List.copyOf(steps);
        this.options = options == null ? Options.defaults() : options;
        this.dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        build();
    }

    public static boolean show(Window owner, String title, List<Step> steps, Options options) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        ProcessWizardDialog wizard = create(owner, title, steps, options);
        return wizard.showDialog();
    }

    public static ProcessWizardDialog create(Window owner, String title, List<Step> steps, Options options) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("Process wizard requires at least one step.");
        }
        return new ProcessWizardDialog(owner, title, steps, options);
    }

    public boolean showDialog() {
        dialog.setVisible(true);
        return accepted;
    }

    public JDialog getDialog() {
        return dialog;
    }

    private void build() {
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        for (Step step : steps) {
            cards.add(createStepPanel(step.title(), step.content()), step.id());
        }

        AppTheme.applyHeaderIconButtonStyle(backButton);
        AppTheme.applyHeaderIconButtonStyle(nextButton);
        backButton.setIcon(SvgIconFactory.create("doraicons/arrow-left.svg", 28, 28, AppTheme::getForeground));
        nextButton.setIcon(nextIcon);
        backButton.setToolTipText("Anterior");
        nextButton.setToolTipText("Siguiente");
        Dimension arrowButtonSize = new Dimension(44, 44);
        backButton.setPreferredSize(arrowButtonSize);
        nextButton.setPreferredSize(arrowButtonSize);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        nav.add(backButton);
        nav.add(nextButton);
        statusLabel.setForeground(Color.RED);
        statusLabel.setVerticalAlignment(SwingConstants.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(nav, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));
        root.add(cards, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(root);

        backButton.addActionListener(e -> previousStep());
        nextButton.addActionListener(e -> nextStep());
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // En el primer paso todavía no se ha elegido nada: se cierra sin preguntar.
                if (stepIndex == 0 || options.confirmCancel().getAsBoolean()) {
                    accepted = false;
                    dialog.dispose();
                }
            }
        });

        dialog.pack();
        Dimension minimum = options.minimumSize();
        if (minimum != null) {
            dialog.setMinimumSize(minimum);
            if (dialog.getWidth() < minimum.width || dialog.getHeight() < minimum.height) {
                dialog.setSize(Math.max(dialog.getWidth(), minimum.width), Math.max(dialog.getHeight(), minimum.height));
            }
        }
        dialog.setLocationRelativeTo(dialog.getOwner());
        refresh();
    }

    public void refresh() {
        Step step = currentStep();
        boolean lastStep = stepIndex == steps.size() - 1;
        backButton.setEnabled(stepIndex > 0);
        nextButton.setEnabled(step.canContinue().getAsBoolean());
        nextButton.setIcon(lastStep ? finishIcon : nextIcon);
        nextButton.setToolTipText(lastStep ? options.finishTooltip() : "Siguiente");
        statusLabel.setText(" ");
        cardLayout.show(cards, step.id());
        options.stepChanged().accept(stepIndex);
    }

    private void previousStep() {
        if (stepIndex <= 0) {
            return;
        }
        stepIndex--;
        refresh();
    }

    private void nextStep() {
        Step step = currentStep();
        String validation = step.validate().apply(stepIndex);
        if (validation != null && !validation.isBlank()) {
            statusLabel.setText(validation);
            nextButton.setEnabled(step.canContinue().getAsBoolean());
            return;
        }
        if (stepIndex == steps.size() - 1) {
            accepted = true;
            dialog.dispose();
            return;
        }
        stepIndex++;
        refresh();
    }

    private Step currentStep() {
        return steps.get(Math.max(0, Math.min(stepIndex, steps.size() - 1)));
    }

    private JPanel createStepPanel(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        JLabel titleLabel = new JLabel(title == null ? "" : title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    public record Step(
            String id,
            String title,
            JComponent content,
            BooleanSupplier canContinue,
            IntFunction<String> validate
    ) {
        public Step {
            id = id == null || id.isBlank() ? "step-" + System.nanoTime() : id;
            content = Objects.requireNonNull(content, "content");
            canContinue = canContinue == null ? () -> true : canContinue;
            validate = validate == null ? ignored -> null : validate;
        }

        public static Step of(String id, String title, JComponent content) {
            return new Step(id, title, content, () -> true, ignored -> null);
        }
    }

    public record Options(
            Dimension minimumSize,
            String finishTooltip,
            BooleanSupplier confirmCancel,
            IntConsumer stepChanged
    ) {
        public Options {
            minimumSize = minimumSize == null ? new Dimension(760, 520) : minimumSize;
            finishTooltip = finishTooltip == null || finishTooltip.isBlank() ? "Finalizar" : finishTooltip;
            confirmCancel = confirmCancel == null ? () -> true : confirmCancel;
            stepChanged = stepChanged == null ? ignored -> { } : stepChanged;
        }

        public static Options defaults() {
            return new Options(new Dimension(760, 520), "Finalizar", () -> true, ignored -> { });
        }
    }

    public static List<Step> steps(Step... steps) {
        List<Step> result = new ArrayList<>();
        if (steps != null) {
            for (Step step : steps) {
                if (step != null) {
                    result.add(step);
                }
            }
        }
        return result;
    }
}
