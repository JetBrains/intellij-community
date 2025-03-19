// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.pullUp;


import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.NotNullPredicate;
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
  private final @NotNull Set<VirtualFile> mySourceRoots;

  /**
   * Returns list of class parents that are under user control
   *
   * @param pyClass class to  find parents for
   * @return list of parents
   */
  static @NotNull Collection<PyClass> getAncestorsUnderUserControl(final @NotNull PyClass pyClass) {
    final List<PyClass> allAncestors = pyClass.getAncestorClasses(TypeEvalContext.userInitiated(pyClass.getProject(), pyClass.getContainingFile()));
    return Collections2.filter(allAncestors, new PyAncestorsUtils(PyUtil.getSourceRoots(pyClass)));
  }

  private PyAncestorsUtils(final @NotNull Collection<VirtualFile> sourceRoots) {
    mySourceRoots = Sets.newHashSet(sourceRoots);
  }

  @Override
  public boolean applyNotNull(final @NotNull PyClass input) {
    return VfsUtilCore.isUnder(input.getContainingFile().getVirtualFile(), mySourceRoots);
  }
}
