// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.python.sdk.backend.saveEditedTargetConfiguration
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import javax.swing.JComponent

/**
 * The underlying configurable in [PythonInterpreterDetailsConfigurable] for target interpreters. It edits the target
 * configuration of [interpreter] in place, and saves it with [saveEditedTargetConfiguration]. The binary of an
 * interpreter does not change, so the dialog shows it read-only.
 */
internal class PythonTargetInterpreterDetailsConfigurable(private val interpreter: TargetPythonInterpreter,
                                                          private val targetConfigurable: Configurable)
  : NamedConfigurable<PythonInterpreter>() {
  private var pythonInterpreterPath = interpreter.pythonBinary.pathString

  /**
   * Contains a text field that shows [pythonInterpreterPath].
   */
  private lateinit var pythonInterpreterDetailsDialogPanel: DialogPanel

  override fun isModified(): Boolean = targetConfigurable.isModified || pythonInterpreterDetailsDialogPanel.isModified()

  override fun apply() {
    targetConfigurable.apply()
    pythonInterpreterDetailsDialogPanel.apply()
    interpreter.saveEditedTargetConfiguration()
  }

  override fun getDisplayName(): String = targetConfigurable.displayName

  override fun setDisplayName(name: String?) {
    // We rely on the separate "Rename" action for changing Python interpreter name
  }

  override fun getEditableObject(): PythonInterpreter = interpreter

  override fun getBannerSlogan(): String = PyBundle.message("python.interpreter.banner.slogan", displayName)

  override fun createOptionsPanel(): JComponent {
    pythonInterpreterDetailsDialogPanel = panel {
      val targetConfigurableComponent = targetConfigurable.createComponent()
      if (targetConfigurableComponent != null) {
        row {
          cell(targetConfigurableComponent)
        }
      }
      else {
        // Due to the contract of `com.intellij.openapi.options.UnnamedConfigurable.createComponent()` method this could happen, but it seems
        // to be highly unlikely
        LOG.error("Target configurable of Python interpreter \"${targetConfigurable.displayName}\" returned `null` component")
      }
      row(PyBundle.message("python.interpreter.label")) {
        textField().bindText(::pythonInterpreterPath)
          .align(AlignX.FILL)
          .enabled(false)
      }
    }
    return pythonInterpreterDetailsDialogPanel
  }

  override fun disposeUIResources() {
    targetConfigurable.disposeUIResources()
  }

  companion object {
    private val LOG = logger<PythonTargetInterpreterDetailsConfigurable>()
  }
}