package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

class ReplaceInstanceVariablePreIncrementDecrement extends FixableUsageInfo {
    private final PsiReferenceExpression reference;
    private final String setterName;
    private final String getterName;
    private final String delegateName;

    ReplaceInstanceVariablePreIncrementDecrement(PsiReferenceExpression reference,
                                                 String delegateName,
                                                 String setterName, String getterName) {
        super(reference);
        this.getterName = getterName;
        this.setterName = setterName;
        this.delegateName = delegateName;
        this.reference = reference;
    }

    public void fixUsage() throws IncorrectOperationException {
        final PsiPrefixExpression expression =
                PsiTreeUtil.getParentOfType(reference, PsiPrefixExpression.class);

        assert expression != null;
        final PsiReferenceExpression lhs =
                (PsiReferenceExpression) expression.getOperand();
        final PsiElement qualifier = lhs.getQualifier();
        final PsiJavaToken sign = expression.getOperationSign();
        final String operator = sign.getText();
        final String newExpression;
        if (qualifier != null) {
            final String qualifierText = qualifier.getText();
            final String strippedOperator = getStrippedOperator(operator);
            newExpression = qualifierText + '.' + delegateName + '.' + setterName + '(' + qualifierText + '.' + delegateName + '.' + getterName + "()" + strippedOperator + "1)";
        } else {
            final String strippedOperator = getStrippedOperator(operator);
            newExpression = delegateName + '.' + setterName + '(' + delegateName + '.' + getterName + "()" + strippedOperator + "1)";
        }
        MutationUtils.replaceExpression(newExpression, expression);
    }

    private static String getStrippedOperator(String operator) {
        return operator.substring(0, operator.length() - 1);
    }
}
