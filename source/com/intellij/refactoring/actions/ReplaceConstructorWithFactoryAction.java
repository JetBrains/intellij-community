package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryHandler;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryAction extends BaseRefactoringAction {
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 &&
            ((elements[0] instanceof PsiMethod && ((PsiMethod) elements[0]).isConstructor()) ||
            (elements[0] instanceof PsiClass));
  }

  protected RefactoringActionHandler getHandler(DataContext dataContext) {
    return new ReplaceConstructorWithFactoryHandler();
  }
}
