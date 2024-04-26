// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.PsiElement;
import com.intellij.python.reStructuredText.validation.RestElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public abstract class RestInspectionVisitor extends RestElementVisitor {
  @Nullable private final ProblemsHolder myHolder;

  public RestInspectionVisitor(@Nullable final ProblemsHolder holder) {
    myHolder = holder;
  }

  protected final void registerProblem(@Nullable final PsiElement element,
                                       @NotNull final @InspectionMessage String message,
                                       @NotNull final LocalQuickFix quickFix) {
    if (element == null || element.getTextLength() == 0) {
      return;
    }
    if (myHolder != null) {
      myHolder.registerProblem(element, message, quickFix);
    }
  }
}
