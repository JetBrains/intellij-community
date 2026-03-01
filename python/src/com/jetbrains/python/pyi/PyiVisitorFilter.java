// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.pyi;

import com.google.common.collect.ImmutableSet;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.inspections.PyCompatibilityInspection;
import com.jetbrains.python.inspections.PyMissingConstructorInspection;
import com.jetbrains.python.inspections.PyMissingOrEmptyDocstringInspection;
import com.jetbrains.python.inspections.PyPropertyDefinitionInspection;
import com.jetbrains.python.inspections.PyShadowingBuiltinsInspection;
import com.jetbrains.python.inspections.PyStatementEffectInspection;
import com.jetbrains.python.inspections.PyTypeCheckerInspection;
import com.jetbrains.python.inspections.unusedLocal.PyUnusedLocalInspection;
import com.jetbrains.python.psi.PythonVisitorFilter;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class PyiVisitorFilter implements PythonVisitorFilter {

  private static final @NotNull Set<Class<?>> disabledVisitors = ImmutableSet.of(
    PyUnusedLocalInspection.class,
    PyStatementEffectInspection.class,
    PyCompatibilityInspection.class,
    PyMissingOrEmptyDocstringInspection.class,
    PyTypeCheckerInspection.class,
    PyPropertyDefinitionInspection.class,
    PyMissingConstructorInspection.class,
    PyShadowingBuiltinsInspection.class
  );

  @Override
  public boolean isSupported(@NotNull Class visitorClass, @NotNull PsiFile file) {
    return !disabledVisitors.contains(visitorClass);
  }
}
