// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstSlashParameter;
import com.jetbrains.python.psi.stubs.PySlashParameterStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Represents positional-only parameters delimiter introduced in Python 3.8 (PEP 570).
 */
@ApiStatus.NonExtendable
public interface PySlashParameter extends PyAstSlashParameter, PyParameter, StubBasedPsiElement<PySlashParameterStub> {
  @Override
  default @Nullable PyNamedParameter getAsNamed() {
    return (PyNamedParameter)PyAstSlashParameter.super.getAsNamed();
  }

  @Override
  default @Nullable PyTupleParameter getAsTuple() {
    return (PyTupleParameter)PyAstSlashParameter.super.getAsTuple();
  }

  @Override
  default @Nullable PyExpression getDefaultValue() {
    return (PyExpression)PyAstSlashParameter.super.getDefaultValue();
  }
}
