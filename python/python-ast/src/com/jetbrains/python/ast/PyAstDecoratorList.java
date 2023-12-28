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
package com.jetbrains.python.ast;

import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A list of function decorators.
 */
@ApiStatus.Experimental
public interface PyAstDecoratorList extends PyAstElement {
  /**
   * @return decorators of function, in order of declaration (outermost first).
   */
  PyAstDecorator @NotNull [] getDecorators();

  @Nullable
  default PyAstDecorator findDecorator(String name) {
    final PyAstDecorator[] decorators = getDecorators();
    for (PyAstDecorator decorator : decorators) {
      final QualifiedName qName = decorator.getQualifiedName();
      if (qName != null && name.equals(qName.toString())) {
        return decorator;
      }
    }
    return null;
  }
}
