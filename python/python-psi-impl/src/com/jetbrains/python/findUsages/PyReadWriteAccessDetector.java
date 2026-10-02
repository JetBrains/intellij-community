// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.findUsages;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyDelStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public final class PyReadWriteAccessDetector extends ReadWriteAccessDetector {
  @Override
  public boolean isReadWriteAccessible(@NotNull PsiElement element) {
    return element instanceof PyTargetExpression || element instanceof PyReferenceExpression;
  }

  @Override
  public boolean isDeclarationWriteAccess(@NotNull PsiElement element) {
    return element instanceof PyTargetExpression || element.getParent() instanceof PyDelStatement;
  }

  @Override
  public @NotNull Access getReferenceAccess(@NotNull PsiElement referencedElement, @NotNull PsiReference reference) {
    return getExpressionAccess(reference.getElement());
  }

  @Override
  public @NotNull Access getExpressionAccess(@NotNull PsiElement expression) {
    if (isDeclarationWriteAccess(expression)) {
      return Access.Write;
    }
    if (expression instanceof PyReferenceExpression) {
      final PyAugAssignmentStatement statement = PyAugAssignmentStatementNavigator.getStatementByTarget(expression);
      if (statement != null) {
        return Access.ReadWrite;
      }
    }
    return Access.Read;
  }
}
