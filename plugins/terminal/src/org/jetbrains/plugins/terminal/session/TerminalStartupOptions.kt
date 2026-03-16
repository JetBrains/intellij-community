// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session

import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Options used to start the shell process.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalStartupOptions {
  /**
   * The command used to start the shell process.
   * For example [/bin/zsh, -i, --login].
   * The list should not be empty.
   */
  val shellCommand: List<String>

  /**
   * The absolute OS-dependent path where the shell process was started.
   */
  val workingDirectory: String

  /**
   * Map of initial environment variables used to start the shell process.
   */
  val envVariables: Map<String, String>

  /**
   * ID of the started process.
   *
   * Can be null if the terminal process is started remotely,
   * for example, inside WSL or Dev Container.
   */
  val pid: Long?
}

/**
 * Tries to guess the name of the shell based on the executable path used to start the process.
 */
@ApiStatus.Experimental
fun TerminalStartupOptions.guessShellName(): ShellName {
  val executablePath = shellCommand.first() // it should be guaranteed that it is not empty
  val executableName = PathUtil.getFileName(executablePath)
  val extension = PathUtil.getFileExtension(executableName)
  val shellName = if (extension != null) executableName.removeSuffix(".$extension") else executableName
  return ShellName.of(shellName)
}