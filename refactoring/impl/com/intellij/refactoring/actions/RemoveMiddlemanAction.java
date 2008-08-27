package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.removemiddleman.RemoveMiddlemanHandler;

public class RemoveMiddlemanAction extends BaseRefactoringAction{


  protected RefactoringActionHandler getHandler(DataContext context) {
        return new RemoveMiddlemanHandler();
    }

  public boolean isAvailableInEditorOnly(){
      return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && PsiTreeUtil.getParentOfType(elements[0], PsiField.class, false) != null;
  }
}
