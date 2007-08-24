
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.tempWithQuery.TempWithQueryHandler;

public class TempWithQueryAction extends BaseRefactoringAction{
  public boolean isAvailableInEditorOnly() {
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new TempWithQueryHandler();
  }

  protected boolean isAvailableOnElementInEditor(final PsiElement element) {
    return element instanceof PsiLocalVariable && ((PsiLocalVariable) element).getInitializer() != null; 
  }
}