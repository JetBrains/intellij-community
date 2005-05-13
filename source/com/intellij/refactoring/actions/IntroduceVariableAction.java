
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;

/**
 *
 */
public class IntroduceVariableAction extends BaseRefactoringAction {
  /**
   * @fabrique
   */
  public static final String INTRODUCE_VARIABLE_ACTION_HANDLER = "IntroduceVariableActionHandler";

  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  protected boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  protected RefactoringActionHandler getHandler(DataContext dataContext) {
    final RefactoringActionHandler handler = (RefactoringActionHandler) dataContext.getData(INTRODUCE_VARIABLE_ACTION_HANDLER);
    if (handler != null) {
      return handler;
    }

    return new IntroduceVariableHandler();
  }
}
