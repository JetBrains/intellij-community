
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsHandler;

public class EncapsulateFieldsAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    if (elements.length == 1) {
      return elements[0] instanceof PsiClass || isAcceptedField(elements[0]);
    }
    else if (elements.length > 1){
      for (int  idx = 0;  idx < elements.length;  idx++) {
        if (!isAcceptedField(elements[idx])) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new EncapsulateFieldsHandler();
  }

  private static boolean isAcceptedField(PsiElement element) {
    if (element instanceof PsiField) {
      if (((PsiField)element).getContainingClass() != null) {
        return true;
      }
    }
    return false;
  }
}