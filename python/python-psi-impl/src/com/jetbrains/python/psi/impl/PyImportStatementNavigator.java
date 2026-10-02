// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatementBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PyImportStatementNavigator {
  private PyImportStatementNavigator() {
  }

  public static @Nullable PyImportStatementBase getImportStatementByElement(final PsiElement element) {
    final PyImportStatementBase statement = PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class, false);
    if (statement == null) {
      return null;
    }
    for (PyImportElement importElement : statement.getImportElements()) {
      if (element == importElement || element == importElement.getImportReferenceExpression()) {
        return statement;
      }
    }
    return null;
  }
}
