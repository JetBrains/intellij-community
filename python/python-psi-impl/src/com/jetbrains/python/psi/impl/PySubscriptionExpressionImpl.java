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
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySliceItem;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public class PySubscriptionExpressionImpl extends PyElementImpl implements PySubscriptionExpression {
  public PySubscriptionExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(final PyElementVisitor pyVisitor) {
    pyVisitor.visitPySubscriptionExpression(this);
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyExpression indexExpression = getIndexExpression();
    if (indexExpression != null) {
      final PyType operandType = context.getType(getOperand());
      if (indexExpression instanceof PySliceItem) {
        if (operandType instanceof PyTupleType) {
          return ((PyTupleType)operandType).isHomogeneous() ? operandType : PyBuiltinCache.getInstance(this).getTupleType();
        }
      }
      else {
        if (operandType instanceof PyTupleType tupleType) {
          List<Integer> indexPossibleValues = getIndexExpressionPossibleValues(indexExpression, context, Integer.class);
          List<@Nullable PyType> possibleTypes = ContainerUtil.map(indexPossibleValues, index -> {
            if (!tupleType.isHomogeneous() && index < 0) {
              index += tupleType.getElementCount();
            }
            return tupleType.getElementType(index);
          });
          return PyUnionType.union(possibleTypes);
        }
        if (operandType instanceof PyTypedDictType typedDictType) {
          List<String> indexPossibleValues = getIndexExpressionPossibleValues(indexExpression, context, String.class);
          return PyUnionType.union(ContainerUtil.map(indexPossibleValues, typedDictType::getElementType));
        }
        if (operandType instanceof PyClassType) {
          PyType parameterizedType = Ref.deref(PyTypingTypeProvider.getType(this, context));
          if (parameterizedType instanceof PyCollectionType collectionType) {
            return collectionType.toClass();
          }
        }
      }
    }
    return PyCallExpressionHelper.getCallType(this, context, key);
  }

  @ApiStatus.Internal
  public static <T> @NotNull List<T> getIndexExpressionPossibleValues(@Nullable PyExpression indexExpression,
                                                                      @NotNull TypeEvalContext context,
                                                                      @NotNull Class<T> indexType) {
    if (indexExpression == null) {
      return List.of();
    }
    T indexExprValue = PyEvaluator.evaluate(indexExpression, indexType);
    if (indexExprValue != null) {
      return List.of(indexExprValue);
    }
    PyType type = context.getType(indexExpression);
    if (type == null) {
      return List.of();
    }
    List<T> result = new ArrayList<>();
    for (PyType subType : PyTypeUtil.toStream(type)) {
      if (!(subType instanceof PyLiteralType literalType)) {
        return List.of();
      }
      T val = PyEvaluator.evaluateNoResolve(literalType.getExpression(), indexType);
      if (val == null) {
        return List.of();
      }
      result.add(val);
    }
    return result;
  }

  @Override
  public PsiReference getReference() {
    return getReference(PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(getProject())));
  }

  @Override
  public @NotNull PsiPolyVariantReference getReference(@NotNull PyResolveContext context) {
    return new PyOperatorReference(this, context);
  }
}
