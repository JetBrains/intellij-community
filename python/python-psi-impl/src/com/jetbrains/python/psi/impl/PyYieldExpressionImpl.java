// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyYieldExpressionImpl extends PyElementImpl implements PyYieldExpression {
  public PyYieldExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyYieldExpression(this);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    if (isDelegating()) {
      final PyExpression e = getExpression();
      final PyType type = e != null ? context.getType(e) : null;
      var generatorDesc = PyTypingTypeProvider.GeneratorTypeDescriptor.create(type);
      if (generatorDesc != null) {
        return generatorDesc.returnType();
      }
      return PyNoneType.INSTANCE;
    }
    else {
      return getSendType(context);
    }
  }

  @Override
  public @Nullable PyType getYieldType(@NotNull TypeEvalContext context) {
    final PyExpression expr = getExpression();
    final PyType type = expr != null ? context.getType(expr) : PyNoneType.INSTANCE;

    if (isDelegating()) {
      return PyTargetExpressionImpl.getIterationType(type, expr, this, context);
    }
    return type;
  }

  @Override
  public @Nullable PyType getSendType(@NotNull TypeEvalContext context) {
    if (ScopeUtil.getScopeOwner(this) instanceof PyFunction function) {
      if (function.getAnnotation() != null || function.getTypeCommentAnnotation() != null) {
        var returnType = context.getReturnType(function);
        var generatorDesc = PyTypingTypeProvider.GeneratorTypeDescriptor.create(returnType);
        if (generatorDesc != null) {
          return generatorDesc.sendType();
        }
      }
    }
    
    if (isDelegating()) {
      final PyExpression e = getExpression();
      final PyType type = e != null ? context.getType(e) : null;
      var generatorDesc = PyTypingTypeProvider.GeneratorTypeDescriptor.create(type);
      if (generatorDesc != null) {
        return generatorDesc.sendType();
      }
      return PyNoneType.INSTANCE;
    }
    return null;
  }
}
