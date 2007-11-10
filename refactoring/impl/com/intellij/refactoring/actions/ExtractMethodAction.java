
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;

/**
 *
 */
public class ExtractMethodAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    final Language language = DataKeys.LANGUAGE.getData(dataContext);
    if (language != null) {
      return language.getRefactoringSupportProvider().getExtractMethodHandler();
    }

    return null;
  }

  protected boolean isAvailableForLanguage(final Language language) {
    return language.getRefactoringSupportProvider().getExtractMethodHandler() != null;
  }
}
