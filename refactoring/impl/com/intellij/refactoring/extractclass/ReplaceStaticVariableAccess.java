package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.base.RefactorJUsageInfo;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.util.IncorrectOperationException;

class ReplaceStaticVariableAccess extends RefactorJUsageInfo {
    private final PsiReferenceExpression expression;
    private final String delegateClass;
    private final String getterName;
    private final boolean isPublic;

    ReplaceStaticVariableAccess(PsiReferenceExpression expression,
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
