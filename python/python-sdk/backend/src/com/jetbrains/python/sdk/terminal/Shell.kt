// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.terminal

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * A shell process described by its executable [path] and recognized [type].
 *
 * Use [Shell.resolve] to classify a process command line into one of [Shell.Type] (or
 * [Shell.Type.UNKNOWN] for shells we don't have integration for).
 */
@ApiStatus.Internal
data class Shell(val path: Path, val type: Type) {
  enum class Type(vararg val aliases: String) {
    BASH("bash"),
    SH("sh"),
    ZSH("zsh"),
    POWERSHELL("powershell.exe", "pwsh.exe"),
    FISH("fish"),
    CSH("csh"),
    UNKNOWN;

    companion object {
      fun resolve(shellPath: Path): Type {
        val fileName = shellPath.name
        return entries.find { it.aliases.contains(fileName) } ?: UNKNOWN
      }
    }
  }

  companion object {
    fun resolve(path: Path): Shell = Shell(path, Type.resolve(path))

    fun resolve(command: Array<out String>): Shell? = command.firstOrNull()?.let { resolve(Path(it)) }

    /**
     * The system default Unix shell, used as a fallback for activate-script env reading.
     * Picks `/bin/bash`, then `/bin/sh`, then `$SHELL`. Returns `null` if none is available
     * (e.g. on Windows, where Posix shells aren't expected at fixed paths).
     */
    val systemDefaultShell: Shell? = run {
      val shellPath = when {
        Path.of("/bin/bash").exists() -> Path.of("/bin/bash")
        Path.of("/bin/sh").exists() -> Path.of("/bin/sh")
        else -> System.getenv("SHELL")?.let { Path.of(it) }
      }
      shellPath?.let { resolve(it) }
    }
  }
}
