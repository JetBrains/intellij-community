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

import com.intellij.psi.NavigatablePsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PyElement extends NavigatablePsiElement {

  /**
   * An empty array to return cheaply without allocating it anew.
   */
  PyElement[] EMPTY_ARRAY = new PyElement[0];

  @Nullable
  default <R> R acceptTyped(@NotNull PyTypedElementVisitor<R> typedVisitor) {
    final PyTypedElementVisitor.Delegate<R> visitor = typedVisitor.asPlainVisitor();
    accept(visitor);
    return visitor.getResult();
  }

}
