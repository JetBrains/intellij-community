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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.makeMethodStatic.MakeMethodStaticHandler;

public class MakeMethodStaticAction extends BaseRefactoringAction {
    protected boolean isAvailableInEditorOnly() {
        return false;
    }

    protected boolean isEnabledOnElements(PsiElement[] elements) {
        return (elements.length == 1) && (elements[0] instanceof PsiMethod) &&
                !((PsiMethod)elements[0]).isConstructor();
    }

    protected RefactoringActionHandler getHandler(DataContext dataContext) {
        return new MakeMethodStaticHandler();
    }
}
