// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.util.Ref;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;

public final class PyCallSignatureTypeProvider extends PyTypeProviderBase {
  @Override
  public Ref<PyType> getParameterType(@NotNull final PyNamedParameter param,
                                      @NotNull final PyFunction func,
                                      @NotNull TypeEvalContext context) {
    final String name = param.getName();
    if (name != null) {
      final String typeName = PySignatureCacheManager.getInstance(param.getProject()).findParameterType(func, name);
      if (typeName != null) {
        final PyType type = PyTypeParser.getTypeByName(param, typeName, context);
        if (type != null) {
          return Ref.create(PyDynamicallyEvaluatedType.create(type));
        }
      }
    }
    return null;
  }

  @Override
  public Ref<PyType> getReturnType(@NotNull final PyCallable callable, @NotNull TypeEvalContext context) {
    if (callable instanceof PyFunction function) {
      PySignature signature = PySignatureCacheManager.getInstance(function.getProject()).findSignature(function);
      if (signature != null && signature.getReturnType() != null) {
        final String typeName = signature.getReturnType().getTypeQualifiedName();
        if (typeName != null) {
          final PyType type = PyTypeParser.getTypeByName(function, typeName, context);
          if (type != null) {
            return Ref.create(PyDynamicallyEvaluatedType.create(type));
          }
        }
      }
    }
    return null;
  }
}
