// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.wrapreturnvalue.usageInfo;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReturnWrappedValue extends FixableUsageInfo {
  private final PsiReturnStatement myStatement;

  public ReturnWrappedValue(PsiReturnStatement statement) {
    super(statement);
    myStatement = statement;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    PsiMethodCallExpression returnValue = (PsiMethodCallExpression)myStatement.getReturnValue();
    assert returnValue != null;
    PsiExpression qualifier = returnValue.getMethodExpression().getQualifierExpression();
    assert qualifier != null;
    MutationUtils.replaceExpression(qualifier.getText(), returnValue);
  }
}
