// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.impl

import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectGeneratorPeer
import com.jetbrains.python.newProjectWizard.PyV3BaseProjectSettings
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificSettings
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificUI
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path
import javax.swing.JComponent

class PyV3GeneratorPeer<TYPE_SPECIFIC_SETTINGS : PyV3ProjectTypeSpecificSettings>(
  baseSettings: PyV3BaseProjectSettings,
  projectPath: StateFlow<Path>,
  specificUiAndSettings: Pair<PyV3ProjectTypeSpecificUI<TYPE_SPECIFIC_SETTINGS>, TYPE_SPECIFIC_SETTINGS>?,
  allowedInterpreterTypes:Set<PythonInterpreterSelectionMode>?
) : ProjectGeneratorPeer<PyV3BaseProjectSettings> {
  private val settings = baseSettings
  private val panel: Py3VUI<*> = Py3VUI(settings, projectPath, specificUiAndSettings, allowedInterpreterTypes)


  override fun getComponent(): JComponent = panel.mainPanel

  override fun buildUI(settingsStep: SettingsStep) = Unit

  override fun getSettings(): PyV3BaseProjectSettings {
    settings.sdkCreator = panel.applyAndGetSdkCreator()
    return settings
  }

  override fun validate(): ValidationInfo? = null // We validate UI with Kotlin DSL UI form

  override fun isBackgroundJobRunning(): Boolean = false
}