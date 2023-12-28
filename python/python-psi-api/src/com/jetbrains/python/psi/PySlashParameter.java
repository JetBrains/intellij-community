// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Nullable
  default PyNamedParameter getAsNamed() {
    return (PyNamedParameter)PyAstSlashParameter.super.getAsNamed();
  }

  @Override
  @Nullable
  default PyTupleParameter getAsTuple() {
    return (PyTupleParameter)PyAstSlashParameter.super.getAsTuple();
  }

  @Override
  @Nullable
  default PyExpression getDefaultValue() {
    return (PyExpression)PyAstSlashParameter.super.getDefaultValue();
  }

  @Override
  default boolean isSelf() {
    return false;
  }
}
