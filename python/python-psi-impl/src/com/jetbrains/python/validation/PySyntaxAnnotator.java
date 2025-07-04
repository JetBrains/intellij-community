// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.validation;

import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public final class PySyntaxAnnotator extends PyAnnotatorBase implements DumbAware {
  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull PyAnnotationHolder holder) {
    List<@NotNull PsiElementVisitor> visitors = List.of(
      new PyAssignTargetAnnotatorVisitor(holder),
      new PyTypeAnnotationTargetAnnotatorVisitor(holder),
      new PyParameterListAnnotatorVisitor(holder),
      new ReturnAnnotator(holder),
      new PyBreakContinueAnnotatorVisitor(holder),
      new PyGlobalAnnotatorVisitor(holder),
      new PyImportAnnotatorVisitor(holder),
      new PyAsyncAwaitAnnotatorVisitor(holder),
      new PyAstNumericLiteralAnnotatorVisitor(holder)
    );
    for (PsiElementVisitor visitor : visitors) {
      psiElement.accept(visitor);
    }
  }
}
