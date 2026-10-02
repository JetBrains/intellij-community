// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyGlobalStatement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PyGlobalStatementNavigator {
  private PyGlobalStatementNavigator() {
  }

  public static @Nullable PyGlobalStatement getByArgument(final PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PyGlobalStatement statement) {
      return ArrayUtil.find(statement.getGlobals(), element) != -1 ? statement : null;
    }
    return null;
  }
}
