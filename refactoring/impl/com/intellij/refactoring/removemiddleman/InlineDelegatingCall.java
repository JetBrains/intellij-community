package com.intellij.refactoring.removemiddleman;

import com.intellij.psi.*;
import com.intellij.refactoring.base.RefactorJUsageInfo;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.util.IncorrectOperationException;

class InlineDelegatingCall extends RefactorJUsageInfo {
    private final PsiMethodCallExpression expression;
    private final String getterName;
    private final String delegatingName;
    private final int[] paramaterPermutation;

    InlineDelegatingCall(PsiMethodCallExpression expression,
                         int[] paramaterPermutation,
                         String getterName,
                         String delegatingName) {
        super(expression);
        this.expression = expression;
        this.paramaterPermutation = paramaterPermutation;
        this.getterName = getterName;
        this.delegatingName = delegatingName;
    }

    public void fixUsage() throws IncorrectOperationException {
        final int length = expression.getTextLength() + getterName.length();
        final StringBuffer replacementText = new StringBuffer(length);
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final PsiElement qualifier = methodExpression.getQualifier();
        if (qualifier != null) {
            final String qualifierText = qualifier.getText();
            replacementText.append(qualifierText + '.');
        }
        replacementText.append(getterName + "().");
        replacementText.append(delegatingName + '(');
        final PsiExpressionList argumentList = expression.getArgumentList();
        assert argumentList != null;
        final PsiExpression[] args = argumentList.getExpressions();
        boolean first = true;
        for (int i : paramaterPermutation) {
            if (!first) {
                replacementText.append(", ");
            }
            first = false;
            final String argText = args[i].getText();
            replacementText.append(argText);
        }
        replacementText.append(')');
        final String replacementTextString = replacementText.toString();
        MutationUtils.replaceExpression(replacementTextString, expression);
    }
}
