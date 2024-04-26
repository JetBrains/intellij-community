// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.block.completion.spec.ShellCommandResult
import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import org.jetbrains.plugins.terminal.exp.BlockTerminalSession

internal class IJShellRuntimeContext(
  override val currentDirectory: String,
  override val commandText: String,
  override val typedPrefix: String,
  private val session: BlockTerminalSession
) : ShellRuntimeContext {
  override suspend fun runShellCommand(command: String): ShellCommandResult {
    return session.commandManager.runGeneratorAsync(command).await()
  }

  override fun toString(): String {
    return "IJShellRuntimeContext(currentDirectory='$currentDirectory', commandText='$commandText', typedPrefix='$typedPrefix')"
  }
}