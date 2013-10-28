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
package com.jetbrains.python.codeInsight.userSkeletons;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class PyUserSkeletonsTypeProvider extends PyTypeProviderBase {
  @Override
  public PyType getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    final String name = param.getName();
    if (name != null) {
      final PyFunction functionSkeleton = PyUserSkeletonsUtil.getUserSkeleton(func);
      if (functionSkeleton != null) {
        final PyNamedParameter paramSkeleton = functionSkeleton.getParameterList().findParameterByName(name);
        if (paramSkeleton != null) {
          return context.getType(paramSkeleton);
        }
      }
    }
    return null;
  }

  @Override
  public PyType getReturnType(@NotNull PyFunction function, @Nullable PyQualifiedExpression callSite, @NotNull TypeEvalContext context) {
    final PyFunction functionSkeleton = PyUserSkeletonsUtil.getUserSkeleton(function);
    if (functionSkeleton != null) {
      return functionSkeleton.getReturnType(context, callSite);
    }
    return null;
  }

  @Override
  public PyType getReferenceType(@NotNull PsiElement target, TypeEvalContext context, @Nullable PsiElement anchor) {
    if (target instanceof PyTargetExpression) {
      final PyTargetExpression targetSkeleton = PyUserSkeletonsUtil.getUserSkeleton((PyTargetExpression)target);
      if (targetSkeleton != null) {
        return context.getType(targetSkeleton);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getCallableType(@NotNull Callable callable, @NotNull TypeEvalContext context) {
    final Callable callableSkeleton = PyUserSkeletonsUtil.getUserSkeleton(callable);
    if (callableSkeleton != null) {
      return context.getType(callableSkeleton);
    }
    return null;
  }
}
