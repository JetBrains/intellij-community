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
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;


public class PySubscriptionExpressionImpl extends PyElementImpl implements PySubscriptionExpression {
  public PySubscriptionExpressionImpl(ASTNode astNode) {
    super(astNode);
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
    if (type instanceof PyTupleType tupleType) {
      return Optional
        .ofNullable(PyEvaluator.evaluate(indexExpression, Integer.class))
        .map(index -> !tupleType.isHomogeneous() && index < 0 ? tupleType.getElementCount() + index : index)
        .map(tupleType::getElementType)
        .orElse(null);
    }
    if (type instanceof PyTypedDictType typedDictType) {
      return Optional
        .ofNullable(PyEvaluator.evaluate(indexExpression, String.class))
        .map(typedDictType::getElementType)
        .orElse(null);
    }
    if (type instanceof PyClassType) {
      PyType parameterizedType = Ref.deref(PyTypingTypeProvider.getType(this, context));
      if (parameterizedType instanceof PyCollectionType collectionType) {
        return collectionType.toClass();
      }
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
}
