/**
 * class InlineAction
 * created Aug 28, 2001
 * @author Jeka
 */
package com.intellij.refactoring.actions;

import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.inline.InlineHandler;

public class InlineAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 &&
           (elements[0] instanceof PsiMethod || elements[0] instanceof PsiPointcutDef);
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new InlineHandler();
  }
}
