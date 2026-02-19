// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyTupleParameter;
import org.jetbrains.annotations.Nullable;

/**
 * Tuple parameter stub, collects nested parameters from stubs.
 */
public interface PyTupleParameterStub extends StubElement<PyTupleParameter> {

  default @Nullable String getDefaultValueText() {
    return null;
  }
}
