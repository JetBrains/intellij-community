// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public class PyResolveResultRaterBase implements PyResolveResultRater {

  @Override
  public int getImportElementRate(@NotNull final PsiElement target) {
    return 0;
  }

  @Override
  public int getMemberRate(PsiElement member, PyType type, TypeEvalContext context) {
    return 0;
  }
}
