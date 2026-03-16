// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Used to define what kind of process is running in the terminal.
 *
 * For example, it affects what base set of environment variables is used to start the process.
 */
@ApiStatus.Experimental
@Serializable
enum class TerminalProcessType {
  /**
   * The process is a shell, like Bash, Zsh, PowerShell, etc.
   *
   * A minimal set of environment variables will be used to start the process ([System.getenv]).
   * Because when an interactive shell is started, it sources config files and populates the environment on its own.
   */
  SHELL,

  /**
   * Some arbitrary PTY process.
   *
   * Usually such processes require a pre-built set of environment variables, so [com.intellij.util.EnvironmentUtil.getEnvironmentMap]
   * will be used to start the process.
   */
  NON_SHELL,
}