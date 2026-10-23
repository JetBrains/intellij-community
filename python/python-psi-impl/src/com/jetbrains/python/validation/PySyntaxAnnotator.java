// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;


/**
 * The syntax checks that still need the stub-based PSI: a quick fix, an injected type hint file, or the console context.
 * The AST-only checks run in {@code PyAstSyntaxAnnotator} of {@code intellij.python.syntax.core}.
 */
@ApiStatus.Internal
public final class PySyntaxAnnotator extends PyAnnotatorBase implements DumbAware {
  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull PyAnnotationHolder holder) {
    List<@NotNull PsiElementVisitor> visitors = List.of(
      new PyAssignTargetAnnotatorVisitor(holder),
      new PyAsyncAwaitAnnotatorVisitor(holder),
      new PyStarAnnotatorVisitor(holder),
      new PyPatternAnnotatorVisitor(holder),
      new PyTryExceptAnnotatorVisitor(holder)
    );
    for (PsiElementVisitor visitor : visitors) {
      psiElement.accept(visitor);
    }
  }
}
