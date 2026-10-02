// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyForStatement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PyForStatementNavigator {
  private PyForStatementNavigator() {
  }

  public static @Nullable PyForStatement getPyForStatementByIterable(final PsiElement element) {
    final PyForStatement forStatement = PsiTreeUtil.getParentOfType(element, PyForStatement.class, false);
    if (forStatement == null) {
      return null;
    }
    final PyExpression target = forStatement.getForPart().getTarget();
    if (target != null && PsiTreeUtil.isAncestor(target, element, false)) {
      return forStatement;
    }
    return null;
  }

  public static @Nullable Object getPyForStatementByBody(final PsiElement element) {
    final PyForStatement forStatement = PsiTreeUtil.getParentOfType(element, PyForStatement.class, false);
    if (forStatement == null) {
      return null;
    }
    final PyForPart forPart = forStatement.getForPart();
    return forPart == element || forPart.getStatementList() == element ? forStatement : null;
  }
}
