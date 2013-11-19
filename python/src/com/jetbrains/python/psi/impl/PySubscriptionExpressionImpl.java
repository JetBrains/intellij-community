/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PySubscriptionExpressionImpl extends PyElementImpl implements PySubscriptionExpression {
  public PySubscriptionExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyExpression getOperand() {
    return childToPsiNotNull(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
  }

  @Nullable
  public PyExpression getIndexExpression() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 1);
  }

  @Override
  protected void acceptPyVisitor(final PyElementVisitor pyVisitor) {
    pyVisitor.visitPySubscriptionExpression(this);
  }

  @Nullable
  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    PyType res = null;
    final PsiReference ref = getReference(PyResolveContext.noImplicits().withTypeEvalContext(context));
    if (ref != null) {
      final PsiElement resolved = ref.resolve();
      if (resolved instanceof Callable) {
        res = ((Callable)resolved).getReturnType(context, this);
      }
    }
    if (PyTypeChecker.isUnknown(res) || res instanceof PyNoneType) {
      final PyExpression indexExpression = getIndexExpression();
      if (indexExpression != null) {
        final PyType type = context.getType(getOperand());
        final PyClass cls = (type instanceof PyClassType) ? ((PyClassType)type).getPyClass() : null;
        if (cls != null && PyABCUtil.isSubclass(cls, PyNames.MAPPING)) {
          return res;
        }
        if (type instanceof PySubscriptableType) {
          res = ((PySubscriptableType)type).getElementType(indexExpression, context);
        }
        else if (type instanceof PyCollectionType) {
          res = ((PyCollectionType) type).getElementType(context);
        }
      }
    }
    return res;
  }

  @Override
  public PsiReference getReference() {
    return getReference(PyResolveContext.noImplicits());
  }

  @Override
  public PsiPolyVariantReference getReference(PyResolveContext context) {
    return new PyOperatorReference(this, context);
  }

  @Override
  public PyExpression getQualifier() {
    return getOperand();
  }

  @Override
  public String getReferencedName() {
    String res = PyNames.GETITEM;
    switch (AccessDirection.of(this)) {
      case READ:
        res = PyNames.GETITEM;
        break;
      case WRITE:
        res = PyNames.SETITEM;
        break;
      case DELETE:
        res = PyNames.DELITEM;
        break;
    }
    return res;
  }

  @Override
  public ASTNode getNameElement() {
    return getNode().findChildByType(PyTokenTypes.LBRACKET);
  }
}
