// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.validation;

import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.List;


// AST-based syntax annotator that does not rely on resolve.
// Planned to move to thin-client (visitors are to be converted to `PyAstElementVisitor`).
public final class PySyntaxAnnotator extends PyAnnotatorBase implements DumbAware {
  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull PyAnnotationHolder holder) {
    List<@NotNull PsiElementVisitor> visitors = List.of(
      new PyAssignTargetAnnotatorVisitor(holder),
      new PyTypeAnnotationTargetAnnotatorVisitor(holder),
      new PyParameterListAnnotatorVisitor(holder),
      new PyReturnYieldAnnotatorVisitor(holder),
      new PyBreakContinueAnnotatorVisitor(holder),
      new PyGlobalAnnotatorVisitor(holder),
      new PyImportAnnotatorVisitor(holder),
      new PyAsyncAwaitAnnotatorVisitor(holder),
      new PyAstNumericLiteralAnnotatorVisitor(holder),
      new PyGeneratorInArgumentListAnnotatorVisitor(holder),
      new PyStarAnnotatorVisitor(holder),
      new PyStringLiteralQuotesAnnotatorVisitor(holder),
      new PyFStringsAnnotatorVisitor(holder),
      new PyPatternAnnotatorVisitor(holder),
      new PyTryExceptAnnotatorVisitor(holder),
      new PyTypeParameterListAnnotatorVisitor(holder)
    );
    for (PsiElementVisitor visitor : visitors) {
      psiElement.accept(visitor);
    }
  }
}
