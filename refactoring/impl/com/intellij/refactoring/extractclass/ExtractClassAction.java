package com.intellij.refactoring.extractclass;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;

public class ExtractClassAction extends BaseRefactoringAction{

  protected RefactoringActionHandler getHandler(DataContext context){
        return new ExtractClassHandler();
    }

  public boolean isAvailableInEditorOnly(){
      return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
      if (elements.length != 1) {
          return false;
      }
      final PsiElement element = elements[0];
    final PsiClass containingClass =
            PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    return containingClass != null;
  }
}
