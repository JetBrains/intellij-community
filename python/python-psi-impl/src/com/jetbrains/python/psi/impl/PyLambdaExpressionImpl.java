// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyExpectedTypeJudgement;
import com.jetbrains.python.psi.types.PyFunctionTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.intellij.util.containers.ContainerUtil.map;


public class PyLambdaExpressionImpl extends PyElementImpl implements PyLambdaExpression {
  public PyLambdaExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyLambdaExpression(this);
  }

  @Override
  public @NotNull PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
      final PyType type = provider.getCallableType(this, context);
      if (type != null) {
        return type;
      }
    }

    @Nullable PyType expected = PyExpectedTypeJudgement.getExpectedType(this, context);

    if (expected instanceof PyCallableType expectedCallable) {
      var params = new ArrayList<PyCallableParameter>();
      var rawActualParams = getParameterList().getParameters();
      var rawExpectedParams = expectedCallable.getParameters(context);
      if (rawExpectedParams != null) {
        // commence actual parameter to expected parameter mapping
        // currently we only map positional arguments TODO: PY-85576
        var actualParams = ContainerUtil.filter(rawActualParams, element -> !(element instanceof PySlashParameter));
        var expectedParams = ContainerUtil.filter(rawExpectedParams, element -> !(element.isPositionOnlySeparator()));
        var supported = true;

        for (var x : ContainerUtil.zip(actualParams, expectedParams)) {
          var rawActualParameter = x.first;
          var expectedParameter = x.second;
          if (!(rawActualParameter instanceof PyNamedParameter actualParameter)) {
            supported = false;
            break;
          }
          if (
            actualParameter.isPositionalContainer() ||
            actualParameter.isKeywordContainer() ||
            expectedParameter.isPositionalContainer() ||
            expectedParameter.isKeywordContainer() ||
            expectedParameter.isKeywordOnlySeparator()
          ) {
            supported = false;
            break;
          }
          params.add(PyCallableParameterImpl.psi(actualParameter, expectedParameter.getArgumentType(context)));
        }
        if (supported) {
          return new PyFunctionTypeImpl(this, params);
        }
      }
    }
    return new PyFunctionTypeImpl(
      this, map(getParameterList().getParameters(), param -> PyCallableParameterImpl.psi(param, null))
    );
  }

  @Override
  public @NotNull List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return Optional
      .ofNullable(context.getType(this))
      .filter(PyCallableType.class::isInstance)
      .map(PyCallableType.class::cast)
      .map(callableType -> callableType.getParameters(context))
      .orElseGet(() -> ContainerUtil.map(getParameterList().getParameters(), PyCallableParameterImpl::psi));
  }

  @Override
  public @Nullable PyType getReturnType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyExpression body = getBody();
    if (body == null) return null;

    final PyFunctionImpl.YieldCollector visitor = new PyFunctionImpl.YieldCollector();
    body.accept(visitor);

    final List<PyType> yieldTypes = map(visitor.getYieldExpressions(), it -> it.getYieldType(context));
    final List<PyType> sendTypes = map(visitor.getYieldExpressions(), it -> it.getSendType(context));

    if (!yieldTypes.isEmpty()) {
      return PyTypingTypeProvider.wrapInGeneratorType(
        PyUnionType.union(yieldTypes), PyUnionType.union(sendTypes), context.getType(body), this);
    }
    return context.getType(body);
  }

  @Override
  public @Nullable PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    return context.getReturnType(this);
  }

  @Override
  public @Nullable PyType getCallType(@Nullable PyExpression receiver,
                                      @Nullable PyCallSiteExpression pyCallSiteExpression,
                                      @NotNull Map<PyExpression, PyCallableParameter> parameters,
                                      @NotNull TypeEvalContext context) {
    return context.getReturnType(this);
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    ControlFlowCache.clear(this);
  }

  @Override
  public @Nullable String getQualifiedName() {
    return null;
  }
}
