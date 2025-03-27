// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.impl

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Placeholder
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.launchOnShow
import com.jetbrains.python.PyBundle
import com.jetbrains.python.newProjectWizard.PyV3BaseProjectSettings
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificSettings
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificUI
import com.jetbrains.python.newProjectWizard.impl.projectPath.ProjectPathImpl
import com.jetbrains.python.sdk.add.v2.PySdkCreator
import com.jetbrains.python.sdk.add.v2.PythonAddNewEnvironmentPanel
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode
import com.jetbrains.python.util.ShowingMessageErrorSync
import javax.swing.JComponent
import javax.swing.SwingUtilities


internal class PyV3UI<TYPE_SPECIFIC_SETTINGS : PyV3ProjectTypeSpecificSettings> @RequiresEdt constructor(
  baseSettings: PyV3BaseProjectSettings,
  projectNameProvider: ProjectPathImpl,
  specificUiAndSettings: Pair<PyV3ProjectTypeSpecificUI<TYPE_SPECIFIC_SETTINGS>, TYPE_SPECIFIC_SETTINGS>?,
  allowedInterpreterTypes: Set<PythonInterpreterSelectionMode>? = null,
) {

  private val sdkPanel = PythonAddNewEnvironmentPanel(projectNameProvider.projectPathFlows, allowedInterpreterTypes, ShowingMessageErrorSync)

  private val _mainPanel: DialogPanel

  init {
    lateinit var mainPanelPlaceholder: Placeholder
    val rootPanel = panel {
      row {
        mainPanelPlaceholder = placeholder().align(Align.FILL)
      }
    }

    SwingUtilities.invokeLater {
      val scopingComponent = SwingUtilities.getWindowAncestor(rootPanel) ?: rootPanel
      scopingComponent.launchOnShow("PyV3UI init") {
        mainPanelPlaceholder.component = panel {
          val checkBoxRow = row {
            checkBox(PyBundle.message("new.project.git")).bindSelected(baseSettings::createGitRepository)
          }
          specificUiAndSettings?.first?.configureUpperPanel(specificUiAndSettings.second, checkBoxRow, this)
          sdkPanel.buildPanel(this, this@launchOnShow)
          specificUiAndSettings?.first?.advancedSettings?.let {
            collapsibleGroup(PyBundle.message("black.advanced.settings.panel.title")) {
              it(this, specificUiAndSettings.second, projectNameProvider)
            }
          }
        }.apply {
          sdkPanel.onShown()
        }
      }
    }

    _mainPanel = rootPanel
  }

  val mainPanel: JComponent = _mainPanel

  fun applyAndGetSdkCreator(): PySdkCreator {
    _mainPanel.apply()
    return sdkPanel
  }
}
