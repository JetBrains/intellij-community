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
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author vlan
 */
public class PyiTypeProvider extends PyTypeProviderBase {
  @Override
  public Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    final String name = param.getName();
    if (name != null) {
      final PsiElement pythonStub = PyiUtil.getPythonStub(func);
      if (pythonStub instanceof PyFunction) {
        final PyFunction functionStub = (PyFunction)pythonStub;
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

  @Nullable
  @Override
  public PyType getCallableType(@NotNull PyCallable callable, @NotNull final TypeEvalContext context) {
    final PsiElement pythonStub = PyiUtil.getPythonStub(callable);
    if (pythonStub instanceof PyFunction) {
      return new PyFunctionTypeImpl((PyFunction)pythonStub);
    }
    else if (callable.getContainingFile() instanceof PyiFile && callable instanceof PyFunction) {
      final PyFunction functionStub = (PyFunction)callable;
      return new PyFunctionTypeImpl(functionStub);
    }
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getCallType(@NotNull PyFunction function, @Nullable PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
    if (callSite != null) {
      final PsiElement pythonStub = PyiUtil.getPythonStub(function);

      if (pythonStub instanceof PyFunction) {
        return getOverloadedCallType((PyFunction)pythonStub, callSite, context);
      }

      return getOverloadedCallType(function, callSite, context);
    }

    return null;
  }

  @Nullable
  private static Ref<PyType> getOverloadedCallType(@NotNull PyFunction function,
                                                   @NotNull PyCallSiteExpression callSite,
                                                   @NotNull TypeEvalContext context) {
    if (PyiUtil.isOverload(function, context)) {
      final List<PyFunction> overloads = PyiUtil.getOverloads(function, context);
      final List<PyType> allReturnTypes = new ArrayList<>();
      final List<PyType> matchedReturnTypes = new ArrayList<>();

      for (PyFunction overload : overloads) {
        final PyType returnType = PyUtil.getReturnTypeToAnalyzeAsCallType(overload, context);
        allReturnTypes.add(PyTypeChecker.substitute(returnType, new HashMap<>(), context));

        final PyCallExpression.PyArgumentsMapping mapping = PyCallExpressionHelper.mapArguments(callSite, overload, context);
        if (!mapping.getUnmappedArguments().isEmpty() || !mapping.getUnmappedParameters().isEmpty()) {
          continue;
        }

        final PyExpression receiver = callSite.getReceiver(overload);
        final Map<PyGenericType, PyType> substitutions = PyTypeChecker.unifyGenericCall(receiver, mapping.getMappedParameters(), context);
        if (substitutions == null) {
          continue;
        }

        final PyType unifiedType = PyTypeChecker.substitute(returnType, substitutions, context);
        matchedReturnTypes.add(unifiedType);
      }

      return Ref.create(PyUnionType.union(matchedReturnTypes.isEmpty() ? allReturnTypes : matchedReturnTypes));
    }

    return null;
  }

  @Override
  public PyType getReferenceType(@NotNull PsiElement target, TypeEvalContext context, @Nullable PsiElement anchor) {
    if (target instanceof PyTargetExpression) {
      final PsiElement pythonStub = PyiUtil.getPythonStub((PyTargetExpression)target);
      if (pythonStub instanceof PyTypedElement) {
        return context.getType((PyTypedElement)pythonStub);
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
