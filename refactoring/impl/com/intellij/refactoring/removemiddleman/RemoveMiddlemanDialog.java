package com.intellij.refactoring.removemiddleman;

import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactorJHelpID;
import com.intellij.refactoring.base.BaseRefactoringDialog;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import javax.swing.border.TitledBorder;

@SuppressWarnings({"OverridableMethodCallInConstructor"})
class RemoveMiddlemanDialog extends BaseRefactoringDialog {

    private final JLabel fieldNameLabel = new JLabel();

    private final boolean deleteMethods;

    private final JRadioButton removeMethodsRadioButton;
    private final JRadioButton dontRemoveMethodsRadioButton;


    RemoveMiddlemanDialog(PsiField field, boolean deleteMethods) {
        super(field.getProject(), true);
        setModal(true);
        this.deleteMethods = deleteMethods;
        removeMethodsRadioButton = new JRadioButton(
                RefactorJBundle.message("delete.all.delegating.methods.radio.button"), true);
        dontRemoveMethodsRadioButton = new JRadioButton(
                RefactorJBundle.message("retain.all.delegating.methods.radio.button"));
        setTitle(RefactorJBundle.message("remove.middleman.title"));

        init();
        final String fieldName = field.getName();
        fieldNameLabel.setText(RefactorJBundle.message("field.label", fieldName));
    }

    protected String getDimensionServiceKey() {
        return "RefactorJ.RemoveMiddleman";
    }

    public boolean removeMethods() {
        return removeMethodsRadioButton.isSelected();
    }

    protected JComponent createNorthPanel() {
        return fieldNameLabel;
    }

    public JComponent getPreferredFocusedComponent(){
        return removeMethodsRadioButton;
    }

    protected JComponent createCenterPanel() {
        final JPanel optionsPanel = new JPanel();
        final TitledBorder border = IdeBorderFactory.createTitledBorder(
                RefactorJBundle.message("delete.delegating.methods.border"));
        optionsPanel.setBorder(border);
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));

        removeMethodsRadioButton.setMnemonic('A');
        dontRemoveMethodsRadioButton.setMnemonic('t');

        optionsPanel.add(removeMethodsRadioButton);
        optionsPanel.add(dontRemoveMethodsRadioButton);
        final ButtonGroup bg = new ButtonGroup();
        bg.add(removeMethodsRadioButton);
        bg.add(dontRemoveMethodsRadioButton);

        removeMethodsRadioButton.setSelected(deleteMethods);
        dontRemoveMethodsRadioButton.setSelected(!deleteMethods);

        return optionsPanel;
    }
    protected void doHelpAction() {
        final HelpManager helpManager = HelpManager.getInstance();
        helpManager.invokeHelp(RefactorJHelpID.RemoveMiddleman);
    }

    protected boolean isValid() {
        return true;
    }
}