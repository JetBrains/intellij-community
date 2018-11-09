// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyTupleParameter;
import org.jetbrains.annotations.Nullable;

/**
 * Tuple parameter stub, collects nested parameters from stubs.
 */
public interface PyTupleParameterStub extends StubElement<PyTupleParameter> {

  @Nullable
  default String getDefaultValueText() {
    return null;
  }
}
