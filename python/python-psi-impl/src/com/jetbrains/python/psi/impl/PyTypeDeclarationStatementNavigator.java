// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.psi.PyUtil.as;

@ApiStatus.Internal
public final class PyTypeDeclarationStatementNavigator {
  private PyTypeDeclarationStatementNavigator() {
  }

  public static boolean isTypeDeclarationTarget(@NotNull PsiElement element) {
    return getStatementByTarget(element) != null;
  }

  public static @Nullable PyTypeDeclarationStatement getStatementByTarget(@NotNull PsiElement element) {
    final PyTypeDeclarationStatement statement = as(element.getParent(), PyTypeDeclarationStatement.class);
    return statement != null && statement.getTarget() == element ? statement : null;
  }
}
