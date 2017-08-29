/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation does nothing but marking the element type. 
 * User: dcheryasov
 * Date: Jul 6, 2009 1:33:08 AM
 */
public class PyTupleParameterStubImpl extends StubBase<PyTupleParameter>  implements PyTupleParameterStub {
  private final boolean myHasDefaultValue;
  @Nullable
  private final String myDefaultValueText;

  protected PyTupleParameterStubImpl(boolean hasDefaultValue, @Nullable String defaultValueText, StubElement parent) {
    super(parent, PyElementTypes.TUPLE_PARAMETER);
    myHasDefaultValue = hasDefaultValue;
    myDefaultValueText = defaultValueText;
  }

  @Override
  public boolean hasDefaultValue() {
    return myHasDefaultValue;
  }

  @Nullable
  @Override
  public String getDefaultValueText() {
    return myDefaultValueText;
  }
}
