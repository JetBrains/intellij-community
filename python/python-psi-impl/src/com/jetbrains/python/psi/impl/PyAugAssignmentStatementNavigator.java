// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PyAugAssignmentStatementNavigator {
  private PyAugAssignmentStatementNavigator() {
  }

  public static @Nullable PyAugAssignmentStatement getStatementByTarget(final PsiElement element) {
    final PyAugAssignmentStatement statement = PsiTreeUtil.getParentOfType(element, PyAugAssignmentStatement.class);
    if (statement == null) {
      return null;
    }
    return statement.getTarget() == element ? statement : null;
  }
}
