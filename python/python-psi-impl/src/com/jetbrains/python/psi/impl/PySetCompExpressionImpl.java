// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PySetCompExpression;
import com.jetbrains.python.psi.types.PyCollectionTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;


public class PySetCompExpressionImpl extends PyComprehensionElementImpl implements PySetCompExpression {
  public PySetCompExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final var resultExpr = getResultExpression();
    final var cache = PyBuiltinCache.getInstance(this);
    final var setType = cache.getSetType();
    if (setType != null && resultExpr != null) {
      final var type = context.getType(resultExpr);
      return new PyCollectionTypeImpl(setType.getPyClass(), false, Collections.singletonList(type));
    }
    return setType;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPySetCompExpression(this);
  }
}
