/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.documentation.docstrings;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParser;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class PyDocStringTypeProvider extends PyTypeProviderBase {
  @Override
  public Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    StructuredDocString docString = func.getStructuredDocString();
    if (PyNames.INIT.equals(func.getName()) && docString == null) {
      final PyClass pyClass = func.getContainingClass();
      if (pyClass != null) {
        docString = pyClass.getStructuredDocString();
      }
    }
    if (docString != null) {
      final String typeText = docString.getParamType(param.getName());
      if (StringUtil.isNotEmpty(typeText)) {
        return parseType(func, typeText);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    if (callable instanceof PyDocStringOwner) {
      final StructuredDocString docString = ((PyDocStringOwner)callable).getStructuredDocString();
      if (docString != null) {
        final String typeText = docString.getReturnType();
        if (StringUtil.isNotEmpty(typeText)) {
          return parseType(callable, typeText);
        }
      }
    }
    return null;
  }

  @NotNull
  private static Ref<PyType> parseType(@NotNull PyCallable callable, String typeText) {
    final PyType type = PyTypeParser.getTypeByName(callable, typeText);
    if (type != null) {
      type.assertValid("from docstring");
    }
    return Ref.create(type);
  }
}
