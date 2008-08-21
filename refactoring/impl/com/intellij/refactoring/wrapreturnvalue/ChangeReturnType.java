package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeElement;
import com.intellij.refactoring.base.RefactorJUsageInfo;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

class ChangeReturnType extends RefactorJUsageInfo {
    @NotNull
    private final PsiMethod method;
    @NotNull
    private final String type;

    ChangeReturnType(@NotNull PsiMethod method, @NotNull String type) {
        super(method);
        this.type = type;
        this.method = method;
    }

    public void fixUsage() throws IncorrectOperationException {
        final PsiTypeElement returnType = method.getReturnTypeElement();
        MutationUtils.replaceType(type, returnType);
    }
}
