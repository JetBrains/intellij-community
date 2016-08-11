/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.PyModuleMembersProvider;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyUserSkeletonsModuleMembersProvider extends PyModuleMembersProvider {
  @Nullable
  @Override
  public PsiElement resolveMember(PyFile module, String name) {
    final PyFile moduleSkeleton = PyUserSkeletonsUtil.getUserSkeleton(module);
    if (moduleSkeleton != null) {
      return moduleSkeleton.getElementNamed(name);
    }
    return null;
  }

  @Override
  protected Collection<PyCustomMember> getMembersByQName(PyFile module, String qName) {
   final PyFile moduleSkeleton = PyUserSkeletonsUtil.getUserSkeletonForModuleQName(qName, module);
    if (moduleSkeleton != null) {
      final List<PyCustomMember> results = new ArrayList<>();
      for (PyElement element : moduleSkeleton.iterateNames()) {
        if (element instanceof PsiFileSystemItem) {
          continue;
        }
        final String name = element.getName();
        if (name != null) {
          results.add(new PyCustomMember(name, element));
        }
      }
      return results;
    }
    return Collections.emptyList();
  }
}
