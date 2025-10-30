// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session

import org.jetbrains.annotations.ApiStatus

/**
 * The typesafe wrapper for the name of the shell.
 *
 * If you receive an instance of [ShellName], it can be suitable to use it in conditions
 * like `if (shellName == ShellName.ZSH)`.
 */
@ApiStatus.Experimental
sealed interface ShellName {
  /** The lowercase name of the shell */
  val value: String

  companion object {
    fun of(value: String): ShellName = ShellNameImpl(value.lowercase())

    val BASH: ShellName = of("bash")
    val ZSH: ShellName = of("zsh")
    val FISH: ShellName = of("fish")
    val POWERSHELL: ShellName = of("powershell")
    val PWSH: ShellName = of("pwsh")

    fun isPowerShell(shellName: ShellName): Boolean {
      return shellName == POWERSHELL || shellName == PWSH
    }
  }
}

private data class ShellNameImpl(override val value: String) : ShellName