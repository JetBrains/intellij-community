package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import java.awt.*;

/**
 * @author ven
 */
public class MoveInstanceMethodDialog extends MoveInstanceMethodDialogBase {
  private static final String KEY = "#com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodDialog";
  private EditorTextField myOldClassParameterNameField;

  public MoveInstanceMethodDialog(final PsiMethod method,
                                  final PsiVariable[] variables) {
    super(method, variables, MoveInstanceMethodHandler.REFACTORING_NAME);
  }

  protected String getDimensionServiceKey() {
    return KEY;
  }

  protected JComponent createCenterPanel() {
    JPanel mainPanel = new JPanel(new GridBagLayout());
    final JLabel jLabel = new JLabel("Select an instance variable:");
    jLabel.setDisplayedMnemonic('i');
    mainPanel.add(jLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0,0));

    myList = createTargetVariableChooser();
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList);
    mainPanel.add(scrollPane, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(0,0,0,0), 0,0));

    myVisibilityPanel = createVisibilityPanel();
    mainPanel.add(myVisibilityPanel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.VERTICAL, new Insets(0,0,0,0), 0,0));

    final String text = "Select a name for old class parameter";
    final JLabel jLabel1 = new JLabel(text);
    jLabel1.setDisplayedMnemonic('n');
    mainPanel.add(jLabel1, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(8,0,0,0), 0,0));

    String suggestedName = MoveInstanceMethodHandler.suggestParameterNameForThisClass(myMethod.getContainingClass());
    myOldClassParameterNameField = new EditorTextField(suggestedName, getProject(), StdFileTypes.JAVA);
    mainPanel.add(myOldClassParameterNameField, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0,0));

    jLabel.setLabelFor(myList);
    jLabel1.setLabelFor(myOldClassParameterNameField);
    return mainPanel;
  }

  protected void doAction() {
    final PsiVariable targetVariable = (PsiVariable)myList.getSelectedValue();
    final String parameterName = myOldClassParameterNameField.getText().trim();
    if (!myMethod.getManager().getNameHelper().isIdentifier(parameterName)) {
      Messages.showErrorDialog(getProject(), "Please Enter a Valid name for Parameter", myRefactoringName);
      return;
    }

    final MoveInstanceMethodProcessor processor = new MoveInstanceMethodProcessor(myMethod.getProject(),
                                                                                            myMethod, targetVariable,
                                                                                            myVisibilityPanel.getVisibility(),
                                                                                  parameterName);
    if (!verifyTargetClass(processor.getTargetClass())) return;
    invokeRefactoring(processor);
  }

}
