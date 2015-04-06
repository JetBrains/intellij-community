/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyFunctionTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author yole
 */
public class PyLambdaExpressionImpl extends PyElementImpl implements PyLambdaExpression {
  public PyLambdaExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyLambdaExpression(this);
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    for (PyTypeProvider provider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      final PyType type = provider.getCallableType(this, context);
      if (type != null) {
        return type;
      }
    }
    return new PyFunctionTypeImpl(this);
  }

  @NotNull
  public PyParameterList getParameterList() {
    final PyElement child = childToPsi(PyElementTypes.PARAMETER_LIST_SET, 0);
    if (child == null) {
      throw new RuntimeException("parameter list must not be null; text=" + getText());
    }
    //noinspection unchecked
    return (PyParameterList)child;
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyExpression body = getBody();
    return body != null ? context.getType(body) : null;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    return context.getReturnType(this);
  }

  @Nullable
  @Override
  public PyType getCallType(@Nullable PyExpression receiver,
                            @NotNull Map<PyExpression, PyNamedParameter> parameters,
                            @NotNull TypeEvalContext context) {
    return context.getReturnType(this);
  }

  @Nullable
  public PyExpression getBody() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  public PyFunction asMethod() {
    return null; // we're never a method
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    ControlFlowCache.clear(this);
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return null;
  }
}
