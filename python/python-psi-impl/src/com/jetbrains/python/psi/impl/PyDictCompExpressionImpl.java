// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.util.SmartList;
import com.jetbrains.python.psi.PyDictCompExpression;
import com.jetbrains.python.psi.PyDoubleStarExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyCollectionTypeImpl;
import com.jetbrains.python.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class PyDictCompExpressionImpl extends PyComprehensionElementImpl implements PyDictCompExpression {
  public PyDictCompExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final var resultExpr = getResultExpression();
    final var cache = PyBuiltinCache.getInstance(this);
    final var dictionary = cache.getDictType();
    if (dictionary == null) {
      return null;
    }
    // PEP 798: {**d for d in dicts} — key/value types come from the unpacked mapping
    if (resultExpr instanceof PyDoubleStarExpression doubleStarExpression) {
      PyType keyType = null;
      PyType valueType = null;
      final PyExpression mapping = doubleStarExpression.getExpression();
      if (mapping != null &&
          PyTypeUtil.convertToType(context.getType(mapping), "typing.Mapping", doubleStarExpression, context)
            instanceof PyClassType mappingType) {
        final List<PyType> elementTypes = mappingType.getTypeArguments();
        if (elementTypes.size() == 2) {
          keyType = elementTypes.get(0);
          valueType = elementTypes.get(1);
        }
      }
      return new PyCollectionTypeImpl(dictionary.getPyClass(), false, new SmartList<>(keyType, valueType));
    }
    if (resultExpr instanceof PyKeyValueExpression keyValue) {
      final PyType keyType = PyLiteralType.upcastLiteralToClass(keyValue.getKey().getType(context));
      PyExpression value = keyValue.getValue();
      final var valueType = value != null ? PyLiteralType.upcastLiteralToClass(value.getType(context)) : null;
      return new PyCollectionTypeImpl(dictionary.getPyClass(), false, new SmartList<>(keyType, valueType));
    }
    return dictionary;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyDictCompExpression(this);
  }
}
