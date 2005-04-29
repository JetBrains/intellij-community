
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.jsp.extractInclude.ExtractIncludeFileHandler;

/**
 *
 */
public class ExtractJspIncludeAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  protected boolean isAvailableForLanguage(Language language) {
    return language.equals(StdFileTypes.JSP.getLanguage()) ||
           language.equals(StdFileTypes.JSPX.getLanguage());
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new ExtractIncludeFileHandler();
  }
}
