// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.util.SmartList;
import com.jetbrains.python.psi.PyDictCompExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.types.PyCollectionTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;


public class PyDictCompExpressionImpl extends PyComprehensionElementImpl implements PyDictCompExpression {
  public PyDictCompExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final var resultExpr = getResultExpression();
    final var cache = PyBuiltinCache.getInstance(this);
    final var dictionary = cache.getDictType();
    if (resultExpr instanceof PyKeyValueExpression keyValue && dictionary != null) {
      final PyType keyType = context.getType(keyValue.getKey());
      PyExpression value = keyValue.getValue();
      final PyType valueType =  value != null ? context.getType(value) : null;
      return new PyCollectionTypeImpl(dictionary.getPyClass(), false, new SmartList<>(keyType, valueType));
    }
    return dictionary;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyDictCompExpression(this);
  }
}
