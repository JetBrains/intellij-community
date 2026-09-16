// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.xmlb.XmlSerializerUtil

/** Default SDK marker for tools that previously stored a per-tool SDK name. */
const val DEFAULT_ENVIRONMENT: String = "Project Default"

/**
 * Storage file for LSP tool settings. This file is saved in .idea/ and can be committed to VCS.
 *
 * Holds each tool's remaining frontend feature toggles (inspections, completions, inlay hints, documentation, ...).
 * Tool enablement is backend-owned and mirrored separately.
 */
const val LSP_TOOLS_STORAGE_FILE: String = "pyLspTools.xml"

/**
 * Per-tool frontend feature settings.
 *
 * The legacy fields remain only for one-way migration. Backend services own enablement, executable
 * discovery, and custom paths.
 */
interface PyLspToolSettings {
  @Deprecated("replaced with PyToolState", ReplaceWith("PyTool.isEnabledOn(project)"))
  var enabled: Boolean
  var inspections: Boolean
  var completions: Boolean?
  var inlayHints: Boolean?
  var documentation: Boolean?
  @Deprecated("replaced with PyToolState", ReplaceWith("PyTool.executeOn()"))
  var pathToExecutable: String
  @Deprecated("replaced with PyToolState", ReplaceWith("PyTool.executeOn()"))
  var sdkName: String
}

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
  override var pathToExecutable: String = ""
  @Deprecated("replaced with PyToolState")
  override var sdkName: String = DEFAULT_ENVIRONMENT

  @Suppress("UNCHECKED_CAST")
  final override fun getState(): State = this as State

  @Suppress("UNCHECKED_CAST")
  override fun loadState(state: State): Unit = XmlSerializerUtil.copyBean(state, this as State)

  /**
   * Reads the legacy `enabled` flag and clears the legacy enable / path fields, so the one-time migration
   * is one-way: re-running it can never resurrect the old values. The legacy custom path is dropped, not
   * migrated — custom paths now live per Eel machine in `PyCustomExecutablePaths`, and importing a stale
   * per-project value here could clobber one the user already set. Feature flags (inspections,
   * completions, ...) are left untouched.
   */
  @Suppress("DEPRECATION")
  fun migrateToPyToolState(): Boolean {
    val migratedEnabled = enabled
    enabled = false
    pathToExecutable = ""
    return migratedEnabled
  }
}
