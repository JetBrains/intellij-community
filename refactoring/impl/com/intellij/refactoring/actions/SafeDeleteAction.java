
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;

public class SafeDeleteAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isAvailableForLanguage(Language language) {
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!SafeDeleteProcessor.validElement(element)) return false;
    }
    return true;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new SafeDeleteHandler();
  }

}