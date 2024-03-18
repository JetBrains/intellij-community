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

import com.google.common.collect.ImmutableSet;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.inspections.unusedLocal.PyUnusedLocalInspection;
import com.jetbrains.python.psi.PythonVisitorFilter;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class PyiVisitorFilter implements PythonVisitorFilter {

  @NotNull
  private static final Set<Class<?>> disabledVisitors = ImmutableSet.of(
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
