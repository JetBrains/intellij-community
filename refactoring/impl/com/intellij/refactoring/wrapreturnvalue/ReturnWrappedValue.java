package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.refactoring.base.RefactorJUsageInfo;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

class ReturnWrappedValue extends RefactorJUsageInfo{
    private final PsiReturnStatement statement;

    ReturnWrappedValue(PsiReturnStatement statement) {
        super(statement);
        this.statement = statement;
    }

    public void fixUsage() throws IncorrectOperationException{
        final PsiMethodCallExpression returnValue =
                (PsiMethodCallExpression) statement.getReturnValue();
        assert returnValue != null;
        final PsiExpression qualifier =
                returnValue.getMethodExpression().getQualifierExpression();
        assert qualifier != null;
        @NonNls final String newExpression = qualifier.getText();
        MutationUtils.replaceExpression(newExpression, returnValue);
    }
}
