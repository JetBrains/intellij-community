
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;

/**
 *
 */
public class IntroduceVariableAction extends BaseRefactoringAction {
  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  protected boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  protected RefactoringActionHandler getHandler(DataContext dataContext) {
    return new IntroduceVariableHandler();
  }
}
