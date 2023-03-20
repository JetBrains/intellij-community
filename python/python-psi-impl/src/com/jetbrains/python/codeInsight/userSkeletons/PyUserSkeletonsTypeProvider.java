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
package com.jetbrains.python.codeInsight.userSkeletons;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyUserSkeletonsTypeProvider extends PyTypeProviderBase {
  @Override
  public Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    if (PyiUtil.isInsideStub(param)) {
      return null;
    }
    final String name = param.getName();
    if (name != null) {
      final PyFunction functionSkeleton = PyUserSkeletonsUtil.getUserSkeletonWithContext(func, context);
      if (functionSkeleton != null) {
        final PyNamedParameter paramSkeleton = functionSkeleton.getParameterList().findParameterByName(name);
        if (paramSkeleton != null) {
          final PyType type = context.getType(paramSkeleton);
          if (type != null) {
            return Ref.create(type);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    if (PyiUtil.isInsideStub(callable)) {
      return null;
    }
    final PyCallable callableSkeleton = PyUserSkeletonsUtil.getUserSkeletonWithContext(callable, context);
    if (callableSkeleton != null) {
      return Ref.create(context.getReturnType(callableSkeleton));
    }
    return null;
  }

  @Override
  public Ref<PyType> getReferenceType(@NotNull PsiElement target, @NotNull TypeEvalContext context, @Nullable PsiElement anchor) {
    if (PyiUtil.isInsideStub(target)) {
      return null;
    }
    if (target instanceof PyTargetExpression) {
      final PyTargetExpression targetSkeleton = PyUserSkeletonsUtil.getUserSkeletonWithContext((PyTargetExpression)target, context);
      if (targetSkeleton != null) {
        return PyTypeUtil.notNullToRef(context.getType(targetSkeleton));
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getCallableType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    if (PyiUtil.isInsideStub(callable)) {
      return null;
    }
    final PyCallable callableSkeleton = PyUserSkeletonsUtil.getUserSkeletonWithContext(callable, context);
    if (callableSkeleton != null) {
      return context.getType(callableSkeleton);
    }
    return null;
  }
}
