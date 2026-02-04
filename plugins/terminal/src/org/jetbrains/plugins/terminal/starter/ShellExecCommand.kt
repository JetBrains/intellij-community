// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.starter

import com.intellij.util.execution.ParametersListUtil
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a shell command with its arguments.
 */
@ApiStatus.Experimental
class ShellExecCommand(command: List<String>) {
  /**
   * The immutable command. The first element is the executable, followed by its arguments, e.g., `["/bin/zsh", "-l", "-i"]`.
   * All elements are in format understood by the remote environment.
   */
  val command: List<String> = command.toImmutableList()

  init {
    require(this.command.isNotEmpty()) { "Shell command cannot be empty" }
  }

  override fun toString(): String = ParametersListUtil.join(command)

  override fun hashCode(): Int = command.hashCode()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as ShellExecCommand
    return command == other.command
  }
}
