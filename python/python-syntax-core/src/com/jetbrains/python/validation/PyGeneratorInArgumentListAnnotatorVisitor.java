// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PySyntaxCoreBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstArgumentList;
import com.jetbrains.python.ast.PyAstElementVisitor;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstGeneratorExpression;
import org.jetbrains.annotations.NotNull;


final class PyGeneratorInArgumentListAnnotatorVisitor extends PyAstElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  PyGeneratorInArgumentListAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyArgumentList(@NotNull PyAstArgumentList node) {
    if (node.getArguments().length > 1) {
      for (PyAstExpression expression : node.getArguments()) {
        if (expression instanceof PyAstGeneratorExpression) {
          ASTNode firstChildNode = expression.getNode().getFirstChildNode();
          if (firstChildNode.getElementType() != PyTokenTypes.LPAR) {
            myHolder.markError(expression, PySyntaxCoreBundle.message("ANN.generator.expression.must.be.parenthesized.if.not.sole.argument"));
          }
        }
      }
    }
  }
}
