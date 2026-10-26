// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.impl

import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.platform.eel.EelApi
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Placeholder
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.newProjectWizard.PyV3BaseProjectSettings
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificSettings
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificUI
import com.jetbrains.python.newProjectWizard.PyV3UIServices
import com.jetbrains.python.newProjectWizard.impl.projectPath.ProjectPathImpl
import javax.swing.JComponent

internal class PyV3GeneratorPeer<TYPE_SPECIFIC_SETTINGS : PyV3ProjectTypeSpecificSettings>(
  baseSettings: PyV3BaseProjectSettings,
  private val specificUiAndSettings: Pair<PyV3ProjectTypeSpecificUI<TYPE_SPECIFIC_SETTINGS>, TYPE_SPECIFIC_SETTINGS>?,
  private val uiServices: PyV3UIServices,
  eel: EelApi,
) : ProjectGeneratorPeer<PyV3BaseProjectSettings> {
  private val settings = baseSettings
  private lateinit var pyV3UI: PyV3UI<*>
  private lateinit var projectPathField: TextFieldWithBrowseButton
  private lateinit var uiPlaceholder: Placeholder

  /**
   * Holds the [PyV3UI.mainPanel] of the current [eel] in [uiPlaceholder].
   * The platform registers the validators of this panel, and the placeholder integrates each new [PyV3UI.mainPanel] into them.
   */
  private val panel: DialogPanel = panel {
    row {
      uiPlaceholder = placeholder().align(Align.FILL)
    }
  }

  /**
   * Lives as long as [pyV3UI]. Disposed when [showUIForEel] replaces the UI.
   */
  private var uiDisposable: Disposable? = null

  var eel: EelApi = eel
    private set


  override fun getComponent(projectPathField: TextFieldWithBrowseButton, checkValid: Runnable): JComponent {
    this.projectPathField = projectPathField
    showUIForEel(eel)
    return panel
  }

  fun showUIForEel(eel: EelApi) {
    this.eel = eel
    uiDisposable?.let { Disposer.dispose(it) }
    val uiDisposable = Disposer.newDisposable(projectPathField).also { this.uiDisposable = it }
    val projectPath = ProjectPathImpl(projectPathField, uiServices, eel.descriptor, parentDisposable = uiDisposable)
    pyV3UI = PyV3UI(settings, projectPath, specificUiAndSettings, eel = eel)
    uiPlaceholder.component = pyV3UI.mainPanel
  }

  override fun buildUI(settingsStep: SettingsStep) = Unit

  override fun getSettings(): PyV3BaseProjectSettings {
    settings.sdkCreator = pyV3UI.applyAndGetSdkCreator()
    return settings
  }

  override fun validate(): ValidationInfo? = null // We validate UI with Kotlin DSL UI form

  override fun isBackgroundJobRunning(): Boolean = false
}