package com.jetbrains.python.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
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
  PyType getParameterType(PyNamedParameter param, final PyFunction func, TypeEvalContext context);

  @Nullable
  PyType getReturnType(PyFunction function, @Nullable PyReferenceExpression callSite, TypeEvalContext context);

  @Nullable
  PyType getIterationType(PyClass iterable);
}
