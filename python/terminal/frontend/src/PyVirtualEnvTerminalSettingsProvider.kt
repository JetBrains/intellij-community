package com.intellij.python.terminal.frontend

import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.python.terminal.shared.PyTerminalBundle
import com.intellij.python.terminal.shared.PyVirtualEnvTerminalSettings
import com.intellij.terminal.frontend.settings.TerminalSettingsProvider
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected

/** Adds the "Activate virtualenv" checkbox to the Terminal settings page. */
internal class PyVirtualEnvTerminalSettingsProvider : TerminalSettingsProvider {
  override fun createConfigurable(project: Project): UnnamedConfigurable = object : UiDslUnnamedConfigurable.Simple() {
    override fun Panel.createContent() {
      val settings = PyVirtualEnvTerminalSettings.getInstance(project)
      row {
        checkBox(PyTerminalBundle.message("activate.virtualenv.checkbox.text")).bindSelected(settings::virtualEnvActivate)
      }
    }
  }
}