/**
 * class InlineAction
 * created Aug 28, 2001
 * @author Jeka
 */
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.inline.InlineHandler;

public class InlineAction extends BaseRefactoringAction {

  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 &&
           (elements[0] instanceof PsiMethod ||
            elements[0] instanceof PsiField ||
            elements[0] instanceof PsiClass);
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new InlineHandler();
  }

  protected boolean isAvailableForLanguage(Language language) {
    return language instanceof JavaLanguage ||
           language.equals(StdFileTypes.JSPX.getLanguage()) ||
           language.equals(StdFileTypes.JSP.getLanguage()) ||
           language.getRefactoringSupportProvider().getInlineHandler() != null;
  }
}
