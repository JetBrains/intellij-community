// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public interface PyResolveResultRater {
  ExtensionPointName<PyResolveResultRater> EP_NAME = ExtensionPointName.create("Pythonid.resolveResultRater");

  int getImportElementRate(final @NotNull PsiElement target);

  int getMemberRate(PsiElement member, PyType type, TypeEvalContext context);
}
