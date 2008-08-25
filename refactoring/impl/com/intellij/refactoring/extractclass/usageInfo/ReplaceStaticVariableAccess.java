package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceStaticVariableAccess extends FixableUsageInfo {
    private final PsiReferenceExpression expression;
    private final String delegateClass;
    private final String getterName;
    private final boolean isPublic;

    public ReplaceStaticVariableAccess(PsiReferenceExpression expression,
                                String delegateClass,
                                String getterName,
                                boolean isPublic) {
        super(expression);
        this.expression = expression;
        this.delegateClass = delegateClass;
        this.getterName = getterName;
        this.isPublic = isPublic;
    }

    public void fixUsage() throws IncorrectOperationException {
        if (isPublic) {
            final String newExpression = delegateClass + '.' + expression.getReferenceName();
            MutationUtils.replaceExpression(newExpression, expression);
        } else {

            final String newExpression = delegateClass + '.' + getterName + "()";
            MutationUtils.replaceExpression(newExpression, expression);
        }
    }
}
