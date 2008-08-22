package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

class WrapReturnValue extends FixableUsageInfo {
    private final PsiReturnStatement statement;
    private final String type;

    WrapReturnValue(PsiReturnStatement statement, String type) {
        super(statement);
        this.type = type;
        this.statement = statement;
    }

    public void fixUsage() throws IncorrectOperationException {
        final PsiExpression returnValue = statement.getReturnValue();
        assert returnValue != null;
        @NonNls final String newExpression =
                "new " + type + '(' + returnValue.getText() + ')';
        MutationUtils.replaceExpression(newExpression, returnValue);
    }
}
