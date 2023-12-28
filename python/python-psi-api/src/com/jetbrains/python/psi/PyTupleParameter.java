/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  @Nullable
  default PyNamedParameter getAsNamed() {
    return (PyNamedParameter)PyAstTupleParameter.super.getAsNamed();
  }

  @Override
  @NotNull
  default PyTupleParameter getAsTuple() {
    return (PyTupleParameter)PyAstTupleParameter.super.getAsTuple();
  }

  @Override
  @Nullable
  default PyExpression getDefaultValue() {
    return (PyExpression)PyAstTupleParameter.super.getDefaultValue();
  }

  @Override
  default boolean isSelf() {
    return false;
  }

  /**
   * @return the nested parameters within this tuple parameter.
   */
  @Override
  PyParameter @NotNull [] getContents();
}
