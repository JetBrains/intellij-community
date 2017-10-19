// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.userSkeletons;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyModuleMembersProvider;
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
public class PyUserSkeletonsModuleMembersProvider extends PyModuleMembersProvider {
  @Nullable
  @Override
  public PsiElement resolveMember(@NotNull PyFile module, @NotNull String name, @NotNull PyResolveContext resolveContext) {
    final PyFile moduleSkeleton = PyUserSkeletonsUtil.getUserSkeleton(module);
    if (moduleSkeleton != null) {
      return moduleSkeleton.getElementNamed(name);
    }
    return null;
  }

  @Override
  protected Collection<PyCustomMember> getMembersByQName(PyFile module, String qName) {
    // This method will be removed in 2018.2
    return getMembersByQName(module, qName, TypeEvalContext.codeInsightFallback(module.getProject()));
  }

  @Override
  @NotNull
  protected Collection<PyCustomMember> getMembersByQName(@NotNull PyFile module, @NotNull String qName, @NotNull TypeEvalContext context) {
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
