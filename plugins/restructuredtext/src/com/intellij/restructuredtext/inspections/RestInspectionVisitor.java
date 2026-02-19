// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.PsiElement;
import com.intellij.restructuredtext.validation.RestElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public abstract class RestInspectionVisitor extends RestElementVisitor {
  private final @Nullable ProblemsHolder myHolder;

  public RestInspectionVisitor(final @Nullable ProblemsHolder holder) {
    myHolder = holder;
  }

  protected final void registerProblem(final @Nullable PsiElement element,
                                       final @NotNull @InspectionMessage String message,
                                       final @NotNull LocalQuickFix quickFix) {
    if (element == null || element.getTextLength() == 0) {
      return;
    }
    if (myHolder != null) {
      myHolder.registerProblem(element, message, quickFix);
    }
  }
}
