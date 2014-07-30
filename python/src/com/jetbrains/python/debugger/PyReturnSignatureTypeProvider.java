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
package com.jetbrains.python.debugger;

import com.jetbrains.python.psi.Callable;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;


public class PyReturnSignatureTypeProvider extends PyTypeProviderBase {
  public PyType getReturnType(@NotNull final Callable callable, @NotNull TypeEvalContext context) {
    if (callable instanceof PyFunction) {
      PyFunction function = (PyFunction)callable;
      final String typeName = PyReturnSignatureCacheManager.getInstance(function.getProject()).findReturnTypes(function);
      if (typeName != null) {
        final PyType type = PyTypeParser.getTypeByName(function, typeName);
        if (type != null) {
          return PyDynamicallyEvaluatedType.create(type);
        }
      }
    }
    return null;
  }
}
