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
package com.jetbrains.python.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Something that can be called, passed parameters to, and return something back.
 */
@ApiStatus.Experimental
public interface PyAstCallable extends PyAstTypedElement, PyAstQualifiedNameOwner {

  /**
   * @return a list of parameters passed to this callable, possibly empty.
   */
  @NotNull
  PyAstParameterList getParameterList();

  /**
   * @return a methods returns itself, non-method callables return null.
   */
  @Nullable
  PyAstFunction asMethod();
}
