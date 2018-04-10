/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyOverridingTypeProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PyCollectionTypeByModificationsProvider extends PyTypeProviderBase implements PyOverridingTypeProvider {

  @Nullable
  @Override
  public Ref<PyType> getCallType(@NotNull PyFunction function, @Nullable PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
    String qualifiedName = function.getQualifiedName();
    if (qualifiedName != null && PyCollectionTypeUtil.INSTANCE.getCOLLECTION_CONSTRUCTORS().contains(qualifiedName) && callSite != null) {
      PyExpression target = PyCollectionTypeUtil.INSTANCE.getTargetForValueInAssignment(callSite);
      if (target instanceof PyTargetExpression) {
        List<PyExpression> arguments = callSite.getArguments(null);
        List<PyType> argumentTypes = getTypesFromConstructorArguments(context, arguments);

        PyTargetExpression element = (PyTargetExpression)target;
        ScopeOwner owner = ScopeUtil.getScopeOwner(element);
        if (owner != null) {
          final List<PyType> typesByModifications = PyCollectionTypeUtil.INSTANCE
            .getCollectionTypeByModifications(qualifiedName, element, context);
          if (!typesByModifications.isEmpty()) {
            if (qualifiedName.equals(PyCollectionTypeUtil.INSTANCE.getDICT_CONSTRUCTOR())) {
              argumentTypes = extractTypesForDict(argumentTypes, typesByModifications);
            }
            else {
              argumentTypes.addAll(typesByModifications);
              argumentTypes = Collections.singletonList(PyUnionType.union(argumentTypes));
            }

            final PyClass cls = function.getContainingClass();
            if (cls != null) {
              return Ref.create(new PyCollectionTypeImpl(cls, false, argumentTypes));
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static List<PyType> getTypesFromConstructorArguments(@NotNull TypeEvalContext context,
                                                               @NotNull List<PyExpression> arguments) {
    List<PyType> argumentTypes = new ArrayList<>();
    final PyExpression arg = ContainerUtil.getFirstItem(arguments);
    if (arg != null) {
      PyType type = context.getType(arg);
      if (type instanceof PyCollectionType) {
        argumentTypes.addAll(((PyCollectionType)type).getElementTypes());
      }
      else {
        argumentTypes.add(type);
      }
    }
    return argumentTypes;
  }

  @NotNull
  private static List<PyType> extractTypesForDict(@NotNull List<PyType> argumentTypes, @NotNull List<PyType> typesByModifications) {
    if (argumentTypes.size() == 1) {
      final PyType type = ContainerUtil.getFirstItem(argumentTypes);
      if (type instanceof PyTupleType) {
        PyTupleType tuple = (PyTupleType)type;
        argumentTypes = tuple.getElementTypes();
      }
      else if (type == null) {
        argumentTypes.add(null);
      }
    }
    if (typesByModifications.size() == 2) {
      if (argumentTypes.size() == 2) {
        argumentTypes.set(0, PyUnionType.union(argumentTypes.get(0), typesByModifications.get(0)));
        argumentTypes.set(1, PyUnionType.union(argumentTypes.get(1), typesByModifications.get(1)));
      }
      else {
        argumentTypes = typesByModifications;
      }
    }
    return argumentTypes;
  }
}
