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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySliceExpression;
import com.jetbrains.python.psi.PySliceItem;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return getSliceType(type, context);
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

  @Nullable
  private PyType getSliceType(@Nullable PyType operandType, @NotNull TypeEvalContext context) {
    // TODO: Currently we don't evaluate the static range of the slice, so we have to return a generic tuple type without elements
    if (operandType instanceof PyTupleType) {
      return PyBuiltinCache.getInstance(this).getTupleType();
    }

    if (operandType instanceof PyCollectionType) {
      return operandType;
    }

    if (operandType instanceof PyClassType) {
      return PyUtil.getReturnTypeOfMember(operandType, PyNames.GETITEM, null, context);
    }

    if (operandType instanceof PyUnionType) {
      return PyUnionType.union(ContainerUtil.map(((PyUnionType)operandType).getMembers(), member -> getSliceType(member, context)));
    }

    return null;
  }
}
