
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;

public class IntroduceFieldAction extends BaseRefactoringAction {
  protected RefactoringActionHandler getHandler(DataContext dataContext) {
    return new IntroduceFieldHandler();
  }

  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  protected boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }
}
