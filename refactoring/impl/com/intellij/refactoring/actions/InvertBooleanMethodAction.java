package com.intellij.refactoring.actions;

import org.jetbrains.annotations.Nullable;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.invertBooleanMethod.InvertBooleanMethodHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

/**
 * @author ven
 */
public class InvertBooleanMethodAction extends BaseRefactoringAction {
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && elements[0] instanceof PsiMethod;
  }

  protected
  @Nullable
  RefactoringActionHandler getHandler(DataContext dataContext) {
    return new InvertBooleanMethodHandler();
  }
}
