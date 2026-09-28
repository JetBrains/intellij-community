// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation;

import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PythonVisitorFilter;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 * <p>
 * filter out some python inspections and annotations if we're in doctest substitution
 */
public final class PyDoctestVisitorFilter implements PythonVisitorFilter {
  @Override
  public boolean isSupported(final @NotNull Class visitorClass, final @NotNull PsiFile file) {
    if (PyInjectedPythonCodeVisitors.disabledInspections.contains(visitorClass) ||
        PyInjectedPythonCodeVisitors.disabledAnnotators.contains(visitorClass)) {
      return false;
    }
    return !PyInjectedPythonCodeVisitors.isUnresolvedReferenceOutsideTopLevelFile(visitorClass, file);
  }
}
