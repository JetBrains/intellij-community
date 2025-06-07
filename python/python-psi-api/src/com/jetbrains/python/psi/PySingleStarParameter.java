// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstSingleStarParameter;
import com.jetbrains.python.psi.stubs.PySingleStarParameterStub;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single star (keyword-only parameter delimiter) appearing in the
 * parameter list of a Py3K function.
 */
public interface PySingleStarParameter extends PyAstSingleStarParameter, PyParameter, StubBasedPsiElement<PySingleStarParameterStub> {
  @Override
  default @Nullable PyNamedParameter getAsNamed() {
    return (PyNamedParameter)PyAstSingleStarParameter.super.getAsNamed();
  }

  @Override
  default @Nullable PyTupleParameter getAsTuple() {
    return (PyTupleParameter)PyAstSingleStarParameter.super.getAsTuple();
  }

  @Override
  default @Nullable PyExpression getDefaultValue() {
    return (PyExpression)PyAstSingleStarParameter.super.getDefaultValue();
  }
}
