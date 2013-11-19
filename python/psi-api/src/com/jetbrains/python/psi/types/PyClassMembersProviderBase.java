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
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public class PyClassMembersProviderBase implements PyClassMembersProvider {
  @NotNull
  @Override
  public Collection<PyDynamicMember> getMembers(PyClassType clazz, PsiElement location) {
    return Collections.emptyList();
  }

  @Override
  public PsiElement resolveMember(PyClassType clazz, String name, PsiElement location) {
    final Collection<PyDynamicMember> members = getMembers(clazz, location);
    return resolveMemberByName(members, clazz, name);
  }

  @Nullable
  public static PsiElement resolveMemberByName(Collection<PyDynamicMember> members,
                                               PyClassType clazz,
                                               String name) {
    final PyClass pyClass = clazz.getPyClass();
    for (PyDynamicMember member : members) {
      if (member.getName().equals(name)) {
        return member.resolve(pyClass);
      }
    }
    return null;
  }
}
