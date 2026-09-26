// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.wrapreturnvalue.usageInfo;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class UnwrapCall extends FixableUsageInfo {
  private final String myUnwrapMethod;

  public UnwrapCall(@NotNull PsiExpression call, @NotNull String unwrapMethod) {
    super(call);
    myUnwrapMethod = unwrapMethod;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    PsiElement element = getElement();
    if (!(element instanceof PsiExpression)) return;
    if (element instanceof PsiMethodReferenceExpression) {
      PsiExpression expression = LambdaRefactoringUtil.convertToMethodCallInLambdaBody((PsiMethodReferenceExpression)element);
      if (expression == null) return;
      element = expression;
    }
    String newExpression = element.getText() + '.' + myUnwrapMethod + "()";
    MutationUtils.replaceExpression(newExpression, (PsiExpression)element);
  }
}
