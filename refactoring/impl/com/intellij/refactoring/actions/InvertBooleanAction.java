package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.invertBoolean.InvertBooleanHandler;

/**
 * @author ven
 */
public class InvertBooleanAction extends BaseRefactoringAction {
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && (elements[0] instanceof PsiMethod || elements[0] instanceof PsiVariable);
  }

  protected boolean isAvailableOnElementInEditor(final PsiElement element) {
    if (element instanceof PsiVariable) {
      return PsiType.BOOLEAN.equals(((PsiVariable) element).getType());
    }
    else if (element instanceof PsiMethod) {
      return PsiType.BOOLEAN.equals(((PsiMethod) element).getReturnType());
    }
    return false;
  }

  protected RefactoringActionHandler getHandler(DataContext dataContext) {
    return new InvertBooleanHandler();
  }
}
