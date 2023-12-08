// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.terminal.completion.ShellEnvironment
import com.intellij.terminal.completion.ShellRuntimeDataProvider
import org.jetbrains.plugins.terminal.exp.TerminalSession

class IJShellRuntimeDataProvider(private val session: TerminalSession) : ShellRuntimeDataProvider {
  override suspend fun getFilesFromDirectory(path: String): List<String> {
    return executeCommand(GetFilesCommand(path))
  }

  override suspend fun getShellEnvironment(): ShellEnvironment? {
    return executeCommand(GetEnvironmentCommand(session))
  }

  private suspend fun <T> executeCommand(command: DataProviderCommand<T>): T {
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