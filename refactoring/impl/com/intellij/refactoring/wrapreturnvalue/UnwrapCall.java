package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.psi.PsiCallExpression;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class UnwrapCall extends FixableUsageInfo {
    @NotNull
    private final PsiCallExpression call;
    @NotNull
    private final String unwrapMethod;

    UnwrapCall(@NotNull PsiCallExpression call, @NotNull String unwrapMethod) {
        super(call);
        this.call =call;
        this.unwrapMethod = unwrapMethod;
    }

    public void fixUsage() throws IncorrectOperationException {
        @NonNls final String newExpression = call.getText() + '.' + unwrapMethod +"()";
        MutationUtils.replaceExpression(newExpression, call);
    }
}
