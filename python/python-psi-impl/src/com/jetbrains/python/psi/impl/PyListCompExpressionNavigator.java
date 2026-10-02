// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListCompExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PyListCompExpressionNavigator {
  private PyListCompExpressionNavigator() {
  }

  public static @Nullable PyListCompExpression getPyListCompExpressionByVariable(final PsiElement element) {
    final PyListCompExpression listCompExpression = PsiTreeUtil.getParentOfType(element, PyListCompExpression.class, false);
    if (listCompExpression == null) {
      return null;
    }
    for (PyComprehensionForComponent component : listCompExpression.getForComponents()) {
      final PyExpression variable = component.getIteratorVariable();
      if (variable != null && PsiTreeUtil.isAncestor(variable, element, false)) {
        return listCompExpression;
      }
    }
    return null;
  }
}
