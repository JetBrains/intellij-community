// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.util

import com.intellij.terminal.completion.spec.ShellCommandResult
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellGeneratorCommandsRunner

internal class TestGeneratorCommandsRunner(
  private val mockCommandResult: suspend (command: String) -> ShellCommandResult
) : ShellGeneratorCommandsRunner {
  override suspend fun runGeneratorCommand(command: String): ShellCommandResult {
    return mockCommandResult(command)
  }

  companion object {
    val DUMMY: ShellGeneratorCommandsRunner = TestGeneratorCommandsRunner {
      ShellCommandResult.create("", 0)
    }
  }
}
