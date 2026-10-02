// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.pyi;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyOverloadType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.jetbrains.python.psi.PyUtil.as;

@ApiStatus.Internal
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

  @Override
  public @Nullable Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
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
        List<PyCallableType> overloads = ContainerUtil.mapNotNull(PyiUtil.getOverloads(pyFunction, context),
                                                                  f -> as(context.getType(f), PyCallableType.class));
        if (overloads.isEmpty()) return null;
        return Ref.create(new PyOverloadType(overloads, null));
      }
      if (pythonStub instanceof PyTypedElement) {
        return PyTypeUtil.notNullToRef(context.getType((PyTypedElement)pythonStub));
      }
    }
    return null;
  }

  @Override
  public @Nullable PyType getGenericType(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    final PyClass classStub = as(PyiUtil.getPythonStub(cls), PyClass.class);
    if (classStub != null) {
      return new PyTypingTypeProvider().getGenericType(classStub, context);
    }
    return null;
  }

  @Override
  public @NotNull Map<PyType, PyType> getGenericSubstitutions(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    final PyClass classStub = as(PyiUtil.getPythonStub(cls), PyClass.class);
    if (classStub != null) {
      return new PyTypingTypeProvider().getGenericSubstitutions(classStub, context);
    }
    return Collections.emptyMap();
  }
}
