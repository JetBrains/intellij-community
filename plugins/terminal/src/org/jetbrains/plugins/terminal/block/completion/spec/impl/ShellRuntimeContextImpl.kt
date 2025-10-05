// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.terminal.completion.spec.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShellRuntimeContextImpl(
  override val currentDirectory: String,
  override val typedPrefix: String,
  override val shellName: ShellName,
  private val generatorCommandsRunner: ShellCommandExecutor,
  private val fileSystemSupport: ShellFileSystemSupport? = null,
) : ShellRuntimeContext, UserDataHolderBase() {

  override suspend fun runShellCommand(command: String): ShellCommandResult {
    return generatorCommandsRunner.runShellCommand(currentDirectory, command)
  }

  override suspend fun listDirectoryFiles(path: String): List<ShellFileInfo> {
    return fileSystemSupport?.listDirectoryFiles(path)
           ?: error("Supported only in Reworked Terminal")
  }

  override fun toString(): String {
    return "ShellRuntimeContextImpl(currentDirectory='$currentDirectory', typedPrefix='$typedPrefix')"
  }
}
