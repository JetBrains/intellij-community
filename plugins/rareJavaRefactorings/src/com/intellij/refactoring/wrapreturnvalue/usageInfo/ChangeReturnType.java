// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.wrapreturnvalue.usageInfo;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeElement;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ChangeReturnType extends FixableUsageInfo {
  private final PsiMethod myMethod;
  private final String myType;

  public ChangeReturnType(@NotNull PsiMethod method, @NotNull String type) {
    super(method);
    myMethod = method;
    myType = type;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    PsiTypeElement returnType = myMethod.getReturnTypeElement();
    assert returnType != null : myMethod;
    MutationUtils.replaceType(myType, returnType);
  }
}
