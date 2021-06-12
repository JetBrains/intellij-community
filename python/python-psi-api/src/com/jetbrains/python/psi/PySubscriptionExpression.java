// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public interface PySubscriptionExpression extends PyQualifiedExpression, PyCallSiteExpression, PyReferenceOwner {

  @Nullable
  @Override
  default PyExpression getReceiver(@Nullable PyCallable resolvedCallee) {
    return getOperand();
  }

  @NotNull
  @Override
  default List<PyExpression> getArguments(@Nullable PyCallable resolvedCallee) {
    if (AccessDirection.of(this) == AccessDirection.WRITE) {
      final PsiElement parent = getParent();
      if (parent instanceof PyAssignmentStatement) {
        return Arrays.asList(getIndexExpression(), ((PyAssignmentStatement)parent).getAssignedValue());
      }
    }
    return Collections.singletonList(getIndexExpression());
  }

  /**
   * @return For {@code spam[x][y][n]} will return {@code spam} regardless number of its dimensions
   */
  @NotNull
  PyExpression getRootOperand();

  @NotNull
  PyExpression getOperand();

  @Nullable
  PyExpression getIndexExpression();
}
