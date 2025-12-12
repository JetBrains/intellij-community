// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.terminal.completion.spec.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil.throwUnsupportedInExpTerminalException

@ApiStatus.Internal
class ShellRuntimeContextImpl(
  override val currentDirectory: String,
  override val envVariables: Map<String, String>,
  override val commandTokens: List<String>,
  override val shellName: ShellName,
  private val generatorCommandsRunner: ShellCommandExecutor,
  private val generatorProcessExecutor: ShellDataGeneratorProcessExecutor? = null,
  private val fileSystemSupport: ShellFileSystemSupport? = null,
) : ShellRuntimeContext, UserDataHolderBase() {
  override val typedPrefix: String = commandTokens.last()

  override suspend fun runShellCommand(command: String): ShellCommandResult {
    return generatorCommandsRunner.runShellCommand(currentDirectory, command)
  }

  override suspend fun createProcessBuilder(executable: String): ShellDataGeneratorProcessBuilder {
    return if (generatorProcessExecutor != null) {
      // Reworked Terminal case - process executor is available
      ShellDataGeneratorProcessBuilderImpl(executable, currentDirectory, generatorProcessExecutor)
    }
    else {
      throwUnsupportedInExpTerminalException()
    }
  }

  override suspend fun listDirectoryFiles(path: String): List<ShellFileInfo> {
    @Suppress("IfThenToElvis")
    return if (fileSystemSupport != null) {
      // Reworked Terminal case - file system support is available
      fileSystemSupport.listDirectoryFiles(path)
    }
    else {
      throwUnsupportedInExpTerminalException()
    }
  }

  override fun toString(): String {
    return "ShellRuntimeContextImpl(currentDirectory='$currentDirectory', typedPrefix='$typedPrefix')"
  }
}