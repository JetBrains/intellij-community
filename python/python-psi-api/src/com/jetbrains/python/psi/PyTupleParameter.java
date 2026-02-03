// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstTupleParameter;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tuple parameter. Defines nothing; this interface is only needed for stub creation.
 */
public interface PyTupleParameter extends PyAstTupleParameter, PyParameter, StubBasedPsiElement<PyTupleParameterStub> {

  @Override
  default @Nullable PyNamedParameter getAsNamed() {
    return (PyNamedParameter)PyAstTupleParameter.super.getAsNamed();
  }

  @Override
  default @NotNull PyTupleParameter getAsTuple() {
    return (PyTupleParameter)PyAstTupleParameter.super.getAsTuple();
  }

  @Override
  default @Nullable PyExpression getDefaultValue() {
    return (PyExpression)PyAstTupleParameter.super.getDefaultValue();
  }

  /**
   * @return the nested parameters within this tuple parameter.
   */
  @Override
  PyParameter @NotNull [] getContents();
}
