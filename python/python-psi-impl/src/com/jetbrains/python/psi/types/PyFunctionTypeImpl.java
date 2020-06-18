// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.psi.PyFunction.Modifier.STATICMETHOD;
import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Type of a particular function that is represented as a {@link PyCallable} in the PSI tree.
 *
 * @author vlan
 */
public class PyFunctionTypeImpl implements PyFunctionType {
  @NotNull private final PyCallable myCallable;
  @NotNull private final List<PyCallableParameter> myParameters;

  public PyFunctionTypeImpl(@NotNull PyCallable callable) {
    this(callable, ContainerUtil.map(callable.getParameterList().getParameters(), PyCallableParameterImpl::psi));
  }

  public PyFunctionTypeImpl(@NotNull PyCallable callable, @NotNull List<PyCallableParameter> parameters) {
    myCallable = callable;
    myParameters = parameters;
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull TypeEvalContext context) {
    return context.getReturnType(myCallable);
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    return myCallable.getCallType(context, callSite);
  }

  @Nullable
  @Override
  public List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return myParameters;
  }

  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    final PyClassType delegate = selectCallableType(location, resolveContext.getTypeEvalContext());
    if (delegate == null) {
      return Collections.emptyList();
    }
    return delegate.resolveMember(name, location, direction, resolveContext);
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    final TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(location.getProject(), location.getContainingFile());
    final PyClassType delegate;
    if (location instanceof PyReferenceExpression) {
      delegate = selectCallableType(((PyReferenceExpression)location).getQualifier(), typeEvalContext);
    }
    else {
      final PyClass cls = PyPsiFacade.getInstance(myCallable.getProject()).createClassByQName(PyNames.TYPES_FUNCTION_TYPE, myCallable);
      delegate = cls != null ? new PyClassTypeImpl(cls, false) : null;
    }
    if (delegate == null) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }
    return delegate.getCompletionVariants(completionPrefix, location, context);
  }

  @Nullable
  private PyClassType selectCallableType(@Nullable PyExpression location, @NotNull TypeEvalContext context) {
    final String className;
    if (location instanceof PyReferenceExpression && isBoundMethodReference((PyReferenceExpression)location, context)) {
      className = PyNames.TYPES_METHOD_TYPE;
    }
    else {
      className = PyNames.TYPES_FUNCTION_TYPE;
    }
    final PyClass cls = PyPsiFacade.getInstance(myCallable.getProject()).createClassByQName(className, myCallable);
    return cls != null ? new PyClassTypeImpl(cls, false) : null;
  }

  private boolean isBoundMethodReference(@NotNull PyReferenceExpression location, @NotNull TypeEvalContext context) {
    final PyFunction function = as(getCallable(), PyFunction.class);
    final boolean isNonStaticMethod = function != null && function.getContainingClass() != null && function.getModifier() != STATICMETHOD;
    if (isNonStaticMethod) {
      // In Python 2 unbound methods have __method fake type
      if (LanguageLevel.forElement(location).isPython2()) {
        return true;
      }
      final PyExpression qualifier;
      if (location.isQualified()) {
        qualifier = location.getQualifier();
      }
      else {
        final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);
        qualifier = ContainerUtil.getLastItem(location.followAssignmentsChain(resolveContext).getQualifiers());
      }
      if (qualifier != null) {
        final PyType qualifierType = PyTypeChecker.toNonWeakType(context.getType(qualifier), context);
        if (isInstanceType(qualifierType)) {
          return true;
        }
        else if (qualifierType instanceof PyUnionType) {
          for (PyType type : ((PyUnionType)qualifierType).getMembers()) {
            if (isInstanceType(type)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static boolean isInstanceType(@Nullable PyType type) {
    return type instanceof PyClassType && !((PyClassType)type).isDefinition();
  }

  @Override
  public String getName() {
    return "function";
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }

  @Override
  @NotNull
  public PyCallable getCallable() {
    return myCallable;
  }

  @Override
  @NotNull
  public PyFunctionType dropSelf(@NotNull TypeEvalContext context) {
    final List<PyCallableParameter> parameters = getParameters(context);

    if (!ContainerUtil.isEmpty(parameters) && parameters.get(0).isSelf()) {
      return new PyFunctionTypeImpl(myCallable, ContainerUtil.subList(parameters, 1));
    }
    return this;
  }
}
