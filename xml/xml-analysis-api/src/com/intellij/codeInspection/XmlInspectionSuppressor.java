// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlInspectionSuppressor implements InspectionSuppressor{
  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
    return XmlSuppressionProvider.isSuppressed(element, toolId);
  }

  @Override
  public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    return XmlSuppressableInspectionTool.getSuppressFixes(toolId);
  }
}
