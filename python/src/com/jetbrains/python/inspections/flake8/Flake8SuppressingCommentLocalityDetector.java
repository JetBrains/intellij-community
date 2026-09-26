// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.flake8;

import com.intellij.codeInsight.daemon.ChangeLocalityDetector;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Flake8SuppressingCommentLocalityDetector implements ChangeLocalityDetector {
  @Override
  public @Nullable PsiElement getChangeHighlightingDirtyScopeFor(@NotNull PsiElement changedElement) {
    if (changedElement instanceof PsiComment && StringUtil.containsIgnoreCase(changedElement.getText(), Flake8InspectionSuppressor.NOQA)) {
      return PsiTreeUtil.getParentOfType(changedElement, PyStatement.class);
    }
    return null;
  }
}
