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

import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PySequenceExpression extends PyExpression{
  @NotNull
  PyExpression[] getElements();

  /**
   * Calling {@link #getElements()} may take too much time in case of large literals with thousands of elements. If you only need to
   * know whether collection is empty, use this method instead.
   *
   * @return true if sequence expression contains no elements
   */
  boolean isEmpty();
}
