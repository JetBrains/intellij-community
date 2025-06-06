// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.impl

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle
import com.jetbrains.python.newProjectWizard.PyV3BaseProjectSettings
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificSettings
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificUI
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathProvider
import com.jetbrains.python.sdk.add.v2.PySdkCreator
import com.jetbrains.python.sdk.add.v2.PythonSdkPanelBuilderAndSdkCreator
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode
import com.jetbrains.python.util.ShowingMessageErrorSync
import javax.swing.JComponent

internal class PyV3UI<TYPE_SPECIFIC_SETTINGS : PyV3ProjectTypeSpecificSettings> @RequiresEdt constructor(
  baseSettings: PyV3BaseProjectSettings,
  projectNameProvider: ProjectPathProvider,
  specificUiAndSettings: Pair<PyV3ProjectTypeSpecificUI<TYPE_SPECIFIC_SETTINGS>, TYPE_SPECIFIC_SETTINGS>?,
  allowedInterpreterTypes: Set<PythonInterpreterSelectionMode>? = null,
) {

  private val sdkPanelBuilderAndSdkCreator: PythonSdkPanelBuilderAndSdkCreator = PythonSdkPanelBuilderAndSdkCreator(
    onlyAllowedInterpreterTypes = allowedInterpreterTypes,
    errorSink = ShowingMessageErrorSync,
    module = null,
  )

  private val _mainPanel: DialogPanel = panel {
    val checkBoxRow = row {
      checkBox(PyBundle.message("new.project.git")).bindSelected(baseSettings::createGitRepository)
    }.apply { bottomGap(BottomGap.MEDIUM) }
    specificUiAndSettings?.first?.configureUpperPanel(specificUiAndSettings.second, checkBoxRow, this)

    sdkPanelBuilderAndSdkCreator.buildPanel(this, projectNameProvider.projectPathFlows)

    specificUiAndSettings?.first?.advancedSettings?.let {
      collapsibleGroup(PyBundle.message("black.advanced.settings.panel.title")) {
        it(this, specificUiAndSettings.second, projectNameProvider)
      }
    }
  }

  init {
    sdkPanelBuilderAndSdkCreator.onShownInitialization(_mainPanel)
  }

  val mainPanel: JComponent = _mainPanel

  fun applyAndGetSdkCreator(): PySdkCreator {
    _mainPanel.apply()
    return sdkPanelBuilderAndSdkCreator
  }
}
