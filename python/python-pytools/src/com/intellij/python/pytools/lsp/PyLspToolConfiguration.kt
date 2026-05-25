// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.lsp

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.util.xmlb.XmlSerializerUtil
import java.nio.file.Path
import kotlin.io.path.Path

/** Default SDK marker for tools that previously stored a per-tool SDK name. */
const val DEFAULT_ENVIRONMENT: String = "Project Default"

/**
 * Storage file for LSP tool settings. This file is saved in .idea/ and can be committed to VCS.
 *
 * Holds project-wide [com.intellij.python.pytools.PyToolsState] (the source of truth for enabled/mode/customPath)
 * plus each tool's remaining feature toggles (inspections, completions, inlay hints, documentation, ...).
 */
const val LSP_TOOLS_STORAGE_FILE: String = "pyLspTools.xml"

/**
 * Per-tool feature settings persisted alongside [com.intellij.python.pytools.PyToolsState].
 *
 * The discovery mode / custom path live centrally in `PyToolsState`. The legacy fields here are kept
 * as a write-through mirror: each tool's `onEnabledChanged` / `onModeChanged` / `onCustomPathChanged`
 * callback updates them so existing code paths (LSP server start, FUS reporting, ...) keep working
 * without needing to know about the central state.
 */
interface PyLspToolSettings {
  @Deprecated("replaced with PyToolState", ReplaceWith("PyTool.isEnabledOn(project)"))
  var enabled: Boolean
  var inspections: Boolean
  var completions: Boolean?
  var inlayHints: Boolean?
  var documentation: Boolean?
  @Deprecated("replaced with PyToolState", ReplaceWith("PyTool.executeOn()"))
  var executableDiscoveryMode: ExecutableDiscoveryMode
  @Deprecated("replaced with PyToolState", ReplaceWith("PyTool.executeOn()"))
  var pathToExecutable: String
  @Deprecated("replaced with PyToolState", ReplaceWith("PyTool.executeOn()"))
  var sdkName: String
}

@Deprecated("replaced with PyToolState", ReplaceWith("PyTool.executeOn()"))
val PyLspToolSettings.executablePath: Path? get() = pathToExecutable.ifEmpty { null }?.let { Path(it) }

abstract class PyLspToolConfiguration<State : PyLspToolConfiguration<State>> : PersistentStateComponent<State>,
                                                                               PyLspToolSettings {
  @Deprecated("replaced with PyToolState")
  override var enabled: Boolean = false
  override var inspections: Boolean = true
  override var completions: Boolean? = null

  /** `null` means: not supported */
  override var inlayHints: Boolean? = null

  /** `null` means: not supported */
  override var documentation: Boolean? = null
  @Deprecated("replaced with PyToolState")
  override var executableDiscoveryMode: ExecutableDiscoveryMode = ExecutableDiscoveryMode.INTERPRETER
  @Deprecated("replaced with PyToolState")
  override var pathToExecutable: String = ""
  @Deprecated("replaced with PyToolState")
  override var sdkName: String = DEFAULT_ENVIRONMENT

  fun isAnyFeatureEnabled(): Boolean = inspections || completions == true || inlayHints == true || documentation == true

  @Suppress("UNCHECKED_CAST")
  final override fun getState(): State = this as State

  @Suppress("UNCHECKED_CAST")
  override fun loadState(state: State): Unit = XmlSerializerUtil.copyBean(state, this as State)
}
