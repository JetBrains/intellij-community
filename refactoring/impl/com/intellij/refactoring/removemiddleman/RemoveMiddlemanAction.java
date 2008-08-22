package com.intellij.refactoring.removemiddleman;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;

public class RemoveMiddlemanAction extends BaseRefactoringAction{


  protected RefactoringActionHandler getHandler(DataContext context) {
        return new RemoveMiddlemanHandler();
    }

  public boolean isAvailableInEditorOnly(){
      return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
      if (elements.length != 1) {
          return false;
      }
      final PsiElement element = elements[0];
    final PsiField field =
            PsiTreeUtil.getParentOfType(element, PsiField.class, false);
    return field != null;
  }
}
