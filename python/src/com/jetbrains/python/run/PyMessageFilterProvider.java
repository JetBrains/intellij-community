// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyMessageFilterProvider implements ConsoleFilterProvider {
  @NotNull
  @Override
  public Filter[] getDefaultFilters(@NotNull Project project) {
    return new Filter[]{new PythonTracebackFilter(project)};
  }
}
