// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.testFramework.completion.impl

import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellCommandResult
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DummyShellCommandExecutor : ShellCommandExecutor {
  override suspend fun runShellCommand(directory: String, command: String): ShellCommandResult {
    return ShellCommandResult.create("", 0)
  }
}
