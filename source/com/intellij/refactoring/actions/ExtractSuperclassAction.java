
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.extractSuperclass.ExtractSuperclassHandler;

public class ExtractSuperclassAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    /*
    if (elements.length == 1) {
      return elements[0] instanceof PsiClass || elements[0] instanceof PsiField || elements[0] instanceof PsiMethod;
    }
    else if (elements.length > 1){
      for (int  idx = 0;  idx < elements.length;  idx++) {
        PsiElement element = elements[idx];
        if (!(element instanceof PsiField || element instanceof PsiMethod)) return false;
      }
      return true;
    }
    return false;
    */
    return elements.length == 1 && elements[0] instanceof PsiClass && !((PsiClass) elements[0]).isInterface();
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new ExtractSuperclassHandler();
  }
}