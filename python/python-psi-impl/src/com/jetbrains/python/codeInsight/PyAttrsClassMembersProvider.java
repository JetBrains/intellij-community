// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassMembersProviderBase;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.codeInsight.PyDataclassesKt.parseDataclassParameters;

/**
 * Adds member __attrs_attrs__ caused by decorator @attrs.define
 */
public final class PyAttrsClassMembersProvider extends PyClassMembersProviderBase {

  @Override
  public @NotNull Collection<PyCustomMember> getMembers(PyClassType clazz, PsiElement location, @NotNull TypeEvalContext context) {
    PyClass pyClass = clazz.getPyClass();
    PyDataclassParameters dataclassParameters = parseDataclassParameters(pyClass, context);
    boolean hasAttrs = dataclassParameters != null && dataclassParameters.getType() == PyDataclassParameters.PredefinedType.ATTRS;
    if (hasAttrs) {
      PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(pyClass);
      PyClass objectClass = builtinCache.getClass(PyNames.OBJECT);
      return List.of(new PyCustomMember("__attrs_attrs__", objectClass));
    }
    return Collections.emptyList();
  }

}
