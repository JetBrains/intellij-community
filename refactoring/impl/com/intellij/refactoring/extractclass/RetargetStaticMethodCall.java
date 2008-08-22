package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

class RetargetStaticMethodCall extends FixableUsageInfo {
    private final String delegateClassName;
    private final PsiMethodCallExpression call;

     RetargetStaticMethodCall(PsiMethodCallExpression call, String delegateClassName) {
        super(call);
        this.call = call;
        this.delegateClassName = delegateClassName;
    }

    public void fixUsage() throws IncorrectOperationException {
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        final PsiExpression qualifier = (PsiExpression) methodExpression.getQualifier();
        if (qualifier == null) {
            MutationUtils.replaceExpression(delegateClassName + '.' + call.getText(), call);
        } else {
            final PsiExpressionList parameterList = call.getArgumentList();
            MutationUtils.replaceExpression(delegateClassName + '.' + methodExpression.getReferenceName()  +parameterList.getText() , call);
        }
    }
}
