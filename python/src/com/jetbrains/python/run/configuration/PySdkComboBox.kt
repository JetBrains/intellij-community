// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import org.jetbrains.annotations.ApiStatus
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Computable
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.asItem
import com.intellij.python.sdk.backend.isOnTarget
import com.intellij.python.sdk.common.PyInterpreterItem
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams
import com.jetbrains.python.sdk.PySdkListCellRenderer
import java.util.function.Consumer
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady

/**
 * The interpreter combo of a run configuration.
 *
 * It holds [PyInterpreterItem]s rather than SDKs: a row states whether its interpreter can be used, and only the
 * interpreter can answer that. The rows are the interpreters of the `PyProject` of the module of the configuration, or
 * of every `PyProject` of [project] when it has no module, from [PythonInterpreterRegistry].
 *
 * The combo reads the rows from the [EvoPyProjectModel] snapshot in the background while it is shown, see [initList].
 * The read must not block the EDT: a modal progress over it held the synchronization with the JPS model back, and the
 * read waited for it (PY-93821). Until the read ends, the combo holds only the interpreter of the configuration, so
 * [apply] keeps it.
 */
class PySdkComboBox @ApiStatus.Internal constructor(
  private val project: Project,
  private val addDefault: Boolean,
  private val moduleProvider: Computable<out Module?>,
) : ComboBox<PyInterpreterItem?>(), PyInterpreterModeNotifier {
  private val interpreterModeListeners: MutableList<Consumer<Boolean>> = mutableListOf()

  /** The interpreter of each row, see [initList]. The row for "the interpreter of the module" has none. */
  private var interpreters: Map<PyInterpreterItem, PythonInterpreter> = emptyMap()

  /** Each new value asks for a new read of the rows, see [initList]. */
  private val readRequests = MutableStateFlow(0)

  init {
    showRows(emptyList())
    launchOnShow("PySdkComboBox.readInterpreters") {
      readRequests.collectLatest {
        val module = moduleProvider.compute()
        val read = withContext(Dispatchers.Default) { readInterpreters(module) }
        val selected = getSelectedInterpreter().takeUnless { isUseModuleSdk() }
        // An interpreter that the read does not hold, such as one of another PyProject, keeps its row.
        showRows(if (selected == null || selected in read) read else read + selected)
        selectedItem = selected?.let { itemFor(it) }
        updateRemoteInterpreterMode()
      }
    }
  }

  fun reset(config: AbstractPythonRunConfigurationParams) {
    // The rows of the last read stay. An interpreter that they do not hold is a row of its own until the next read.
    config.pythonInterpreter?.takeIf { itemFor(it) == null }?.let { showRows(interpreters.values + it) }
    initList()
    selectedItem = config.pythonInterpreter?.let { itemFor(it) }
    setUseModuleSdk(config.isUseModuleSdk)
    setModule(config.module)
    addActionListener {
      updateRemoteInterpreterMode()
    }
    updateRemoteInterpreterMode()
  }

  /** Asks for a new read of the rows. The read runs while the combo is shown, and it keeps the selected interpreter. */
  fun initList() {
    readRequests.update { it + 1 }
  }

  private suspend fun readInterpreters(module: Module?): List<PythonInterpreter> {
    val snapshot = EvoPyProjectModel.getInstance(project).snapshot()
    val pyProjects = module?.let { snapshot.forModule(it) }?.let { listOf(it.pyProject) } ?: snapshot.evoPyProjects.map { it.pyProject }.toList()
    val registry = PythonInterpreterRegistry.getInstance(project)
    return pyProjects.flatMap { registry.interpreters(it) }.distinct()
  }

  private fun showRows(rows: List<PythonInterpreter>) {
    interpreters = rows.associateBy { it.asItem() }
    removeAllItems()
    if (addDefault) {
      addItem(null)
    }
    for (item in interpreters.keys) {
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

}
