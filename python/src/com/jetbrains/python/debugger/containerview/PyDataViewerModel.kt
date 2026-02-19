// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyFrameAccessor

class PyDataViewerModel(
  val project: Project,
  val frameAccessor: PyFrameAccessor,
) {
  /**
   * Represents a formatting string used for specifying format.
   *
   * This field is used as a source of truth during switching between community and powerful tables.
   */
  var format: String = ""

  /**
   * Represents a slicing string used for slicing data in a viewer panel.
   * For example, np_array_3d[0] or df['column_1'].
   *
   * This field is used as a source of truth during switching between community and powerful tables.
   */
  var slicing: String = ""

  /**
   * This field is used as a source of truth during switching between community and powerful tables.
   */
  var isColored: Boolean = false

  var protectedColored: Boolean = PyDataView.isColoringEnabled(project)

  var originalVarName: @NlsSafe String? = null

  var modifiedVarName: String? = null

  var debugValue: PyDebugValue? = null

  var isModified: Boolean = false
}