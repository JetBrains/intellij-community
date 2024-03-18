// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;


public class PyLambdaExpressionImpl extends PyElementImpl implements PyLambdaExpression {
  public PyLambdaExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyLambdaExpression(this);
  }

  @Override
  @NotNull
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
      final PyType type = provider.getCallableType(this, context);
      if (type != null) {
        return type;
      }
    }
    return new PyFunctionTypeImpl(this);
  }



  @NotNull
  @Override
  public List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return Optional
      .ofNullable(context.getType(this))
      .filter(PyCallableType.class::isInstance)
      .map(PyCallableType.class::cast)
      .map(callableType -> callableType.getParameters(context))
      .orElseGet(() -> ContainerUtil.map(getParameterList().getParameters(), PyCallableParameterImpl::psi));
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
                            @NotNull Map<PyExpression, PyCallableParameter> parameters,
                            @NotNull TypeEvalContext context) {
    return context.getReturnType(this);
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
