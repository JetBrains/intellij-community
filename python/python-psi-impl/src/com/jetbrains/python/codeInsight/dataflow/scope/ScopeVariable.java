// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.dataflow.scope;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
public interface ScopeVariable {
  @NotNull
  String getName();

  @NotNull
  Collection<PsiElement> getDeclarations();

  boolean isParameter();
}
