// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public class PyDictLiteralExpressionImpl extends PySequenceExpressionImpl implements PyDictLiteralExpression {
  private static final TokenSet KEY_VALUE_EXPRESSIONS = TokenSet.create(PyElementTypes.KEY_VALUE_EXPRESSION);

  public PyDictLiteralExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  @NotNull
  public PyKeyValueExpression[] getElements() {
    return childrenToPsi(KEY_VALUE_EXPRESSIONS, PyKeyValueExpression.EMPTY_ARRAY);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return PyBuiltinCache.getInstance(this).createLiteralCollectionType(this, "dict", context);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyDictLiteralExpression(this);
  }
}
