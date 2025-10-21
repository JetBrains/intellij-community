// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.externaltools.configuration

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.xmlb.XmlSerializerUtil
import java.nio.file.Path
import kotlin.io.path.Path

abstract class PyLspToolConfiguration<State : PyLspToolConfiguration<State>> : PersistentStateComponent<State> {
  var enabled: Boolean = false
  var inspections: Boolean = true
  open var completions: Boolean? = null

  /**
   * `null` means: not supported
   */
  open var inlayHints: Boolean? = null
  var executableDiscoveryMode: ExecutableDiscoveryMode = ExecutableDiscoveryMode.INTERPRETER
  var pathToExecutable: String? = null
  val executablePath: Path? = pathToExecutable?.let { Path(it) }
  var sdkName: String = DEFAULT_ENVIRONMENT

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
