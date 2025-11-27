// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyDataclassNames.Dataclasses;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

import static com.jetbrains.python.codeInsight.PyDataclassesKt.parseDataclassParameters;

/**
 * Adds members for dataclass-like classes.
 */
public final class PyDataclassClassMembersProvider extends PyClassMembersProviderBase {

  @Override
  public @NotNull Collection<PyCustomMember> getMembers(PyClassType clazz, PsiElement location, @NotNull TypeEvalContext context) {
    PyClass pyClass = clazz.getPyClass();
    PyDataclassParameters dataclassParameters = parseDataclassParameters(pyClass, context);

    return CachedValuesManager.getCachedValue(pyClass, () -> {
      Collection<PyCustomMember> result = new ArrayList<>();

      // Adds member __slots__ caused by dataclass.slots
      if (dataclassParameters != null && dataclassParameters.getSlots()) {
        PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(pyClass);
        PyType strType = builtinCache.getStrType();

        if (strType != null) {
          // creating the following PyTupleType requires caching the result to avoid Idempotency Errors at runtime
          PyTupleType tupleOfStrings = PyTupleType.createHomogeneous(pyClass, strType);
          if (tupleOfStrings != null) {
            String qNameTuple = tupleOfStrings.getPyClass().getQualifiedName();
            PyCustomMember slotsMember = new PyCustomMember(Dataclasses.DUNDER_SLOTS, qNameTuple, ignored -> tupleOfStrings);
            result.add(slotsMember);
          }
        }
      }

      // Adds member __attrs_attrs__ caused by decorator @attrs.define
      boolean hasAttrs = dataclassParameters != null && dataclassParameters.getType() == PyDataclassParameters.PredefinedType.ATTRS;
      if (hasAttrs) {
        PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(pyClass);
        PyClass objectClass = builtinCache.getClass(PyNames.OBJECT);
        result.add(new PyCustomMember("__attrs_attrs__", objectClass).asClassVar());
      }

      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }
}
