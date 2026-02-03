// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public class PyResolveResultRaterBase implements PyResolveResultRater {

  @Override
  public int getImportElementRate(final @NotNull PsiElement target) {
    return 0;
  }

  @Override
  public int getMemberRate(PsiElement member, PyType type, TypeEvalContext context) {
    return 0;
  }
}
