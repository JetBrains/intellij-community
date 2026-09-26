// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class TerminalPromptState(
  val currentDirectory: String,
  val userName: String? = null,
  val userHome: String? = null,
  val gitBranch: String? = null,
  /** Absolute path to the virtual env */
  val virtualEnv: String? = null,
  val condaEnv: String? = null,
  val originalPrompt: String? = null,
  val originalRightPrompt: String? = null,
  val shellName: String? = null
) {
  companion object {
    val EMPTY: TerminalPromptState = TerminalPromptState("")
  }
}
