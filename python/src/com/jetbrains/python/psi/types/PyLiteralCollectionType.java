/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyLiteralCollectionType extends PyClassTypeImpl implements PyCollectionType {
  private final PySequenceExpression mySequence;

  public PyLiteralCollectionType(@NotNull PyClass source, boolean isDefinition, PySequenceExpression sequence) {
    super(source, isDefinition);
    mySequence = sequence;
  }

  @Override
  public PyType getElementType(@NotNull TypeEvalContext context) {
    final PyExpression[] elements = mySequence.getElements();
    if (elements.length == 0 || elements.length > 10 /* performance */) {
      return null;
    }
    PyType result = context.getType(elements [0]);
    if (result == null) {
      return null;
    }
    for (int i = 1; i < elements.length; i++) {
      PyType elementType = context.getType(elements[i]);
      if (elementType == null || !elementType.equals(result)) {
        return null;
      }
    }
    return result;
  }
}
