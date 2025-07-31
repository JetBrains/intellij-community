// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellName
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShellRuntimeContextImpl(
  override val currentDirectory: String,
  override val typedPrefix: String,
  override val shellName: ShellName,
  private val generatorCommandsRunner: ShellCommandExecutor,
) : ShellRuntimeContext, UserDataHolderBase() {

  override suspend fun runShellCommand(command: String): ShellCommandResult {
    return generatorCommandsRunner.runShellCommand(command)
  }

  override fun toString(): String {
    return "IJShellRuntimeContext(currentDirectory='$currentDirectory', typedPrefix='$typedPrefix')"
  }
}
