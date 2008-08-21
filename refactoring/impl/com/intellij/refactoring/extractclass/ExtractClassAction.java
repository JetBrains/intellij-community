package com.intellij.refactoring.extractclass;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.base.BaseRefactorJAction;

public class ExtractClassAction extends BaseRefactorJAction{

    protected boolean isEnabledOnElement(PsiElement element){
        final PsiClass containingClass =
                PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
        return containingClass != null;
    }

    protected RefactoringActionHandler getHandler(DataContext context){
        return new ExtractClassHandler();
    }
}
