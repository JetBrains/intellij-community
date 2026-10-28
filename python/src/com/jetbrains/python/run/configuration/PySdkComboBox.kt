// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import org.jetbrains.annotations.ApiStatus
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Computable
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.asItem
import com.intellij.python.sdk.backend.isOnTarget
import com.intellij.python.sdk.common.PyInterpreterItem
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams
import com.jetbrains.python.sdk.PySdkListCellRenderer
import java.util.function.Consumer
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady

/**
 * The interpreter combo of a run configuration.
 *
 * It holds [PyInterpreterItem]s rather than SDKs: a row states whether its interpreter can be used, and only the
 * interpreter can answer that. The items are read under a progress, off the EDT. The rows are the interpreters of the
 * `PyProject` of the module of the configuration, or of every `PyProject` of [project] when it has no module, from
 * [PythonInterpreterRegistry].
 */
class PySdkComboBox @ApiStatus.Internal constructor(
  private val project: Project,
  private val addDefault: Boolean,
  private val moduleProvider: Computable<out Module?>,
) : ComboBox<PyInterpreterItem?>(), PyInterpreterModeNotifier {
  private val interpreterModeListeners: MutableList<Consumer<Boolean>> = mutableListOf()

  /** The interpreter of each row, see [initList]. The row for "the interpreter of the module" has none. */
  private var interpreters: Map<PyInterpreterItem, PythonInterpreter> = emptyMap()

  fun reset(config: AbstractPythonRunConfigurationParams) {
    initList()
    selectedItem = config.pythonInterpreter?.let { itemFor(it) }
    setUseModuleSdk(config.isUseModuleSdk)
    setModule(config.module)
    addActionListener {
      updateRemoteInterpreterMode()
    }
    updateRemoteInterpreterMode()
  }

  fun initList() {
    interpreters = readInterpreters {
      val pyProjects = moduleProvider.compute()?.asPyProject()?.let { listOf(it) } ?: project.getPyProjects()
      val registry = PythonInterpreterRegistry.getInstance(project)
      pyProjects.flatMap { registry.interpreters(it) }.distinct().associateBy { it.asItem() }
    }
    val items: MutableList<PyInterpreterItem?> = interpreters.keys.toMutableList()
    if (addDefault) {
      items.add(0, null)
    }
    removeAllItems() // initList is called at least twice: on creation and on reset, so we need to clean it up
    for (item in items) {
      addItem(item)
    }
  }

  fun apply(config: AbstractPythonRunConfigurationParams) {
    config.pythonInterpreter = getSelectedInterpreter()
    config.isUseModuleSdk = isUseModuleSdk()
  }

  fun setModule(module: Module?) {
    updateDefaultInterpreter(module)
    updateRemoteInterpreterMode()
  }

  private fun updateDefaultInterpreter(module: Module?) {
    // A combo cannot suspend, so it peeks at the snapshot. Before the first one, no default interpreter is shown.
    val item = module?.findEvoPyProjectIfReady()?.interpreter?.asItem()
    setRenderer(
      if (item == null) PySdkListCellRenderer()
      else PySdkListCellRenderer(PyBundle.message("python.sdk.rendering.current"), item)
    )
  }

  private fun setUseModuleSdk(useModuleSdk: Boolean) {
    if (selectedItem != null && useModuleSdk) {
      selectedItem = null
    }
  }

  private fun isUseModuleSdk(): Boolean = addDefault && selectedItem == null

  /** The selected interpreter, or the interpreter of the module for the row that stands for it. */
  @ApiStatus.Internal
  fun getSelectedInterpreter(): PythonInterpreter? {
    (selectedItem as? PyInterpreterItem)?.let { return interpreters[it] }
    if (!isUseModuleSdk()) return null
    return moduleProvider.get()?.findEvoPyProjectIfReady()?.interpreter
  }

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

  /** The row of [interpreter], or `null` when the combo holds no row for it. */
  private fun itemFor(interpreter: PythonInterpreter): PyInterpreterItem? = interpreters.entries.firstOrNull { it.value == interpreter }?.key

  private fun <T> readInterpreters(read: suspend () -> T): T =
    // Before the combo box is in a window, the progress cannot use it as the owner.
    // An incomplete list of interpreters is not usable, so the user cannot cancel the read.
    runWithModalProgressBlocking(if (isShowing) ModalTaskOwner.component(this) else ModalTaskOwner.guess(),
                                 PyBundle.message("python.interpreters.reading.interpreters.progress"),
                                 TaskCancellation.nonCancellable()) {
      read()
    }
}
