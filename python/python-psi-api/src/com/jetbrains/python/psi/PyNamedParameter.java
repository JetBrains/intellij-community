// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstNamedParameter;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a named parameter, as opposed to a tuple parameter.
 */
public interface PyNamedParameter extends PyAstNamedParameter, PyParameter, PsiNamedElement, PsiNameIdentifierOwner, PyExpression, PyTypeCommentOwner,
                                          PyAnnotationOwner, StubBasedPsiElement<PyNamedParameterStub> {

  @Override
  default @Nullable PyExpression getDefaultValue() {
    return (PyExpression)PyAstNamedParameter.super.getDefaultValue();
  }

  /**
   * @param context context to be used to resolve argument type
   * @return argument type. Returns element type for *param and value type for **param.
   * @deprecated Use {@link PyCallableParameter#getArgumentType(TypeEvalContext)}
   */
  @Deprecated(forRemoval = true)
  @Nullable
  PyType getArgumentType(@NotNull TypeEvalContext context);

  @Override
  default @NotNull PyNamedParameter getAsNamed() {
    return (PyNamedParameter)PyAstNamedParameter.super.getAsNamed();
  }

  @Override
  default @Nullable PyTupleParameter getAsTuple() {
    return (PyTupleParameter)PyAstNamedParameter.super.getAsTuple();
  }
}

