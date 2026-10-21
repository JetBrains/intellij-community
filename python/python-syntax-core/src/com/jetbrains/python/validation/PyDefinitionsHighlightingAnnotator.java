// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.PyAstClass;
import com.jetbrains.python.ast.PyAstDecorator;
import com.jetbrains.python.ast.PyAstElementVisitor;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtilCore;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Highlights class definitions, function definitions, and decorators from the AST. It runs in dumb mode, so a process
 * without indexes, such as JetBrains Light before the smart mode, gets the colors too.
 * <p>
 * A {@code __new__} method needs the class hierarchy to tell a new-style class from an old-style one.
 * {@code PyNewMethodHighlightingAnnotator} in {@code intellij.python.psi.impl} colors it with the stub-based PSI.
 */
@ApiStatus.Internal
public final class PyDefinitionsHighlightingAnnotator extends PyAnnotatorBase implements DumbAware {
  @Override
  public boolean runInBatchMode() {
    return false;
  }

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull PyAnnotationHolder holder) {
    if (holder.isBatchMode()) return;
    element.accept(new MyVisitor(holder));
  }

  private static class MyVisitor extends PyAstElementVisitor {
    private final @NotNull PyAnnotationHolder myHolder;

    private MyVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

    @Override
    public void visitPyClass(@NotNull PyAstClass node) {
      final ASTNode name_node = node.getNameNode();
      if (name_node != null) {
        myHolder.addHighlightingAnnotation(name_node, PyHighlighter.PY_CLASS_DEFINITION);
      }
    }

    @Override
    public void visitPyFunction(@NotNull PyAstFunction node) {
      ASTNode nameNode = node.getNameNode();
      if (nameNode != null) {
        final String name = node.getName();
        LanguageLevel languageLevel = LanguageLevel.forElement(node);
        if (PyNames.UNDERSCORED_ATTRIBUTES.contains(name) || PyNames.getBuiltinMethods(languageLevel).containsKey(name)) {
          if (!PyUtilCore.isNewMethod(node)) {
            myHolder.addHighlightingAnnotation(nameNode, PyHighlighter.PY_PREDEFINED_DEFINITION);
          }
        }
        else {
          if (ScopeUtilCore.getScopeOwner(node) instanceof PyAstFunction) {
            myHolder.addHighlightingAnnotation(nameNode, PyHighlighter.PY_NESTED_FUNC_DEFINITION);
          }
          else {
            myHolder.addHighlightingAnnotation(nameNode, PyHighlighter.PY_FUNC_DEFINITION);
          }
        }
      }
    }

    @Override
    public void visitPyDecorator(@NotNull PyAstDecorator node) {
      final PsiElement atSign = node.getFirstChild();
      if (atSign != null) {
        myHolder.addHighlightingAnnotation(atSign, PyHighlighter.PY_DECORATOR);
        if (node.getQualifiedName() != null) {
          myHolder.addHighlightingAnnotation(Objects.requireNonNull(node.getCallee()), PyHighlighter.PY_DECORATOR);
        }
      }
    }
  }
}
