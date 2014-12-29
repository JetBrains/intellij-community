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
class PyAncestorsUtils extends NotNullPredicate<PyClass> {
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
