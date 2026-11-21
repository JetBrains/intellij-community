// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.isOnTarget
import com.jetbrains.python.configuration.interpreter.PyInterpreterComboBox
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.util.function.Consumer
import javax.swing.JPanel

/**
 * The interpreter combo of a run configuration: [PyInterpreterComboBox] of the module of the configuration, without the
 * add button. A run configuration only picks an interpreter.
 *
 * With [addDefault], the combo has the row that stands for the interpreter of the module, see
 * [AbstractPythonRunConfigurationParams.isUseModuleSdk].
 */
class PySdkComboBox @ApiStatus.Internal constructor(
  project: Project,
  private val addDefault: Boolean,
  moduleProvider: Computable<out Module?>,
) : JPanel(BorderLayout()), PyInterpreterModeNotifier {
  private val interpreterModeListeners: MutableList<Consumer<Boolean>> = mutableListOf()

  private val combo = PyInterpreterComboBox(project, { moduleProvider.compute() }, offerModuleInterpreter = addDefault, offerAdd = false)

  init {
    isOpaque = false
    add(combo.component, BorderLayout.CENTER)
    combo.addSelectionListener { updateRemoteInterpreterMode() }
  }

  fun reset(config: AbstractPythonRunConfigurationParams) {
    if (addDefault && config.isUseModuleSdk) combo.selectModuleInterpreter() else combo.select(config.pythonInterpreter)
    updateRemoteInterpreterMode()
  }

  fun apply(config: AbstractPythonRunConfigurationParams) {
    config.pythonInterpreter = getSelectedInterpreter()
    config.isUseModuleSdk = combo.isModuleInterpreterSelected
  }

  /** Reads the rows again after the module of the configuration changed, and keeps the selected row. */
  fun moduleChanged() {
    combo.reloadKeepingSelection()
    updateRemoteInterpreterMode()
  }

  /** The selected interpreter, or the interpreter of the module for the row that stands for it. */
  @ApiStatus.Internal
  fun getSelectedInterpreter(): PythonInterpreter? = combo.selectedInterpreter

  override fun isRemoteSelected(): Boolean = getSelectedInterpreter()?.isOnTarget == true

  private fun updateRemoteInterpreterMode() {
    val isRemote = isRemoteSelected()
    for (listener in interpreterModeListeners) {
      listener.accept(isRemote)
    }
  }

  override fun addInterpreterModeListener(listener: Consumer<Boolean>) {
    interpreterModeListeners.add(listener)
  }
}
