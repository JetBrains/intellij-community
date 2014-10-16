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
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents function parameter list.
 * Date: 29.05.2005
 */
public interface PyParameterList extends PyElement, StubBasedPsiElement<PyParameterListStub> {

  /**
   * Extracts the individual parameters.
   * Note that tuple parameters are flattened by this method.
   * @return a possibly empty array of named paramaters.
   */
  PyParameter[] getParameters();

  @Nullable
  PyNamedParameter findParameterByName(@NotNull String name);


  /**
   * Adds a paramter to list, after all other parameters.
   * @param param what to add
   */
  void addParameter(PyNamedParameter param);


  /**
   * @return true iff this list contains an '*args'-type parameter.
   */
  boolean hasPositionalContainer();
  
  /**
   * @return true iff this list contains a '**kwargs'-type parameter.
   */
  boolean hasKeywordContainer();

  String getPresentableText(boolean includeDefaultValue);

  @Nullable
  PyFunction getContainingFunction();
}
