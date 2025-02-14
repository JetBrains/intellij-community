package com.jetbrains.python.codeInsight.typing;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

@ApiStatus.Internal
public abstract class PyTypeProviderWithCustomContext<Context> extends PyTypeProviderBase {
  @Override
  public final @Nullable PyType getReferenceExpressionType(@NotNull PyReferenceExpression referenceExpression, @NotNull TypeEvalContext context) {
    return withCustomContext(context, customContext -> {
      return getReferenceExpressionType(referenceExpression, customContext);
    });
  }

  protected @Nullable PyType getReferenceExpressionType(@NotNull PyReferenceExpression expression, @NotNull Context context) {
    return null;
  }

  @Override
  public final Ref<PyType> getReferenceType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context, @Nullable PsiElement anchor) {
    return withCustomContext(context, customContext -> {
      return getReferenceType(referenceTarget, customContext, anchor);
    });
  }

  public Ref<PyType> getReferenceType(@NotNull PsiElement target, @NotNull Context context, @Nullable PsiElement anchor) {
    return null;
  }

  @Override
  public final @Nullable Ref<PyType> getParameterType(@NotNull PyNamedParameter param,
                                                @NotNull PyFunction func,
                                                @NotNull TypeEvalContext context) {
    return withCustomContext(context, customContext -> {
      return getParameterType(param, func, customContext);
    });
  }

  public Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull Context context) {
    return null;
  }

  @Override
  public final @Nullable Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    return withCustomContext(context, customContext -> {
      return getReturnType(callable, customContext);
    });
  }

  public Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull Context context) {
    return null;
  }

  @Override
  public final @Nullable Ref<PyType> getCallType(@NotNull PyFunction function,
                                           @NotNull PyCallSiteExpression callSite,
                                           @NotNull TypeEvalContext context) {
    return withCustomContext(context, customContext -> {
      return getCallType(function, callSite, customContext);
    });
  }

  public Ref<PyType> getCallType(@NotNull PyFunction function, @NotNull PyCallSiteExpression site, @NotNull Context context) {
    return null;
  }

  @Override
  public final @Nullable PyType getContextManagerVariableType(PyClass contextManager, PyExpression withExpression, TypeEvalContext context) {
    return withCustomContext(context, customContext -> {
      return getContextManagerVariableType(contextManager, withExpression, customContext);
    });
  }

  public PyType getContextManagerVariableType(PyClass manager, PyExpression expression, Context context) {
    return null;
  }

  @Override
  public final @Nullable PyType getCallableType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    return withCustomContext(context, customContext -> {
      return getCallableType(callable, customContext);
    });
  }

  public PyType getCallableType(@NotNull PyCallable callable, @NotNull Context context) {
    return null;
  }

  @Override
  public final @Nullable PyType getGenericType(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    return withCustomContext(context, customContext -> {
      return getGenericType(cls, customContext);
    });
  }

  public PyType getGenericType(@NotNull PyClass cls, @NotNull Context context) {
    return null;
  }

  @Override
  public final @NotNull Map<PyType, PyType> getGenericSubstitutions(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    return withCustomContext(context, customContext -> {
      return getGenericSubstitutions(cls, customContext);
    });
  }

  public Map<PyType, PyType> getGenericSubstitutions(@NotNull PyClass cls, @NotNull Context context) {
    return null;
  }

  @Override
  public final @Nullable Ref<@Nullable PyCallableType> prepareCalleeTypeForCall(@Nullable PyType type,
                                                                          @NotNull PyCallExpression call,
                                                                          @NotNull TypeEvalContext context) {
    return withCustomContext(context, customContext -> {
      return prepareCalleeTypeForCall(type, call, customContext);
    });
  }

  public Ref<PyCallableType> prepareCalleeTypeForCall(@Nullable PyType type, @NotNull PyCallExpression call, @NotNull Context context) {
    return null;
  }

  protected abstract <T> T withCustomContext(@NotNull TypeEvalContext context, @NotNull Function<@NotNull Context, T> delegate);
}
