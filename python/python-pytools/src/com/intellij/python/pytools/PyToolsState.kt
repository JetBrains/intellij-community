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
 * One [ToolEntry] per tool, keyed by [PyTool.fusId]. First-time reads fall back to each tool's
 * legacy `enabled` / mode / path values (see [PyTool.legacyEnabled], [PyTool.legacyDiscoveryMode], [PyTool.legacyCustomPath])
 * so users upgrading from the per-tool configuration model do not lose their settings.
 */
@Service(Service.Level.PROJECT)
@State(name = "PyToolsState", storages = [Storage(LSP_TOOLS_STORAGE_FILE)])
class PyToolsState(private val project: Project) : PersistentStateComponent<PyToolsState.State> {
  data class ToolEntry(
    var enabled: Boolean = false,
    var discoveryMode: ExecutableDiscoveryMode = ExecutableDiscoveryMode.INTERPRETER,
    @OptionTag(value = "customPathToExecutable", converter = PathConverter::class)
    var customToolBinaryPath: Path? = null,
  )

  internal class PathConverter : Converter<Path>() {
    override fun fromString(value: String): Path? = value.takeIf { it.isNotBlank() }?.let { Path(it) }
    override fun toString(value: Path): String = value.toString()
  }

  data class State(var tools: MutableMap<String, ToolEntry> = ConcurrentHashMap())

  private var state = State()

  override fun getState(): State = state
  override fun loadState(state: State) {
    this.state = state
  }

  fun getEntry(tool: PyTool): ToolEntry {
    val key = tool.fusId
    state.tools[key]?.let { return it }
    val migrated = ToolEntry(
      enabled = tool.legacyEnabled(project),
      discoveryMode = tool.legacyDiscoveryMode(project),
      customToolBinaryPath = tool.legacyCustomPath(project),
    )
    return state.tools.putIfAbsent(key, migrated) ?: migrated
  }

  fun isEnabled(tool: PyTool): Boolean = getEntry(tool).enabled
  fun setEnabled(tool: PyTool, value: Boolean) {
    getEntry(tool).enabled = value
  }

  fun getMode(tool: PyTool): ExecutableDiscoveryMode = getEntry(tool).discoveryMode
  fun setMode(tool: PyTool, value: ExecutableDiscoveryMode) {
    getEntry(tool).discoveryMode = value
  }

  fun getCustomPath(tool: PyTool): Path? = getEntry(tool).customToolBinaryPath

  fun setCustomPath(tool: PyTool, value: Path?) {
    getEntry(tool).customToolBinaryPath = value
  }

  companion object {
    fun getInstance(project: Project): PyToolsState = project.service()
  }
}
