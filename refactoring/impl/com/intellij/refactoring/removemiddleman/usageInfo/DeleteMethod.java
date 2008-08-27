package com.intellij.refactoring.removemiddleman.usageInfo;

import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class DeleteMethod extends FixableUsageInfo {
    private final PsiMethod method;

    public DeleteMethod(PsiMethod method) {
        super(method);
        this.method = method;
    }

    public void fixUsage() throws IncorrectOperationException {
        method.delete();
    }
}
