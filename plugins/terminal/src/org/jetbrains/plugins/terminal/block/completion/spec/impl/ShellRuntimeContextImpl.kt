// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.terminal.completion.spec.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShellRuntimeContextImpl(
  override val currentDirectory: String,
  override val envVariables: Map<String, String>,
  override val typedPrefix: String,
  override val shellName: ShellName,
  private val generatorCommandsRunner: ShellCommandExecutor,
  private val generatorProcessExecutor: ShellDataGeneratorProcessExecutor? = null,
  private val fileSystemSupport: ShellFileSystemSupport? = null,
) : ShellRuntimeContext, UserDataHolderBase() {

  override suspend fun runShellCommand(command: String): ShellCommandResult {
    return generatorCommandsRunner.runShellCommand(currentDirectory, command)
  }

  override suspend fun createProcessBuilder(executable: String): ShellDataGeneratorProcessBuilder {
    return if (generatorProcessExecutor != null) {
      // Reworked Terminal case - process executor is available
      ShellDataGeneratorProcessBuilderImpl(executable, currentDirectory, generatorProcessExecutor)
    }
    else {
      // Experimental 2024 Terminal case - this API is not supported there.
      NoOpProcessBuilder()
    }
  }

  override suspend fun listDirectoryFiles(path: String): List<ShellFileInfo> {
    return fileSystemSupport?.listDirectoryFiles(path)
           ?: error("Supported only in Reworked Terminal")
  }

  override fun toString(): String {
    return "ShellRuntimeContextImpl(currentDirectory='$currentDirectory', typedPrefix='$typedPrefix')"
  }
}

/**
 * Doesn't run the process and always return [ShellCommandResult] with empty output and exit code 1.
 */
private class NoOpProcessBuilder : ShellDataGeneratorProcessBuilder {
  override fun args(args: List<String>): ShellDataGeneratorProcessBuilder {
    return this
  }

  override fun workingDirectory(workingDirectory: String): ShellDataGeneratorProcessBuilder {
    return this
  }

  override fun env(env: Map<String, String>): ShellDataGeneratorProcessBuilder {
    return this
  }

  override suspend fun execute(): ShellCommandResult {
    return ShellCommandResult.create("", 1)
  }
}