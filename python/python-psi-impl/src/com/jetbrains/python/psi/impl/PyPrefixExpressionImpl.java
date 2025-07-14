// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyPrefixExpressionImpl extends PyElementImpl implements PyPrefixExpression {
  public PyPrefixExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyPrefixExpression(this);
  }

  @Override
  public PsiReference getReference() {
    return getReference(PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(getProject())));
  }

  @Override
  public @NotNull PsiPolyVariantReference getReference(@NotNull PyResolveContext context) {
    return new PyOperatorReference(this, context);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    if (getOperator() == PyTokenTypes.NOT_KEYWORD) {
      final PyExpression operand = getOperand();
      if (operand != null && context.getType(operand) instanceof PyNarrowedType narrowedType) {
        return narrowedType.negate();
      }
      return PyBuiltinCache.getInstance(this).getBoolType();
    }
    final boolean isAwait = getOperator() == PyTokenTypes.AWAIT_KEYWORD;
    if (isAwait) {
      final PyExpression operand = getOperand();
      if (operand != null) {
        final PyType operandType = context.getType(operand);
        final Ref<PyType> type = getGeneratorReturnType(operandType);
        if (type != null) {
          return type.get();
        }
      }
    }

    return StreamEx
      .of(PyCallExpressionHelper.mapArguments(this, PyResolveContext.defaultContext(context)))
      .map(PyCallExpression.PyArgumentsMapping::getCallableType)
      .nonNull()
      .map(callableType -> callableType.getCallType(context, this))
      .map(callType -> isAwait ? Ref.deref(getGeneratorReturnType(callType)) : callType)
      .collect(PyTypeUtil.toUnion());
  }

  private static @Nullable Ref<PyType> getGeneratorReturnType(@Nullable PyType type) {
    if (type instanceof PyCollectionType) {
      if (PyNames.AWAITABLE.equals(((PyClassType)type).getPyClass().getName())) {
        return Ref.create(((PyCollectionType)type).getIteratedItemType());
      }
      else {
        return PyTypingTypeProvider.coroutineOrGeneratorElementType(type);
      }
    }
    else if (type instanceof PyUnionType) {
      return PyTypeUtil
        .toStream(type)
        .map(PyPrefixExpressionImpl::getGeneratorReturnType)
        .collect(PyTypeUtil.toUnionFromRef(type));
    }
    return null;
  }
}
