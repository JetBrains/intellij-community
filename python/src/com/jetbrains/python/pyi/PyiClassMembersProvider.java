/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyClassMembersProviderBase;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyOverridingAncestorsClassMembersProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyiClassMembersProvider extends PyClassMembersProviderBase implements PyOverridingAncestorsClassMembersProvider {
  @NotNull
  @Override
  public Collection<PyCustomMember> getMembers(@NotNull PyClassType classType, PsiElement location) {
    final PyClass cls = classType.getPyClass();
    final PsiElement pythonStub = PyiUtil.getPythonStub(cls);
    if (pythonStub instanceof PyClass) {
      return getClassMembers((PyClass)pythonStub);
    }
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public PsiElement resolveMember(@NotNull PyClassType classType, @NotNull String name, PsiElement location) {
    final PyClass cls = classType.getPyClass();
    final PsiElement pythonStub = PyiUtil.getPythonStub(cls);
    if (pythonStub instanceof PyClass) {
      return findClassMember((PyClass)pythonStub, name);
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

  private static Collection<PyCustomMember> getClassMembers(@NotNull PyClass cls) {
    final List<PyCustomMember> result = new ArrayList<PyCustomMember>();
    for (PyFunction function : cls.getMethods(false)) {
      final String name = function.getName();
      if (name != null) {
        result.add(new PyCustomMember(name, function));
      }
    }
    for (PyTargetExpression attribute : cls.getInstanceAttributes()) {
      final String name = attribute.getName();
      if (name != null) {
        result.add(new PyCustomMember(name, attribute));
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
