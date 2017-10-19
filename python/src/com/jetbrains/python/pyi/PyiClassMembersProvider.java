// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.pyi;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsClassMembersProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassMembersProviderBase;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyOverridingAncestorsClassMembersProvider;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author vlan
 */
public class PyiClassMembersProvider extends PyClassMembersProviderBase implements PyOverridingAncestorsClassMembersProvider {
  @NotNull
  @Override
  public Collection<PyCustomMember> getMembers(@NotNull PyClassType classType, PsiElement location, @NotNull TypeEvalContext context) {
    final PyClass cls = classType.getPyClass();
    final PsiElement pythonStub = PyiUtil.getPythonStub(cls);
    if (pythonStub instanceof PyClass) {
      return PyUserSkeletonsClassMembersProvider.getClassMembers((PyClass)pythonStub, classType.isDefinition());
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
    final PsiElement pythonStub = PyiUtil.getPythonStub(cls);
    if (pythonStub instanceof PyClass) {
      return PyUserSkeletonsClassMembersProvider.findClassMember((PyClass)pythonStub, name, type.isDefinition());
    }
    return null;
  }
}
