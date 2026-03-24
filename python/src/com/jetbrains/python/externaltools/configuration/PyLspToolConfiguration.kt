// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.externaltools.configuration

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.xmlb.XmlSerializerUtil
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Storage file for LSP tool settings. This file is saved in .idea/ and can be committed to VCS.
 */
const val LSP_TOOLS_STORAGE_FILE: String = "pyLspTools.xml"

interface PyLspExecutionConfiguration {
  var executableDiscoveryMode: ExecutableDiscoveryMode
  var pathToExecutable: String
  val executablePath: Path? get() = pathToExecutable.ifEmpty { null }?.let { Path(it) }
  var sdkName: String
}

/**
 * Common interface for LSP tool settings that can be used for both project-level
 * and module-level settings. This allows UI bindings to work with either type.
 */
interface PyLspToolSettings : PyLspExecutionConfiguration {
  var enabled: Boolean
  var inspections: Boolean
  var completions: Boolean?
  var inlayHints: Boolean?
  var documentation: Boolean?
}

abstract class PyLspToolConfiguration<State : PyLspToolConfiguration<State>> : PersistentStateComponent<State>,
                                                                               PyLspToolSettings {
  override var enabled: Boolean = false
  override var inspections: Boolean = true
  override var completions: Boolean? = null

  /**
   * `null` means: not supported
   */
  override var inlayHints: Boolean? = null

  /**
   * `null` means: not supported
   */
  override var documentation: Boolean? = null
  override var executableDiscoveryMode: ExecutableDiscoveryMode = ExecutableDiscoveryMode.INTERPRETER
  override var pathToExecutable: String = ""
  override var sdkName: String = DEFAULT_ENVIRONMENT

  final override fun getState(): State = this as State

  override fun loadState(state: State): Unit = XmlSerializerUtil.copyBean(state, this as State)
}

/**
 * Executable discovery mode for LSP tools.
 */
enum class ExecutableDiscoveryMode {
  INTERPRETER,
  PATH,
}
