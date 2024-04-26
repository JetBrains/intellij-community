// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyBoolLiteralExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;


public class PyBoolLiteralExpressionImpl extends PyElementImpl implements PyBoolLiteralExpression {
  public PyBoolLiteralExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return PyBuiltinCache.getInstance(this).getBoolType();
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyBoolLiteralExpression(this);
  }
}
