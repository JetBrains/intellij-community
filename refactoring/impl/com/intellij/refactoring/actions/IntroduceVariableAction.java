
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
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
    final Language language = (Language)dataContext.getData(DataConstants.LANGUAGE);
    if (language != null) {
      return language.getRefactoringSupportProvider().getIntroduceVariableHandler();
    }

    return null;
    
  }

  protected boolean isAvailableForLanguage(Language language) {
    return language.getRefactoringSupportProvider().getIntroduceVariableHandler() != null;
  }
}
