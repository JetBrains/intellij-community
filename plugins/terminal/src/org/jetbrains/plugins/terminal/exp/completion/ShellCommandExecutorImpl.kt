// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import org.jetbrains.plugins.terminal.exp.BlockTerminalSession

class ShellCommandExecutorImpl(private val session: BlockTerminalSession) : ShellCommandExecutor {
  override suspend fun <T> executeCommand(command: DataProviderCommand<T>): T {
    return if (command.isAvailable(session)) {
      val rawResult: String = executeCommandBlocking(command)
      command.parseResult(rawResult)
    }
    else command.defaultResult
  }

  private suspend fun executeCommandBlocking(command: DataProviderCommand<*>): String {
    return session.commandManager.runGeneratorAsync(command.functionName, command.parameters).await()
  }
}