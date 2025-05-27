// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellName
import com.intellij.terminal.completion.spec.ShellRuntimeContext

class ShellRuntimeContextReworkedImpl(
  override val currentDirectory: String,
  override val typedPrefix: String,
  override val shellName: ShellName,
) : ShellRuntimeContext, UserDataHolderBase() {
  override suspend fun runShellCommand(command: String): ShellCommandResult {
    return ShellCommandResult.create("{}", 0)
  }
}