/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
      final PyFunction functionStub = (PyFunction)pythonStub;
      if (isOverload(functionStub, context)) {
        return getOverloadType(functionStub, context);
      }
      return new PyFunctionTypeImpl(functionStub);
    }
    else if (callable.getContainingFile() instanceof PyiFile && callable instanceof PyFunction) {
      final PyFunction functionStub = (PyFunction)callable;
      if (isOverload(functionStub, context)) {
        return getOverloadType(functionStub, context);
      }
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
      else if (function.getContainingFile() instanceof PyiFile) {
        return getOverloadedCallType(function, callSite, context);
      }
    }

    return null;
  }

  @Nullable
  private static Ref<PyType> getOverloadedCallType(@NotNull PyFunction function,
                                                   @NotNull PyCallSiteExpression callSite,
                                                   @NotNull TypeEvalContext context) {
    if (isOverload(function, context)) {
      final List<PyFunction> overloads = getOverloads(function, context);
      final List<PyType> allReturnTypes = new ArrayList<>();
      final List<PyType> matchedReturnTypes = new ArrayList<>();

      for (PyFunction overload : overloads) {
        final PyType returnType = context.getReturnType(overload);
        if (!PyTypeChecker.hasGenerics(returnType, context)) {
          allReturnTypes.add(returnType);
        }

        final PyExpression receiver = PyTypeChecker.getReceiver(callSite, overload);
        final Map<PyExpression, PyNamedParameter> mapping = mapArguments(callSite, overload, context);
        final Map<PyGenericType, PyType> substitutions = PyTypeChecker.unifyGenericCall(receiver, mapping, context);

        final PyType unifiedType = substitutions != null ? PyTypeChecker.substitute(returnType, substitutions, context) : null;
        if (unifiedType != null) {
          matchedReturnTypes.add(unifiedType);
        }
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
        // XXX: Force the switch to AST for getting the type out of the hint in the comment
        final TypeEvalContext effectiveContext = context.maySwitchToAST(pythonStub) ?
                                                 context : TypeEvalContext.deepCodeInsight(target.getProject());
        return effectiveContext.getType((PyTypedElement)pythonStub);
      }
    }
    return null;
  }

  @Nullable
  private static PyType getOverloadType(@NotNull PyFunction function, @NotNull final TypeEvalContext context) {
    final List<PyFunction> overloads = getOverloads(function, context);
    if (!overloads.isEmpty()) {
      final List<PyType> overloadTypes = new ArrayList<>();
      for (PyFunction overload : overloads) {
        overloadTypes.add(new PyFunctionTypeImpl(overload));
      }
      return PyUnionType.union(overloadTypes);
    }
    return null;
  }

  @NotNull
  private static List<PyFunction> getOverloads(@NotNull PyFunction function, final @NotNull TypeEvalContext context) {
    final ScopeOwner owner = ScopeUtil.getScopeOwner(function);
    final String name = function.getName();
    final List<PyFunction> overloads = new ArrayList<>();
    final Processor<PyFunction> overloadsProcessor = f -> {
      if (name != null && name.equals(f.getName()) && isOverload(f, context)) {
        overloads.add(f);
      }
      return true;
    };
    if (owner instanceof PyClass) {
      final PyClass cls = (PyClass)owner;
      if (name != null) {
        cls.visitMethods(overloadsProcessor, false, context);
      }
    }
    else if (owner instanceof PyFile) {
      final PyFile file = (PyFile)owner;
      for (PyFunction f : file.getTopLevelFunctions()) {
        if (!overloadsProcessor.process(f)) {
          break;
        }
      }
    }
    return overloads;
  }

  private static boolean isOverload(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    if (callable instanceof PyDecoratable) {
      final PyDecoratable decorated = (PyDecoratable)callable;
      final ImmutableSet<PyKnownDecoratorUtil.KnownDecorator> decorators =
        ImmutableSet.copyOf(PyKnownDecoratorUtil.getKnownDecorators(decorated, context));
      if (decorators.contains(PyKnownDecoratorUtil.KnownDecorator.TYPING_OVERLOAD)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static Map<PyExpression, PyNamedParameter> mapArguments(@NotNull PyCallSiteExpression callSite,
                                                                  @NotNull PyFunction function,
                                                                  @NotNull TypeEvalContext context) {
    final List<PyParameter> parameters = Arrays.asList(function.getParameterList().getParameters());
    return PyCallExpressionHelper.mapArguments(callSite, function, parameters, context);
  }
}
