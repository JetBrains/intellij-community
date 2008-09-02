package com.intellij.refactoring.wrapreturnvalue.usageInfo;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeElement;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ChangeReturnType extends FixableUsageInfo {
    @NotNull
    private final PsiMethod method;
    @NotNull
    private final String type;

    public ChangeReturnType(@NotNull PsiMethod method, @NotNull String type) {
        super(method);
        this.type = type;
        this.method = method;
    }

    public void fixUsage() throws IncorrectOperationException {
        final PsiTypeElement returnType = method.getReturnTypeElement();
        MutationUtils.replaceType(type, returnType);
    }
}
