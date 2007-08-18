
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;

/**
 *
 */
public class IntroduceVariableAction extends BaseRefactoringAction {
  public IntroduceVariableAction() {
    setInjectedContext(true);
  }

  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  protected boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  protected RefactoringActionHandler getHandler(DataContext dataContext) {
    final Language language = DataKeys.LANGUAGE.getData(dataContext);
    if (language != null) {
      return language.getRefactoringSupportProvider().getIntroduceVariableHandler();
    }

    return null;
    
  }

  protected boolean isAvailableForLanguage(Language language) {
    return language.getRefactoringSupportProvider().getIntroduceVariableHandler() != null;
  }
}
