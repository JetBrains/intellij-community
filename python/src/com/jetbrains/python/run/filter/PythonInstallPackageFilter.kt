// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.filter

import com.intellij.execution.filters.Filter
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyPsiPackageUtil

class PythonInstallPackageFilter(val project: Project, val editor: EditorImpl? = null) : Filter, DumbAware {
  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val prefix = "ModuleNotFoundError: No module named '"
    if (!line.startsWith(prefix))
      return null
    val moduleName = line.removePrefix(prefix).dropLastWhile { it != '\'' }.dropLast(1)
    val pipPackageName = PyPsiPackageUtil.moduleToPackageName(moduleName)
    val info = InstallPackageButtonItem(project, editor, entireLength - line.length + "ModuleNotFoundError:".length, pipPackageName)
    return Filter.Result(
      listOf(
        info,
        // A hack without which the element will not appear.
        Filter.ResultItem(0, 0, null)
      )
    )
  }
}