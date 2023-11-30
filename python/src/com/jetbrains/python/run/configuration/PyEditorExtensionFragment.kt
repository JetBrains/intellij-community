// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import com.intellij.execution.ui.CommandLinePanel
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.util.Disposer
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.PyRunConfigurationEditorExtension
import com.jetbrains.python.run.PyRunConfigurationEditorFactory
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

class PyEditorExtensionFragment<T : AbstractPythonRunConfiguration<*>> :
  SettingsEditorFragment<T, JPanel>("py.extension", null, null, JPanel(GridBagLayout()), null, null, { true }) {

  private var configurationFactory: PyRunConfigurationEditorFactory? = null
  private val settingsPlaceholder: JPanel = JPanel(GridBagLayout())
  private var currentEditor: SettingsEditor<AbstractPythonRunConfiguration<*>>? = null

  init {
    CommandLinePanel.setMinimumWidth(component(), 500)
    val constrains = GridBagConstraints()
    constrains.fill = GridBagConstraints.HORIZONTAL
    constrains.weightx = 1.0
    constrains.gridx = 0
    component().add(settingsPlaceholder, constrains)
  }

  override fun resetEditorFrom(s: T) {
    updateEditorComponent(s)
    currentEditor?.resetFrom(s)
  }

  override fun applyEditorTo(s: T) {
    val updated = updateEditorComponent(s)
    // Such a complicated logic is needed for correctly updating additional fields if SDK was changed in the current run configuration.
    // It is possible, because `applyEditorTo()` is called frequently enough to get new SDK value and update dependent fields.
    if (currentEditor != null) {
      if (updated) {
        currentEditor?.resetFrom(s)
      }
      else {
        currentEditor?.applyTo(s)
      }
    }
  }

  private fun updateEditorComponent(s: T): Boolean {
    val newConfigurationFactory = PyRunConfigurationEditorExtension.EP_NAME.extensionList.firstNotNullOfOrNull { it.accepts(s) }
    if (newConfigurationFactory == configurationFactory) return false
    configurationFactory = newConfigurationFactory
    currentEditor?.let { oldEditor ->
      settingsPlaceholder.removeAll()
      Disposer.dispose(oldEditor)
    }
    if (newConfigurationFactory != null) {
      val newEditor = newConfigurationFactory.createEditor(s)
      val constrains = GridBagConstraints()
      constrains.fill = GridBagConstraints.HORIZONTAL
      constrains.weightx = 1.0
      constrains.gridx = 0
      settingsPlaceholder.add(newEditor.component, constrains)
      Disposer.register(this, newEditor)
      currentEditor = newEditor
    }
    return true
  }

  override fun isRemovable(): Boolean {
    return false
  }

  override fun isSelected(): Boolean {
    return true
  }

  override fun getAllComponents(): Array<JComponent> {
    return arrayOf(settingsPlaceholder)
  }
}