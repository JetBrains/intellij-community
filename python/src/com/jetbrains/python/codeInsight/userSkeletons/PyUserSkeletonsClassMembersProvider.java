// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.userSkeletons;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassMembersProviderBase;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyOverridingAncestorsClassMembersProvider;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyUserSkeletonsClassMembersProvider extends PyClassMembersProviderBase implements PyOverridingAncestorsClassMembersProvider {
  @NotNull
  @Override
  public Collection<PyCustomMember> getMembers(@NotNull PyClassType classType, PsiElement location, @NotNull TypeEvalContext context) {
    final PyClass cls = classType.getPyClass();
    final PyClass skeleton = PyUserSkeletonsUtil.getUserSkeletonWithContext(cls, context);
    if (skeleton != null) {
      return getClassMembers(skeleton, classType.isDefinition());
    }
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public PsiElement resolveMember(@NotNull PyClassType type,
                                  @NotNull String name,
                                  @Nullable PsiElement location,
                                  @NotNull PyResolveContext resolveContext) {
    final PyClass cls = type.getPyClass();
    final PyClass skeleton = PyUserSkeletonsUtil.getUserSkeletonWithContext(cls, resolveContext.getTypeEvalContext());
    if (skeleton != null) {
      return findClassMember(skeleton, name, type.isDefinition());
    }
    return null;
  }

  public static PsiElement findClassMember(@NotNull PyClass cls, @NotNull String name, boolean isDefinition) {
    final PyFunction function = cls.findMethodByName(name, false, null);
    if (function != null) {
      final PyUtil.MethodFlags methodFlags = PyUtil.MethodFlags.of(function);
      final boolean instanceMethod = methodFlags == null || methodFlags.isInstanceMethod();
      if (isDefinition ^ instanceMethod) {
        return function;
      }
    }
    if (!isDefinition) {
      final PyTargetExpression instanceAttribute = cls.findInstanceAttribute(name, false);
      if (instanceAttribute != null) {
        return instanceAttribute;
      }
    }
    final PyTargetExpression classAttribute = cls.findClassAttribute(name, false, null);
    if (classAttribute != null) {
      return classAttribute;
    }
    return null;
  }

  public static Collection<PyCustomMember> getClassMembers(@NotNull PyClass cls, boolean isDefinition) {
    final List<PyCustomMember> result = new ArrayList<>();
    for (PyFunction function : cls.getMethods()) {
      final String name = function.getName();
      final PyUtil.MethodFlags methodFlags = PyUtil.MethodFlags.of(function);
      final boolean instanceMethod = methodFlags == null || methodFlags.isInstanceMethod();
      if (name != null && (isDefinition ^ instanceMethod)) {
        result.add(new PyCustomMember(name, function));
      }
    }
    if (!isDefinition) {
      for (PyTargetExpression attribute : cls.getInstanceAttributes()) {
        final String name = attribute.getName();
        if (name != null) {
          result.add(new PyCustomMember(name, attribute));
        }
      }
    }
    for (PyTargetExpression attribute : cls.getClassAttributes()) {
      final String name = attribute.getName();
      if (name != null) {
        result.add(new PyCustomMember(name, attribute));
      }
    }
    return result;
  }
}
