package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodDialogBase;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.HelpID;

import javax.swing.*;

/**
 * @author ven
 */
public class ConvertToInstanceMethodDialog  extends MoveInstanceMethodDialogBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodDialog");
  public ConvertToInstanceMethodDialog(final PsiMethod method, final PsiParameter[] variables) {
    super(method, variables, ConvertToInstanceMethodHandler.REFACTORING_NAME);
  }

  protected void doAction() {
    final PsiVariable targetVariable = (PsiVariable)myList.getSelectedValue();
    LOG.assertTrue(targetVariable instanceof PsiParameter);
    final ConvertToInstanceMethodProcessor processor = new ConvertToInstanceMethodProcessor(myMethod.getProject(),
                                                                                            myMethod, (PsiParameter)targetVariable,
                                                                                            myVisibilityPanel.getVisibility());
    if (!verifyTargetClass(processor.getTargetClass())) return;
    invokeRefactoring(processor);
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.CONVERT_TO_INSTANCE_METHOD);
  }

  protected JComponent createCenterPanel() {
    final Box vBox = Box.createVerticalBox();
    final Box labelBox = Box.createHorizontalBox();
    final JLabel label = new JLabel();
    labelBox.add(label);
    labelBox.add(Box.createHorizontalGlue());
    vBox.add(labelBox);
    vBox.add(Box.createVerticalStrut(4));

    vBox.add(createListAndVisibilityPanels());
    label.setText(RefactoringBundle.message("moveInstanceMethod.select.an.instance.parameter"));
    return vBox;
  }
}
