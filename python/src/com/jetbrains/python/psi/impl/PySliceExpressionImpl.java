/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PySliceExpressionImpl extends PyElementImpl implements PySliceExpression {
  public PySliceExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Nullable
  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyType type = context.getType(getOperand());

    // TODO: Currently we don't evaluate the static range of the slice, so we have to return a generic tuple type without elements
    if (type instanceof PyTupleType) {
      return PyBuiltinCache.getInstance(this).getTupleType();
    }

    if (type instanceof PyCollectionType) {
      return type;
    }

    if (type instanceof PyClassType) {
      final List<? extends RatedResolveResult> resolveResults = type.resolveMember(
        PyNames.GETITEM,
        null,
        AccessDirection.READ,
        PyResolveContext.noImplicits().withTypeEvalContext(context)
      );

      if (resolveResults != null) {
        final List<PyType> types = new ArrayList<>();

        for (RatedResolveResult resolveResult : resolveResults) {
          types.addAll(
            getPossibleReturnTypes(resolveResult.getElement(), context)
          );
        }

        return PyUnionType.union(types);
      }
    }

    return null;
  }

  @NotNull
  @Override
  public PyExpression getOperand() {
    return childToPsiNotNull(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
  }

  @Nullable
  @Override
  public PySliceItem getSliceItem() {
    return PsiTreeUtil.getChildOfType(this, PySliceItem.class);
  }

  @NotNull
  private static List<PyType> getPossibleReturnTypes(@Nullable PsiElement element, @NotNull TypeEvalContext context) {
    final List<PyType> result = new ArrayList<PyType>();

    if (element instanceof PyTypedElement) {
      final PyType elementType = context.getType((PyTypedElement)element);

      result.addAll(getPossibleReturnTypes(elementType, context));

      if (elementType instanceof PyUnionType) {
        for (PyType type : ((PyUnionType)elementType).getMembers()) {
          result.addAll(getPossibleReturnTypes(type, context));
        }
      }
    }

    return result;
  }

  @NotNull
  private static List<PyType> getPossibleReturnTypes(@Nullable PyType type, @NotNull TypeEvalContext context) {
    if (type instanceof PyCallableType) {
      return Collections.singletonList(((PyCallableType)type).getReturnType(context));
    }

    return Collections.emptyList();
  }
}
