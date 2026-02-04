// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.starter

import org.jetbrains.annotations.ApiStatus

/**
 * Represents an immutable shell command.
 */
@ApiStatus.Experimental
interface ShellExecCommand {
  /**
   * The immutable list representing a shell command.
   * The first element is the executable, followed by its arguments, e.g., `["/bin/zsh", "-l", "-i"]`.
   * All elements are in format understood by the remote environment.
   */
  val command: List<String>
}
