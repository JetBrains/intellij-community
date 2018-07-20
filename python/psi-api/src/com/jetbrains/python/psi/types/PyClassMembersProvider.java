// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Dennis.Ushakov
 */
public interface PyClassMembersProvider {
  ExtensionPointName<PyClassMembersProvider> EP_NAME = ExtensionPointName.create("Pythonid.pyClassMembersProvider");

  @NotNull
  Collection<PyCustomMember> getMembers(final PyClassType clazz, PsiElement location, @NotNull TypeEvalContext context);

  /**
   * Provides member with specified name for specified class type.
   *
   * @param type           member owner
   * @param name           member name
   * @param location       psi element to be used as psi context
   * @param resolveContext context to be used in resolve
   * @return provided member
   */
  @Nullable
  PsiElement resolveMember(@NotNull PyClassType type,
                           @NotNull String name,
                           @Nullable PsiElement location,
                           @NotNull PyResolveContext resolveContext);
}
