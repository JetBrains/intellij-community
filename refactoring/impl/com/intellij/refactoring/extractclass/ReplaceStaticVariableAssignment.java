package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.base.RefactorJUsageInfo;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.util.IncorrectOperationException;

class ReplaceStaticVariableAssignment extends RefactorJUsageInfo {
    private final PsiReferenceExpression reference;
    private final String setterName;
    private final String getterName;
    private final String originalClassName;
    private final boolean isPublic;

    ReplaceStaticVariableAssignment(PsiReferenceExpression reference,
                                    String originalClassName,
                                    String setterName,
                                    String getterName,
                                    boolean isPublic
                                    ) {
        super(reference);
        this.getterName = getterName;
        this.setterName = setterName;
        this.originalClassName = originalClassName;
        this.reference = reference;
        this.isPublic = isPublic;
    }

    public void fixUsage() throws IncorrectOperationException {
        final PsiAssignmentExpression assignment =
                PsiTreeUtil.getParentOfType(reference, PsiAssignmentExpression.class);
        assert assignment != null;
        final PsiExpression rhs = assignment.getRExpression();
        assert rhs != null;
        final PsiJavaToken sign = assignment.getOperationSign();
        final String operator = sign.getText();
        final String rhsText = rhs.getText();
        if (isPublic) {
            MutationUtils.replaceExpression(originalClassName + '.' + reference.getText(), reference);
        } else {
            final String newExpression;
            if ("=".equals(operator)) {
                newExpression = originalClassName + '.' + setterName + "( " + rhsText + ')';
            } else {
                final String strippedOperator = getStrippedOperator(operator);
                newExpression = originalClassName + '.' + setterName + '(' + originalClassName + '.' + getterName + "()" + strippedOperator + rhsText + ')';
            }
            MutationUtils.replaceExpression(newExpression, assignment);
        }
    }

    private static String getStrippedOperator(String operator) {
        return operator.substring(0, operator.length() - 1);
    }
}
