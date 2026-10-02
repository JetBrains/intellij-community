// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PyExceptPartNavigator {
  private PyExceptPartNavigator() {
  }

  public static @Nullable PyExceptPart getPyExceptPartByTarget(final PsiElement element) {
    final PyExceptPart pyExceptPart = PsiTreeUtil.getParentOfType(element, PyExceptPart.class, false);
    if (pyExceptPart == null) {
      return null;
    }
    final PyExpression expr = pyExceptPart.getTarget();
    if (expr != null && PsiTreeUtil.isAncestor(expr, element, false)) {
      return pyExceptPart;
    }
    return null;
  }
}
