// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.classes.pullUp;


import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Ilya.Kazakevich
 */
final class PyAncestorsUtils extends NotNullPredicate<PyClass> {
  @NotNull
  private final Set<VirtualFile> mySourceRoots;

  /**
   * Returns list of class parents that are under user control
   *
   * @param pyClass class to  find parents for
   * @return list of parents
   */
  @NotNull
  static Collection<PyClass> getAncestorsUnderUserControl(@NotNull final PyClass pyClass) {
    final List<PyClass> allAncestors = pyClass.getAncestorClasses(TypeEvalContext.userInitiated(pyClass.getProject(), pyClass.getContainingFile()));
    return Collections2.filter(allAncestors, new PyAncestorsUtils(PyUtil.getSourceRoots(pyClass)));
  }

  private PyAncestorsUtils(@NotNull final Collection<VirtualFile> sourceRoots) {
    mySourceRoots = Sets.newHashSet(sourceRoots);
  }

  @Override
  public boolean applyNotNull(@NotNull final PyClass input) {
    return VfsUtilCore.isUnder(input.getContainingFile().getVirtualFile(), mySourceRoots);
  }
}
