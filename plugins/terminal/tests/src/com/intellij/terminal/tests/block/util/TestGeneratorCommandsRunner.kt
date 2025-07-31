// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.util

import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellCommandResult

internal class TestGeneratorCommandsRunner {
  companion object {
    val DUMMY: ShellCommandExecutor = ShellCommandExecutor {
      ShellCommandResult.create("", 0)
    }
  }
}
