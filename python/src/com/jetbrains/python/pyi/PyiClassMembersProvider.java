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
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsClassMembersProvider;
import com.jetbrains.python.psi.PyClass;
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
  public Collection<PyCustomMember> getMembers(@NotNull PyClassType classType, PsiElement location, TypeEvalContext typeEvalContext) {
    final PyClass cls = classType.getPyClass();
    final PsiElement pythonStub = PyiUtil.getPythonStub(cls);
    if (pythonStub instanceof PyClass) {
      return PyUserSkeletonsClassMembersProvider.getClassMembers((PyClass)pythonStub, classType.isDefinition());
    }
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public PsiElement resolveMember(@NotNull PyClassType classType, @NotNull String name, PsiElement location,
                                  @Nullable TypeEvalContext context) {
    final PyClass cls = classType.getPyClass();
    final PsiElement pythonStub = PyiUtil.getPythonStub(cls);
    if (pythonStub instanceof PyClass) {
      return PyUserSkeletonsClassMembersProvider.findClassMember((PyClass)pythonStub, name, classType.isDefinition());
    }
    return null;
  }
}
