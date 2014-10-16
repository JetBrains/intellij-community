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
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PySubscriptableType extends PyType {

  /**
   * Access elements by a constant Python expression
   * @param index an expression that should evaluate to integer, zero-based
   * @param context for type evaluation (think caching)
   * @return type of item
   */
  @Nullable
  PyType getElementType(PyExpression index, TypeEvalContext context);

  /**
   * Access elements by zero-based index.
   * @param index
   * @return type of item
   */
  @Nullable
  PyType getElementType(int index);

  int getElementCount();
}
