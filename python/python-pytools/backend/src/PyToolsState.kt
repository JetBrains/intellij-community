// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.pytools.common.PyToolEnabledStateDto
import com.intellij.python.pytools.common.PyToolId
import com.intellij.util.xmlb.annotations.OptionTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

private const val LSP_TOOLS_STORAGE_FILE = "pyLspTools.xml"

/** Backend-authoritative enabled state for Python tools. */
@Service(Service.Level.PROJECT)
@State(name = "PyToolsState", storages = [Storage(LSP_TOOLS_STORAGE_FILE)])
class PyToolsState : PersistentStateComponent<PyToolsState.State> {
  data class ToolEntry(
    @OptionTag
    val enabled: Boolean = false,
  )

  data class State(
    @OptionTag
    val tools: MutableMap<String, ToolEntry> = ConcurrentHashMap(),
  ) {
    internal fun persist(toolId: PyToolId, entry: ToolEntry) {
      if (entry == DEFAULT_TOOL_ENTRY) tools.remove(toolId.value)
      else tools[toolId.value] = entry
    }
  }

  private var state = State()
  private var initialized = false
  private val enabledState = MutableStateFlow<List<PyToolEnabledStateDto>>(emptyList())

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
    initialized = true
    publish()
  }

  fun isInitialized(): Boolean = initialized

  fun initialize(entries: List<PyToolEnabledStateDto>) {
    if (initialized) return
    entries.forEach { state.persist(it.toolId, ToolEntry(it.enabled)) }
    initialized = true
    publish()
  }

  fun getEntry(toolId: PyToolId): ToolEntry = state.tools[toolId.value] ?: DEFAULT_TOOL_ENTRY

  fun isEnabled(toolId: PyToolId): Boolean = getEntry(toolId).enabled

  fun isEnabled(tool: PyTool): Boolean = isEnabled(PyToolId(tool.packageName.name))

  fun setEnabled(toolId: PyToolId, value: Boolean) {
    state.persist(toolId, getEntry(toolId).copy(enabled = value))
    initialized = true
    publish()
  }

  fun setEnabled(tool: PyTool, value: Boolean): Unit = setEnabled(PyToolId(tool.packageName.name), value)

  fun enabledStates(): StateFlow<List<PyToolEnabledStateDto>> = enabledState.asStateFlow()

  private fun publish() {
    enabledState.value = state.tools.map { (id, entry) -> PyToolEnabledStateDto(PyToolId(id), entry.enabled) }
  }

  companion object {
    fun getInstance(project: Project): PyToolsState = project.service()
    private val DEFAULT_TOOL_ENTRY = ToolEntry()
  }
}
