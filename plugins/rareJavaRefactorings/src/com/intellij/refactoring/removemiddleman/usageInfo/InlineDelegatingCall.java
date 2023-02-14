// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.removemiddleman.usageInfo;

import com.intellij.psi.*;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class InlineDelegatingCall extends FixableUsageInfo {
  private final PsiMethodCallExpression expression;
  private final String myAccess;
  private final String delegatingName;
  private final int[] paramaterPermutation;

  public InlineDelegatingCall(PsiMethodCallExpression expression,
                              int[] paramaterPermutation,
                              String access,
                              String delegatingName) {
    super(expression);
    this.expression = expression;
    this.paramaterPermutation = paramaterPermutation;
    myAccess = access;
    this.delegatingName = delegatingName;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    final StringBuilder replacementText = new StringBuilder();
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiElement qualifier = methodExpression.getQualifier();
    if (qualifier != null) {
      final String qualifierText = qualifier.getText();
      replacementText.append(qualifierText + '.');
    }
    replacementText.append(myAccess).append(".");
    replacementText.append(delegatingName).append('(');
    final PsiExpressionList argumentList = expression.getArgumentList();
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
