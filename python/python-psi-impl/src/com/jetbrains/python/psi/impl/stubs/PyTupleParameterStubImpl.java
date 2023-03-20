// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation does nothing but marking the element type. 
 */
public class PyTupleParameterStubImpl extends StubBase<PyTupleParameter>  implements PyTupleParameterStub {
  @Nullable
  private final String myDefaultValueText;

  protected PyTupleParameterStubImpl(@Nullable String defaultValueText, StubElement parent) {
    super(parent, PyElementTypes.TUPLE_PARAMETER);
    myDefaultValueText = defaultValueText;
  }

  @Nullable
  @Override
  public String getDefaultValueText() {
    return myDefaultValueText;
  }
}
