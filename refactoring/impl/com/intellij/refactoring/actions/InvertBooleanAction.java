package com.intellij.refactoring.actions;

import org.jetbrains.annotations.Nullable;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.invertBoolean.InvertBooleanHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;

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

  protected
  @Nullable
  RefactoringActionHandler getHandler(DataContext dataContext) {
    return new InvertBooleanHandler();
  }
}
