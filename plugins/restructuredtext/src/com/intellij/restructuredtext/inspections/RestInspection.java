// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.restructuredtext.RestBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public abstract class RestInspection extends LocalInspectionTool {
  @Override
  public @Nls @NotNull String getGroupDisplayName() {
    return RestBundle.message("INSP.GROUP.rest");
  }

  @Override
  public @NotNull String getShortName() {
    return getClass().getSimpleName();
  }

  @Override
  public SuppressQuickFix @NotNull [] getBatchSuppressActions(@Nullable PsiElement element) {
    return SuppressQuickFix.EMPTY_ARRAY;
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    return false;
  }
}
