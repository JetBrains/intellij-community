// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellDataGeneratorProcessBuilder
import com.intellij.terminal.completion.spec.ShellFileInfo
import com.intellij.terminal.completion.spec.ShellName
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil.throwUnsupportedInExpTerminalException
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessExecutor
import org.jetbrains.plugins.terminal.block.completion.spec.ShellFileSystemSupport

@ApiStatus.Internal
class ShellRuntimeContextImpl(
  override val currentDirectory: String,
  override val envVariables: Map<String, String>,
  override val commandTokens: List<String>,
  private val definedShellName: ShellName?,
  private val generatorCommandsRunner: ShellCommandExecutor,
  private val generatorProcessExecutor: ShellDataGeneratorProcessExecutor? = null,
  private val fileSystemSupport: ShellFileSystemSupport? = null,
) : ShellRuntimeContext, UserDataHolderBase() {
  override val typedPrefix: String = TerminalCompletionUtil.getTypedPrefix(commandTokens)

  override val shellName: ShellName
    get() = definedShellName ?: throw UnsupportedOperationException("Not supported in Reworked Terminal")

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