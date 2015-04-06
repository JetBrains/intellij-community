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

import com.intellij.openapi.util.Ref;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyCallSignatureTypeProvider extends PyTypeProviderBase {
  public Ref<PyType> getParameterType(@NotNull final PyNamedParameter param,
                                      @NotNull final PyFunction func,
                                      @NotNull TypeEvalContext context) {
    final String name = param.getName();
    if (name != null) {
      final String typeName = PySignatureCacheManager.getInstance(param.getProject()).findParameterType(func, name);
      if (typeName != null) {
        final PyType type = PyTypeParser.getTypeByName(param, typeName);
        if (type != null) {
          final PyType evaluatedType = PyDynamicallyEvaluatedType.create(type);
          return Ref.create(evaluatedType);
        }
      }
    }
    return null;
  }
}
