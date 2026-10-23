// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.types.PyCollectionTypeUtil;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public class PyDictLiteralExpressionImpl extends PySequenceExpressionImpl implements PyDictLiteralExpression {
  public PyDictLiteralExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public PyKeyValueExpression @NotNull [] getElements() {
    return childrenToPsi(PyElementTypes.KEY_VALUE_EXPRESSIONS, PyKeyValueExpression.EMPTY_ARRAY);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return PyCollectionTypeUtil.getDictLiteralType(this, context);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyDictLiteralExpression(this);
  }
}
