package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodDialog;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.openapi.diagnostic.Logger;

/**
 * @author ven
 */
public class ConvertToInstanceMethodDialog  extends MoveInstanceMethodDialog {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodDialog");
  public ConvertToInstanceMethodDialog(final PsiMethod method, final PsiVariable[] variables, final String refactoringName) {
    super(method, variables, refactoringName);
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
}
