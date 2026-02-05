// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.starter

import org.jetbrains.annotations.ApiStatus

/**
 * Represents an immutable command for starting a shell process in the remote environment.
 */
@ApiStatus.Experimental
sealed interface ShellExecCommand {
  /**
   * The command to execute, represented as an immutable list.
   * The first element is the executable path, followed by its arguments, e.g., `["/bin/zsh", "-l", "-i"]`.
   * All elements are in format understood by the remote environment.
   */
  val command: List<String>
}
