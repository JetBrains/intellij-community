// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import kotlinx.coroutines.flow.Flow

/**
 * Binds [PROJECT_SPECIFIC_SETTINGS] to Kotlin DSL UI.
 */
interface PyV3ProjectTypeSpecificUI<PROJECT_SPECIFIC_SETTINGS : PyV3ProjectTypeSpecificSettings> {

  /**
   * Upper panel right below the project interpreter path.
   * [checkBoxRow] is a row with "generate git" checkbox.
   * [belowCheckBoxes] is a panel below it
   */
  fun configureUpperPanel(settings: PROJECT_SPECIFIC_SETTINGS, checkBoxRow: Row, belowCheckBoxes: Panel) = Unit

  /**
   * If you need to show something in "advanced settings". You also have a flow with a project name calculated from a project path,
   * you might bind it to the cell
   */
  val advancedSettings: (Panel.(settings: PROJECT_SPECIFIC_SETTINGS, projectName: Flow<@NlsSafe String>) -> Unit)? get() = null
}