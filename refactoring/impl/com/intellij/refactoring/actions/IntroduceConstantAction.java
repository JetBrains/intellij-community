
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;

public class IntroduceConstantAction extends BaseRefactoringAction {

  protected RefactoringActionHandler getHandler(DataContext dataContext) {
    final Language language = LangDataKeys.LANGUAGE.getData(dataContext);
    if (language != null) {
      return LanguageRefactoringSupport.INSTANCE.forLanguage(language).getIntroduceConstantHandler();
    }

    return null;
  }

  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  protected boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  protected boolean isAvailableForLanguage(Language language) {
    return LanguageRefactoringSupport.INSTANCE.forLanguage(language).getIntroduceConstantHandler() != null;
  }
}
