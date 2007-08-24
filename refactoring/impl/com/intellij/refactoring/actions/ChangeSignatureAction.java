
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;

public class ChangeSignatureAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && (elements[0] instanceof PsiMethod || elements[0] instanceof PsiClass);
  }

  protected boolean isAvailableOnElementInEditor(final PsiElement element) {
    return element instanceof PsiMethod || element instanceof PsiClass;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new ChangeSignatureHandler();
  }
}