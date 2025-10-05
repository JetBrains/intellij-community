// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.psi.PyUtil.as;


public class PyFunctionHighlightingAnnotator extends PyAnnotatorBase {
  @Override
  protected void annotate(@NotNull PsiElement element, @NotNull PyAnnotationHolder holder) {
    element.accept(new MyVisitor(holder));
  }

  private static class MyVisitor extends PyElementVisitor {
    public static final HighlightSeverity LOW_PRIORITY_HIGHLIGHTING = new HighlightSeverity("LOW_PRIORITY_HIGHLIGHTING",
                                                                                            HighlightSeverity.INFORMATION.myVal - 3);

    private final @NotNull PyAnnotationHolder myHolder;

    private MyVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

    @Override
    public void visitPyParameter(@NotNull PyParameter node) {
      final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
      if (function != null) {
        final TextAttributesKey attrKey = node.isSelf() ? PyHighlighter.PY_SELF_PARAMETER : PyHighlighter.PY_PARAMETER;
        if (isArgOrKwargParameter(node)) {
          myHolder.addHighlightingAnnotation(node, attrKey);
        }
        else {
          myHolder.addHighlightingAnnotation(node.getFirstChild(), attrKey);
        }
      }
    }

    private static boolean isArgOrKwargParameter(PyParameter parameter) {
      if (parameter instanceof PyNamedParameter namedParameter) {
        return namedParameter.isPositionalContainer() ||
               namedParameter.isKeywordContainer();
      }
      return false;
    }

    @Override
    public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
      final String referencedName = node.getReferencedName();
      if (!node.isQualified() && referencedName != null) {
        PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
        if (function != null) {
          PyNamedParameter element = findParameterRecursively(function, referencedName);
          if (element != null) {
            final TextAttributesKey attrKey = element.isSelf() ? PyHighlighter.PY_SELF_PARAMETER : PyHighlighter.PY_PARAMETER;
            myHolder.addHighlightingAnnotation(node, attrKey);
          }
        }
      }
    }

    @Override
    public void visitPyKeywordArgument(@NotNull PyKeywordArgument node) {
      final ASTNode keywordNode = node.getKeywordNode();
      if (keywordNode != null) {
        myHolder.addHighlightingAnnotation(keywordNode, PyHighlighter.PY_KEYWORD_ARGUMENT);
      }
    }

    @Override
    public void visitPyCallExpression(@NotNull PyCallExpression node) {
      if (node.getParent() instanceof PyDecorator) return; //if it's in decorator, then we've already highlighted it as a decorator
      final PyReferenceExpression callee = as(node.getCallee(), PyReferenceExpression.class);
      if (callee != null) {
        if (!callee.isQualified() && PyBuiltinCache.isInBuiltins(callee)) {
          return;
        }
        final ASTNode functionName = callee.getNameElement();
        if (functionName != null) {
          final TextAttributesKey attrKey = callee.isQualified() ? PyHighlighter.PY_METHOD_CALL : PyHighlighter.PY_FUNCTION_CALL;
          myHolder.addHighlightingAnnotation(functionName, attrKey);
        }
      }
    }

    @Override
    public void visitPyAnnotation(@NotNull PyAnnotation node) {
      final PyExpression value = node.getValue();
      if (value != null) {
        myHolder.addHighlightingAnnotation(value, PyHighlighter.PY_ANNOTATION, LOW_PRIORITY_HIGHLIGHTING);
      }
    }

    private static @Nullable PyNamedParameter findParameterRecursively(@NotNull PyFunction function, @NotNull String referencedName) {
      for (PyFunction f = function; f != null; f = ObjectUtils.tryCast(ScopeUtil.getScopeOwner(f), PyFunction.class)) {
        PyNamedParameter element = f.getParameterList().findParameterByName(referencedName);
        if (element != null) {
          return element;
        }
      }
      return null;
    }
  }
}