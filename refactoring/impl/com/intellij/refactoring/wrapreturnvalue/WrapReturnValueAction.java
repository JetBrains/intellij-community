package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;

public class WrapReturnValueAction extends BaseRefactoringAction{

  protected RefactoringActionHandler getHandler(DataContext context){
        return new WrapReturnValueHandler();
    }

  public boolean isAvailableInEditorOnly(){
      return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
      if (elements.length != 1) {
          return false;
      }
      final PsiElement element = elements[0];
    final PsiMethod containingMethod =
            PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    return containingMethod != null;
  }
}
