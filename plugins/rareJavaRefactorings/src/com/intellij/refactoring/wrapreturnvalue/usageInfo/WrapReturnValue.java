// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.wrapreturnvalue.usageInfo;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class WrapReturnValue extends FixableUsageInfo {
  private final PsiReturnStatement myStatement;
  private final String myType;

  public WrapReturnValue(@NotNull PsiReturnStatement statement, @NotNull String type) {
    super(statement);
    myStatement = statement;
    myType = type;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    PsiExpression returnValue = myStatement.getReturnValue();
    assert returnValue != null;
    @NonNls String newExpression = "new " + myType + '(' + returnValue.getText() + ')';
    MutationUtils.replaceExpression(newExpression, returnValue);
  }
}
