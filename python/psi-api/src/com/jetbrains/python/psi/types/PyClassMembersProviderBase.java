// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.resolve.PyResolveContext;
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
  public Collection<PyCustomMember> getMembers(PyClassType clazz, PsiElement location, @NotNull TypeEvalContext context) {
    return Collections.emptyList();
  }

  @Override
  public PsiElement resolveMember(PyClassType clazz, String name, @Nullable PsiElement location, @NotNull PyResolveContext resolveContext) {
    final Collection<PyCustomMember> members = getMembers(clazz, location, resolveContext.getTypeEvalContext());
    return resolveMemberByName(members, clazz, name, resolveContext);
  }

  @Nullable
  public static PsiElement resolveMemberByName(Collection<PyCustomMember> members,
                                               PyClassType clazz,
                                               String name,
                                               @NotNull PyResolveContext resolveContext) {
    final PyClass pyClass = clazz.getPyClass();
    for (PyCustomMember member : members) {
      if (member.getName().equals(name)) {
        return member.resolve(pyClass, resolveContext);
      }
    }
    return null;
  }
}
