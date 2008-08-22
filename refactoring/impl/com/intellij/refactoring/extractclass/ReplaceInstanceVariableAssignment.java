package com.intellij.refactoring.extractclass;

import com.intellij.psi.*;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

class ReplaceInstanceVariableAssignment extends FixableUsageInfo {
    private final String setterName;
    private final PsiAssignmentExpression assignment;
    private final String getterName;
    private final String delegateName;

    ReplaceInstanceVariableAssignment(PsiAssignmentExpression assignment,
                                      String delegateName,
                                      String setterName,
                                      String getterName) {
        super(assignment);
        this.assignment = assignment;
        this.getterName = getterName;
        this.setterName = setterName;
        this.delegateName = delegateName;
    }

    public void fixUsage() throws IncorrectOperationException {
        final PsiReferenceExpression lhs =
                (PsiReferenceExpression) assignment.getLExpression();
        final PsiExpression rhs = assignment.getRExpression();
        assert rhs != null;
        final PsiElement qualifier = lhs.getQualifier();
        final PsiJavaToken sign = assignment.getOperationSign();
        final String operator = sign.getText();
        final String rhsText = rhs.getText();
        final String newExpression;
        if (qualifier != null) {
            final String qualifierText = qualifier.getText();
            if ("=".equals(operator)) {
                newExpression = qualifierText + '.' + delegateName + '.' + setterName + "( " + rhsText + ')';
            } else {
                final String strippedOperator = getStrippedOperator(operator);
                newExpression = qualifierText + '.'+delegateName + '.' + setterName + '(' + qualifierText + '.'+delegateName + '.' + getterName + "()" + strippedOperator + rhsText + ')';
            }
        } else {
            if ("=".equals(operator)) {
                newExpression = delegateName + '.' + setterName + "( " + rhsText + ')';
            } else {
                final String strippedOperator = getStrippedOperator(operator);
                newExpression = delegateName + '.' + setterName + '(' + delegateName + '.' + getterName + "()" + strippedOperator + rhsText + ')';
            }
        }
        MutationUtils.replaceExpression(newExpression, assignment);
    }

    private static String getStrippedOperator(String operator) {
        return operator.substring(0, operator.length() - 1);
    }
}
