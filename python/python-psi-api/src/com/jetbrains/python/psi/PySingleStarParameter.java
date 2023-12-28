// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Nullable
  default PyNamedParameter getAsNamed() {
    return (PyNamedParameter)PyAstSingleStarParameter.super.getAsNamed();
  }

  @Override
  @Nullable
  default PyTupleParameter getAsTuple() {
    return (PyTupleParameter)PyAstSingleStarParameter.super.getAsTuple();
  }

  @Override
  @Nullable
  default PyExpression getDefaultValue() {
    return (PyExpression)PyAstSingleStarParameter.super.getDefaultValue();
  }

  @Override
  default boolean isSelf() {
    return false;
  }
}
