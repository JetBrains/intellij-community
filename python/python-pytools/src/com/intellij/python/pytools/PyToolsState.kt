// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.python.pytools.lsp.LSP_TOOLS_STORAGE_FILE
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path

/**
 * Central per-project state for all Python tools that participate in the [PyTool] extension point.
 *
 * One [ToolEntry] per tool, keyed by [PyTool.fusId]; a tool absent from [State.tools] is at its defaults.
 * The first time a project has no stored state (see [noStateLoaded]) each tool's pre-existing configuration is
 * imported once via [PyTool.migrateLegacyState], which also clears the old settings so the migration is one-way and can
 * never resurrect stale values after a reset to defaults.
 */
@Service(Service.Level.PROJECT)
@State(name = "PyToolsState", storages = [Storage(LSP_TOOLS_STORAGE_FILE)])
class PyToolsState(private val project: Project) : PersistentStateComponent<PyToolsState.State> {
  data class ToolEntry(
    @OptionTag
    val enabled: Boolean = false,
    @OptionTag
    val discoveryMode: ExecutableDiscoveryMode = ExecutableDiscoveryMode.INTERPRETER,
    @OptionTag(value = "customPathToExecutable", converter = PathConverter::class)
    val customToolBinaryPath: Path? = null,
  )

  internal class PathConverter : Converter<Path>() {
    override fun fromString(value: String): Path? = value.takeIf { it.isNotBlank() }?.let { Path(it) }
    override fun toString(value: Path): String = value.toString()
  }

  data class State(
    @OptionTag
    val tools: MutableMap<String, ToolEntry> = ConcurrentHashMap(),
  ) {
    internal fun persist(tool: PyTool, entry: ToolEntry) {
      if (entry == DEFAULT_TOOL_ENTRY) {
        tools.remove(tool.fusId)
      }
      else {
        tools[tool.fusId] = entry
      }
    }
  }

  private var state = State()

  override fun getState(): State = state
  override fun loadState(state: State) {
    this.state = state
  }

  /**
   * Runs once per project, the first time there is no stored [PyToolsState] yet: imports each tool's pre-existing
   * configuration via [PyTool.migrateLegacyState] (which also clears the old settings). Because the old settings are cleared,
   * re-running this after a reset to defaults imports nothing and cannot resurrect stale values.
   */
  override fun noStateLoaded() {
    for (tool in PyTool.EP_NAME.extensionList) {
      val entry = tool.migrateLegacyState(project) ?: continue
      state.persist(tool, entry)
    }
  }

  fun getEntry(tool: PyTool): ToolEntry = state.tools[tool.fusId] ?: DEFAULT_TOOL_ENTRY

  fun isEnabled(tool: PyTool): Boolean = getEntry(tool).enabled
  fun setEnabled(tool: PyTool, value: Boolean) {
    state.persist(tool, getEntry(tool).copy(enabled = value))
  }

  fun getMode(tool: PyTool): ExecutableDiscoveryMode = getEntry(tool).discoveryMode
  fun setMode(tool: PyTool, value: ExecutableDiscoveryMode) {
    state.persist(tool, getEntry(tool).copy(discoveryMode = value))
  }

  fun getCustomPath(tool: PyTool): Path? = getEntry(tool).customToolBinaryPath

  fun setCustomPath(tool: PyTool, value: Path?) {
    state.persist(tool, getEntry(tool).copy(customToolBinaryPath = value))
  }

  companion object {
    fun getInstance(project: Project): PyToolsState = project.service()
    private val DEFAULT_TOOL_ENTRY = ToolEntry()
  }
}
