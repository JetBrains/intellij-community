package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.inheritanceToDelegation.InheritanceToDelegationHandler;

public class InheritanceToDelegationAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && elements[0] instanceof PsiClass && !((PsiClass) elements[0]).isInterface();
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new InheritanceToDelegationHandler();
  }
}