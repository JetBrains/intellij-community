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

  /**
   * Get members for this class type only: no need to read its ancestors since it is duty of this method client
   */
  @NotNull
  @Override
  public Collection<PyCustomMember> getMembers(PyClassType clazz, PsiElement location, @NotNull TypeEvalContext context) {
    return Collections.emptyList();
  }

  /**
   * {@inheritDoc}
   * @deprecated Use {@link PyClassMembersProvider#resolveMember(PyClassType, String, PsiElement, PyResolveContext)} instead.
   * This method will be removed in 2018.2.
   */
  @Nullable
  @Override
  @Deprecated
  public PsiElement resolveMember(PyClassType clazz, String name, @Nullable PsiElement location, @NotNull TypeEvalContext context) {
    final Collection<PyCustomMember> members = getMembers(clazz, location, context);
    return resolveMemberByName(members, clazz, name);
  }

  @Override
  @Nullable
  public PsiElement resolveMember(@NotNull PyClassType type,
                                  @NotNull String name,
                                  @Nullable PsiElement location,
                                  @NotNull PyResolveContext resolveContext) {
    return resolveMember(type, name, location, resolveContext.getTypeEvalContext());
  }

  /**
   * Helper to find member with specified name in collection.
   *
   * @param members collection of members
   * @param clazz   type to be used to get class as psi context
   * @param name    member name to look for
   * @return found member or null
   * @deprecated Use {@link PyClassMembersProviderBase#resolveMemberByName(Collection, String, PsiElement, PyResolveContext)} instead.
   * This method will be removed in 2018.2.
   */
  @Nullable
  @Deprecated
  public static PsiElement resolveMemberByName(Collection<PyCustomMember> members,
                                               PyClassType clazz,
                                               String name) {
    final PyClass pyClass = clazz.getPyClass();
    for (PyCustomMember member : members) {
      if (member.getName().equals(name)) {
        return member.resolve(pyClass);
      }
    }
    return null;
  }

  /**
   * Helper to find member with specified name in collection.
   *
   * @param members collection of members
   * @param name    member name to look for
   * @param context psi element to be used as psi context
   * @return found member or null
   */
  @Nullable
  public static PsiElement resolveMemberByName(@NotNull Collection<PyCustomMember> members,
                                               @NotNull String name,
                                               @NotNull PsiElement context,
                                               @NotNull PyResolveContext resolveContext) {
    for (PyCustomMember member : members) {
      if (member.getName().equals(name)) {
        return member.resolve(context, resolveContext);
      }
    }
    return null;
  }
}
