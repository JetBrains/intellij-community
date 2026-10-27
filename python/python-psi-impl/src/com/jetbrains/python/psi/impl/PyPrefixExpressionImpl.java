// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyNarrowedType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;


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
    final PyExpression operand = getOperand();
    final PyType operandType = operand != null ? context.getType(operand) : null;
    if (getOperator() == PyTokenTypes.NOT_KEYWORD) {
      if (operandType instanceof PyNarrowedType narrowedType) {
        return narrowedType.negate();
      }
      final PyType literalResultType = getLiteralOperationType(operandType);
      if (literalResultType != null) {
        return literalResultType;
      }
      final PyClassType boolType = PyBuiltinCache.getInstance(this).getBoolType();
      return boolType != null ? boolType : PyAnyType.getUnknown();
    }
    final boolean isAwait = getOperator() == PyTokenTypes.AWAIT_KEYWORD;
    if (isAwait) {
      if (operand != null) {
        final Ref<PyType> type = getGeneratorReturnType(operandType);
        if (type != null) {
          return type.get();
        }
      }
    }
    else {
      final PyType literalResultType = getLiteralOperationType(operandType);
      if (literalResultType != null) {
        return literalResultType;
      }
    }

    return StreamEx
      .of(PyCallExpressionHelper.mapArguments(this, PyResolveContext.defaultContext(context)))
      .map(PyCallExpression.PyArgumentsMapping::getCallableType)
      .nonNull()
      .map(callableType -> callableType.getCallType(context, this, ContainerUtil.emptyList()))
      .map(callType -> {
        if (!isAwait) return callType;
        final Ref<PyType> returnType = getGeneratorReturnType(callType);
        return returnType != null ? returnType.get() : PyAnyType.getUnknown();
      })
      .collect(PyTypeUtil.toUnion());
  }

  /**
   * Evaluates {@code -}, {@code +} and {@code ~} on an {@code int} literal, and {@code not} on a {@code bool} literal.
   * A union of such literals gives the union of the results.
   * Returns {@code null} if a member of the operand type is not such a literal.
   */
  private @Nullable PyType getLiteralOperationType(@Nullable PyType operandType) {
    if (operandType instanceof PyLiteralType literalType) {
      return getLiteralOperationType(literalType);
    }
    if (operandType instanceof PyUnionType unionType) {
      final List<PyType> resultTypes = new ArrayList<>();
      for (PyType member : unionType.getMembers()) {
        final PyType resultType = member instanceof PyLiteralType literalType ? getLiteralOperationType(literalType) : null;
        if (resultType == null) {
          return null;
        }
        resultTypes.add(resultType);
      }
      return PyUnionType.unionOrUnknown(resultTypes);
    }
    return null;
  }

  private @Nullable PyLiteralType getLiteralOperationType(@NotNull PyLiteralType literalType) {
    if (literalType.isDefinition()) {
      return null;
    }
    final PyElementType operator = getOperator();
    final BigInteger intValue = literalType.getIntValue();
    if (intValue != null) {
      if (operator == PyTokenTypes.PLUS) {
        return literalType;
      }
      if (operator == PyTokenTypes.MINUS) {
        return PyLiteralType.intLiteral(this, intValue.negate());
      }
      if (operator == PyTokenTypes.TILDE) {
        return PyLiteralType.intLiteral(this, intValue.not());
      }
    }
    final Boolean boolValue = literalType.getBoolValue();
    if (boolValue != null && operator == PyTokenTypes.NOT_KEYWORD) {
      return PyLiteralType.boolLiteral(this, !boolValue);
    }
    return null;
  }

  private static @Nullable Ref<PyType> getGeneratorReturnType(@Nullable PyType type) {
    if (type instanceof PyClassType classType && classType.isParameterized()) {
      if (PyNames.AWAITABLE.equals(classType.getPyClass().getName())) {
        return Ref.create(classType.getIteratedItemType());
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
