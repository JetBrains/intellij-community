// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyGeneratorExpression;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Internal
public class PyGeneratorExpressionImpl extends PyComprehensionElementImpl implements PyGeneratorExpression {
  public PyGeneratorExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyGeneratorExpression(this);
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyExpression resultExpr = getResultExpression();
    if (resultExpr != null) {
      return PyTypingTypeProvider.wrapInGeneratorType(getResultElementType(resultExpr, context), PyAnyType.getUnknown(),
                                                             PyBuiltinCache.getInstance(this).getNoneType(),
                                                             this);
    }
    return PyAnyType.getUnknown();
  }
}
