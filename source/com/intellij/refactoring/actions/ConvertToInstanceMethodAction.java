package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodHandler;

/**
 * @author dsl
 */
public class ConvertToInstanceMethodAction extends BaseRefactoringAction {
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && elements[0] instanceof PsiMethod;
  }

  protected RefactoringActionHandler getHandler(DataContext dataContext) {
    return new ConvertToInstanceMethodHandler();
  }
}
