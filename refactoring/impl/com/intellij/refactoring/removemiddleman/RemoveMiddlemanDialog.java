package com.intellij.refactoring.removemiddleman;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactorJHelpID;
import com.intellij.refactoring.ui.RefactoringDialog;

import javax.swing.*;

@SuppressWarnings({"OverridableMethodCallInConstructor"})
public class RemoveMiddlemanDialog extends RefactoringDialog {

  private final JLabel fieldNameLabel = new JLabel();

  private final boolean deleteMethods;

  private final JRadioButton removeMethodsRadioButton;
  private final JRadioButton dontRemoveMethodsRadioButton;
  private final PsiField myField;


  RemoveMiddlemanDialog(PsiField field, boolean deleteMethods) {
    super(field.getProject(), true);
    myField = field;
    setModal(true);
    this.deleteMethods = deleteMethods;
    removeMethodsRadioButton = new JRadioButton(RefactorJBundle.message("delete.all.delegating.methods.radio.button"), true);
    dontRemoveMethodsRadioButton = new JRadioButton(RefactorJBundle.message("retain.all.delegating.methods.radio.button"));
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

  public JComponent getPreferredFocusedComponent() {
    return removeMethodsRadioButton;
  }

  protected JComponent createCenterPanel() {
    final JPanel optionsPanel = new JPanel();
    optionsPanel.setBorder(BorderFactory.createEmptyBorder(5, 20, 5, 5));
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
    HelpManager.getInstance().invokeHelp(RefactorJHelpID.RemoveMiddleman);
  }

  protected void doAction() {
    PropertiesComponent.getInstance(getProject()).setValue(RemoveMiddlemanHandler.REMOVE_METHODS, String.valueOf(removeMethods()));
    invokeRefactoring(new RemoveMiddlemanProcessor(myField, removeMethods()));
  }

  protected boolean areButtonsValid() {
    return true;
  }
}