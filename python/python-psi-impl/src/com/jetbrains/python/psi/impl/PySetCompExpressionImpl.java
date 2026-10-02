// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PySetCompExpression;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCollectionTypeImpl;
import com.jetbrains.python.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;


@ApiStatus.Internal
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
      final var type = PyLiteralType.upcastLiteralToClass(getResultElementType(resultExpr, context));
      return new PyCollectionTypeImpl(setType.getPyClass(), false, Collections.singletonList(type));
    }
    return setType != null ? setType : PyAnyType.getUnknown();
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPySetCompExpression(this);
  }
}
