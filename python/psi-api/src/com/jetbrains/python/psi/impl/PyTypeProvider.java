package com.jetbrains.python.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyTypeProvider {
  ExtensionPointName<PyTypeProvider> EP_NAME = ExtensionPointName.create("Pythonid.typeProvider");

  @Nullable
  PyType getReferenceExpressionType(PyReferenceExpression referenceExpression, TypeEvalContext context);

  @Nullable
  PyType getReferenceType(@NotNull PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor);

  @Nullable
  PyType getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context);

  @Nullable
  PyType getReturnType(@NotNull PyFunction function, @Nullable PyQualifiedExpression callSite, @NotNull TypeEvalContext context);

  @Nullable
  PyType getIterationType(@NotNull PyClass iterable);

  @Nullable
  PyType getContextManagerVariableType(PyClass contextManager, PyExpression withExpression, TypeEvalContext context);

  @Nullable
  PyType getCallableType(@NotNull Callable callable, @NotNull TypeEvalContext context);
}
