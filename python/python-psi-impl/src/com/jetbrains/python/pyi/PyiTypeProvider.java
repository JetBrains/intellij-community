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
package com.jetbrains.python.pyi;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

import static com.jetbrains.python.psi.PyUtil.as;

public final class PyiTypeProvider extends PyTypeProviderBase {
  @Override
  public Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    final String name = param.getName();
    if (name != null) {
      final PsiElement pythonStub = PyiUtil.getPythonStub(func);
      if (pythonStub instanceof PyFunction functionStub) {
        final PyNamedParameter paramSkeleton = functionStub.getParameterList().findParameterByName(name);
        if (paramSkeleton != null) {
          final PyType type = context.getType(paramSkeleton);
          if (type != null) {
            return Ref.create(type);
          }
        }
      }
      // TODO: Allow the stub for a function to be defined as a class or a target expression alias
    }
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    final PsiElement pythonStub = PyiUtil.getPythonStub(callable);
    if (pythonStub instanceof PyCallable) {
      final PyType type = context.getReturnType((PyCallable)pythonStub);
      if (type != null) {
        return Ref.create(type);
      }
    }
    return null;
  }

  @Override
  public Ref<PyType> getReferenceType(@NotNull PsiElement target, @NotNull TypeEvalContext context, @Nullable PsiElement anchor) {
    if (target instanceof PyElement) {
      final PsiElement pythonStub = PyiUtil.getPythonStub((PyElement)target);
      if (pythonStub instanceof PyFunction pyFunction) {
        PyType allSignatures = StreamEx.ofNullable(PyiUtil.getImplementation(pyFunction, context))
          .prepend(PyiUtil.getOverloads(pyFunction, context))
          .map(context::getType)
          .collect(PyTypeUtil.toUnion());
        return PyTypeUtil.notNullToRef(allSignatures);
      }
      if (pythonStub instanceof PyTypedElement) {
        return PyTypeUtil.notNullToRef(context.getType((PyTypedElement)pythonStub));
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getGenericType(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    final PyClass classStub = as(PyiUtil.getPythonStub(cls), PyClass.class);
    if (classStub != null) {
      return new PyTypingTypeProvider().getGenericType(classStub, context);
    }
    return null;
  }

  @NotNull
  @Override
  public Map<PyType, PyType> getGenericSubstitutions(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    final PyClass classStub = as(PyiUtil.getPythonStub(cls), PyClass.class);
    if (classStub != null) {
      return new PyTypingTypeProvider().getGenericSubstitutions(classStub, context);
    }
    return Collections.emptyMap();
  }
}
