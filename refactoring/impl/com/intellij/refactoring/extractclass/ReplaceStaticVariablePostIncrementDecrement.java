package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

class ReplaceStaticVariablePostIncrementDecrement extends FixableUsageInfo {
    private final PsiReferenceExpression reference;
    private final String setterName;
    private final String getterName;
    private final String originalClassName;
    private final boolean isPublic;

    ReplaceStaticVariablePostIncrementDecrement(PsiReferenceExpression reference,
                                                String originalClassName,
                                                String setterName,
                                                String getterName,
                                                boolean isPublic) {
        super(reference);
        this.getterName = getterName;
        this.setterName = setterName;
        this.originalClassName = originalClassName;
        this.reference = reference;
        this.isPublic = isPublic;
    }

    public void fixUsage() throws IncorrectOperationException {
        final PsiPostfixExpression expression =
                PsiTreeUtil.getParentOfType(reference, PsiPostfixExpression.class);

        assert expression != null;
        final PsiJavaToken sign = expression.getOperationSign();
        final String operator = sign.getText();
        if (isPublic) {
            MutationUtils.replaceExpression(originalClassName + '.' + reference.getText(), reference);
        } else {
            final String strippedOperator = getStrippedOperator(operator);
            final String newExpression = originalClassName + '.' + setterName + '(' + originalClassName + '.' + getterName + "()" + strippedOperator + "1)";
            MutationUtils.replaceExpression(newExpression, expression);
        }
    }

    private static String getStrippedOperator(String operator) {
        return operator.substring(0, operator.length() - 1);
    }
}
