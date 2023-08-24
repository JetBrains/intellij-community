// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import com.intellij.execution.ui.CommandLinePanel
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.execution.util.PathMappingsComponent
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.configuration.AbstractPythonConfigurationFragmentedEditor.Companion.MIN_FRAGMENT_WIDTH
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

class PyPathMappingsEditorFragment<T : AbstractPythonRunConfiguration<*>>(val notifier: PyInterpreterModeNotifier) :
  SettingsEditorFragment<T, JPanel>("py.path.mappings", null, null, JPanel(GridBagLayout()), null, null, { true }) {
  private val pathMappingsComponent = PathMappingsComponent()

  init {
    pathMappingsComponent.labelLocation = BorderLayout.WEST
    CommandLinePanel.setMinimumWidth(component(), MIN_FRAGMENT_WIDTH)
    val constrains = GridBagConstraints()
    constrains.fill = GridBagConstraints.HORIZONTAL
    constrains.weightx = 1.0
    constrains.gridx = 0

    component().add(pathMappingsComponent, constrains)
    pathMappingsComponent.isVisible = notifier.isRemoteSelected()
    notifier.addInterpreterModeListener { isRemote ->
      pathMappingsComponent.isVisible = isRemote
    }
  }

  override fun getAllComponents(): Array<JComponent> {
    return arrayOf(pathMappingsComponent)
  }

  override fun resetEditorFrom(config: T) {
    pathMappingsComponent.setMappingSettings(config.mappingSettings)
  }

  override fun applyEditorTo(s: T) {
    s.mappingSettings = pathMappingsComponent.mappingSettings
  }

  override fun isRemovable(): Boolean = false

  override fun isSelected(): Boolean = true
}

