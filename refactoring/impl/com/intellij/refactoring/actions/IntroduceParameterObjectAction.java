package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.introduceparameterobject.IntroduceParameterObjectHandler;

public class IntroduceParameterObjectAction extends BaseRefactoringAction {

  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isEnabledOnElements(final PsiElement[] elements) {
    return elements.length == 1 && PsiTreeUtil.getParentOfType(elements[0], PsiMethod.class, false) != null;
  }

  protected RefactoringActionHandler getHandler(DataContext context) {
    return new IntroduceParameterObjectHandler();
  }
}
