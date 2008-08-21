package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.base.RefactorJUsageInfo;
import com.intellij.util.IncorrectOperationException;

class RemoveMethod extends RefactorJUsageInfo {
    private final PsiMethod method;

    RemoveMethod(PsiMethod method) {
        super(method);
        this.method = method;
    }

    public void fixUsage() throws IncorrectOperationException {
        method.delete();
    }
}
