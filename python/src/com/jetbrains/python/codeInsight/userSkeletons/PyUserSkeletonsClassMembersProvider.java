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
import com.jetbrains.python.codeInsight.PyDynamicMember;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyClassMembersProviderBase;
import com.jetbrains.python.psi.types.PyClassType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyUserSkeletonsClassMembersProvider extends PyClassMembersProviderBase {
  @NotNull
  @Override
  public Collection<PyDynamicMember> getMembers(@NotNull PyClassType classType, PsiElement location) {
    final PyClass cls = classType.getPyClass();
    final PyClass skeleton = PyUserSkeletonsUtil.getUserSkeleton(cls);
    if (skeleton != null) {
      return getClassMembers(skeleton);
    }
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public PsiElement resolveMember(@NotNull PyClassType classType, @NotNull String name, PsiElement location) {
    final PyClass cls = classType.getPyClass();
    final PyClass skeleton = PyUserSkeletonsUtil.getUserSkeleton(cls);
    if (skeleton != null) {
      return findClassMember(skeleton, name);
    }
    return null;
  }

  private static PsiElement findClassMember(@NotNull PyClass cls, @NotNull String name) {
    final PyFunction function = cls.findMethodByName(name, false);
    if (function != null) {
      return function;
    }
    final PyTargetExpression instanceAttribute = cls.findInstanceAttribute(name, false);
    if (instanceAttribute != null) {
      return instanceAttribute;
    }
    final PyTargetExpression classAttribute = cls.findClassAttribute(name, false);
    if (classAttribute != null) {
      return classAttribute;
    }
    return null;
  }

  private static Collection<PyDynamicMember> getClassMembers(@NotNull PyClass cls) {
    final List<PyDynamicMember> result = new ArrayList<PyDynamicMember>();
    for (PyFunction function : cls.getMethods()) {
      final String name = function.getName();
      if (name != null) {
        result.add(new PyDynamicMember(name, function));
      }
    }
    for (PyTargetExpression attribute : cls.getInstanceAttributes()) {
      final String name = attribute.getName();
      if (name != null) {
        result.add(new PyDynamicMember(name, attribute));
      }
    }
    for (PyTargetExpression attribute : cls.getClassAttributes()) {
      final String name = attribute.getName();
      if (name != null) {
        result.add(new PyDynamicMember(name, attribute));
      }
    }
    return result;
  }
}
