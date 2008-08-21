package com.intellij.refactoring.removemiddleman;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.base.BaseRefactorJAction;

public class RemoveMiddlemanAction extends BaseRefactorJAction{


    protected boolean isEnabledOnElement(PsiElement element){
        final PsiField field =
                PsiTreeUtil.getParentOfType(element, PsiField.class, false);
        return field != null;
    }

    protected RefactoringActionHandler getHandler(DataContext context) {
        return new RemoveMiddlemanHandler();
    }
}
