/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypedDictType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;


public class PySubscriptionExpressionImpl extends PyElementImpl implements PySubscriptionExpression {
  public PySubscriptionExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  @NotNull
  public PyExpression getOperand() {
    return childToPsiNotNull(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 0);
  }

  @NotNull
  @Override
  public PyExpression getRootOperand() {
    PyExpression operand = getOperand();
    while (operand instanceof PySubscriptionExpression) {
      operand = ((PySubscriptionExpression)operand).getOperand();
    }
    return operand;
  }

  @Override
  @Nullable
  public PyExpression getIndexExpression() {
    return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 1);
  }

  @Override
  protected void acceptPyVisitor(final PyElementVisitor pyVisitor) {
    pyVisitor.visitPySubscriptionExpression(this);
  }

  @Nullable
  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyExpression indexExpression = getIndexExpression();
    final PyType type = indexExpression != null ? context.getType(getOperand()) : null;
    if (type instanceof PyTupleType) {
      final PyTupleType tupleType = (PyTupleType)type;
      return Optional
        .ofNullable(PyEvaluator.evaluate(indexExpression, Integer.class))
        .map(tupleType::getElementType)
        .orElse(null);
    }
    if (type instanceof PyTypedDictType) {
      final PyTypedDictType typedDictType = (PyTypedDictType)type;
      return Optional
        .ofNullable(PyEvaluator.evaluate(indexExpression, String.class))
        .map(typedDictType::getElementType)
        .orElse(null);
    }
    return PyCallExpressionHelper.getCallType(this, context, key);
  }

  @Override
  public PsiReference getReference() {
    return getReference(PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(getProject())));
  }

  @NotNull
  @Override
  public PsiPolyVariantReference getReference(@NotNull PyResolveContext context) {
    return new PyOperatorReference(this, context);
  }

  @Override
  public PyExpression getQualifier() {
    return getOperand();
  }

  @Nullable
  @Override
  public QualifiedName asQualifiedName() {
    return PyPsiUtils.asQualifiedName(this);
  }

  @Override
  public boolean isQualified() {
    return getQualifier() != null;
  }

  @Override
  public String getReferencedName() {
    return switch (AccessDirection.of(this)) {
      case READ -> PyNames.GETITEM;
      case WRITE -> PyNames.SETITEM;
      case DELETE -> PyNames.DELITEM;
    };
  }

  @Override
  public ASTNode getNameElement() {
    return getNode().findChildByType(PyTokenTypes.LBRACKET);
  }
}
