package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.base.BaseRefactorJAction;

public class WrapReturnValueAction extends BaseRefactorJAction{

    protected boolean isEnabledOnElement(PsiElement element){
        final PsiMethod containingMethod =
                PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
        return containingMethod != null;
    }

    protected RefactoringActionHandler getHandler(DataContext context){
        return new WrapReturnValueHandler();
    }
}
