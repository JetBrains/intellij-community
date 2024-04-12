// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject

import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row

interface MainPartUiCustomizer {
  /**
   * [row] with checkboxes
   */
  fun checkBoxSection(row: Row)

  /**
   * [panel] under checkbox row
   */
  fun underCheckBoxSection(panel: Panel)
}