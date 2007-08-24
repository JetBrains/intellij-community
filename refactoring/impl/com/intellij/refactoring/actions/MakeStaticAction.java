/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: Apr 15, 2002
 * Time: 1:32:20 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.makeStatic.MakeStaticHandler;

public class MakeStaticAction extends BaseRefactoringAction {
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isEnabledOnElements(PsiElement[] elements) {
    return (elements.length == 1) && (elements[0] instanceof PsiMethod) && !((PsiMethod)elements[0]).isConstructor();
  }

  protected boolean isAvailableOnElementInEditor(PsiElement element) {
    if (element instanceof PsiIdentifier) {
      element = element.getParent();
    }
    return element instanceof PsiTypeParameterListOwner &&
           MakeStaticHandler.validateTarget((PsiTypeParameterListOwner) element) == null;
  }

  protected RefactoringActionHandler getHandler(DataContext dataContext) {
    return new MakeStaticHandler();
  }
}
