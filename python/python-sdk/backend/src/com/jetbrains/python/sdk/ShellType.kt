// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import org.jetbrains.annotations.ApiStatus

/**
 * A shell that the IDE can activate a Python environment in.
 *
 * Use `null` for a shell that is not in this list.
 */
@ApiStatus.Internal
enum class ShellType(vararg val aliases: String) {
  BASH("bash"),
  SH("sh"),
  ZSH("zsh"),
  POWERSHELL("powershell", "pwsh"),
  FISH("fish"),
  CSH("csh"),
  CMD("cmd");

  companion object {
    /**
     * The shell type of the executable [fileName], for example `bash`, `bash.exe` or `pwsh.exe`,
     * or null when the shell is not known.
     */
    fun resolve(fileName: String): ShellType? = entries.firstOrNull { shellType ->
      shellType.aliases.any { fileName.startsWith(it) }
    }
  }
}
