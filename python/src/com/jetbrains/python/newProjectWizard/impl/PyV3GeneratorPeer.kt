// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.impl

import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectGeneratorPeer
import com.jetbrains.python.newProjectWizard.PyV3BaseProjectSettings
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificSettings
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificUI
import com.jetbrains.python.newProjectWizard.PyV3UIServices
import com.jetbrains.python.newProjectWizard.impl.projectPath.ProjectPathImpl
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode
import javax.swing.JComponent

internal class PyV3GeneratorPeer<TYPE_SPECIFIC_SETTINGS : PyV3ProjectTypeSpecificSettings>(
  baseSettings: PyV3BaseProjectSettings,
  private val specificUiAndSettings: Pair<PyV3ProjectTypeSpecificUI<TYPE_SPECIFIC_SETTINGS>, TYPE_SPECIFIC_SETTINGS>?,
  private val allowedInterpreterTypes: Set<PythonInterpreterSelectionMode>?,
  private val uiServices: PyV3UIServices,
) : ProjectGeneratorPeer<PyV3BaseProjectSettings> {
  private val settings = baseSettings
  private lateinit var panel: PyV3UI<*>


  override fun getComponent(projectPathField: TextFieldWithBrowseButton, checkValid: Runnable): JComponent {
    panel = PyV3UI(settings, ProjectPathImpl(projectPathField, uiServices), specificUiAndSettings, allowedInterpreterTypes)
    return panel.mainPanel
  }

  override fun buildUI(settingsStep: SettingsStep) = Unit

  override fun getSettings(): PyV3BaseProjectSettings {
    settings.sdkCreator = panel.applyAndGetSdkCreator()
    return settings
  }

  override fun validate(): ValidationInfo? = null // We validate UI with Kotlin DSL UI form

  override fun isBackgroundJobRunning(): Boolean = false
}