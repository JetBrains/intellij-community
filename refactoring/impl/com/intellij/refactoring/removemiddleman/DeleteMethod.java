package com.intellij.refactoring.removemiddleman;

import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

class DeleteMethod extends FixableUsageInfo {
    private final PsiMethod method;

    DeleteMethod(PsiMethod method) {
        super(method);
        this.method = method;
    }

    public void fixUsage() throws IncorrectOperationException {
        method.delete();
    }
}
