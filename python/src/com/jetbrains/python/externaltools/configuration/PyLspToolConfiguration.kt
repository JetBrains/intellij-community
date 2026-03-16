// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.externaltools.configuration

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.xmlb.XmlSerializerUtil
import java.nio.file.Path
import kotlin.io.path.Path

interface PyLspExecutionConfiguration {
  var executableDiscoveryMode: ExecutableDiscoveryMode
  var pathToExecutable: String
  val executablePath: Path? get() = pathToExecutable.ifEmpty { null }?.let { Path(it) }
  var sdkName: String
}

abstract class PyLspToolConfiguration<State : PyLspToolConfiguration<State>> : PersistentStateComponent<State>,
                                                                               PyLspExecutionConfiguration {
  open var enabled: Boolean = false
  var inspections: Boolean = true
  open var completions: Boolean? = null

  /**
   * `null` means: not supported
   */
  open var inlayHints: Boolean? = null

  /**
   * `null` means: not supported
   */
  open var documentation: Boolean? = null
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
