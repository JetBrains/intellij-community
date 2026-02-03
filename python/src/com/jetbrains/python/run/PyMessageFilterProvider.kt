// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project
import com.jetbrains.python.run.filter.PythonInstallPackageFilter

class PyMessageFilterProvider : ConsoleFilterProvider {
  override fun getDefaultFilters(project: Project): Array<Filter> = arrayOf(
    PythonTracebackFilter(project),
    PythonInstallPackageFilter(project)
  )
}