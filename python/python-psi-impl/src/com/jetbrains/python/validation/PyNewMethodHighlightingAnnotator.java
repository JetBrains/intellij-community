// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Highlights a {@code __new__} method of a new-style class as a predefined definition.
 * {@link PyDefinitionsHighlightingAnnotator} colors every other definition from the AST and skips {@code __new__},
 * because the new-style check needs the class hierarchy.
 */
public final class PyNewMethodHighlightingAnnotator extends PyAnnotatorBase {
  @Override
  public boolean runInBatchMode() {
    return false;
  }

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull PyAnnotationHolder holder) {
    if (holder.isBatchMode()) return;
    element.accept(new MyVisitor(holder));
  }

  private static class MyVisitor extends PyElementVisitor {
    private final @NotNull PyAnnotationHolder myHolder;

    private MyVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
      ASTNode nameNode = node.getNameNode();
      if (nameNode == null || !PyUtil.isNewMethod(node)) return;
      PyClass cls = node.getContainingClass();
      boolean newStyleClass = false;
      try {
        if (cls != null) newStyleClass = cls.isNewStyleClass(null);
      }
      catch (IndexNotReadyException ignored) {
      }
      if (newStyleClass) {
        myHolder.addHighlightingAnnotation(nameNode, PyHighlighter.PY_PREDEFINED_DEFINITION);
      }
    }
  }
}
