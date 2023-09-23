// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.completion.ShellEnvironment
import com.intellij.terminal.completion.ShellRuntimeDataProvider
import com.intellij.util.io.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.terminal.exp.ShellCommandListener
import org.jetbrains.plugins.terminal.exp.TerminalSession
import org.jetbrains.plugins.terminal.util.ShellType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class IJShellRuntimeDataProvider(private val session: TerminalSession) : ShellRuntimeDataProvider {
  override suspend fun getFilesFromDirectory(path: String): List<String> {
    val command = GetFilesCommand(path)
    return executeCommand(command)
  }

  override suspend fun getShellEnvironment(): ShellEnvironment? {
    return executeCommand(GetEnvironmentCommand())
  }

  private suspend fun <T> executeCommand(command: DataProviderCommand<T>): T {
    return if (command.isAvailable(session)) {
      val requestId = CUR_ID.getAndIncrement()
      val commandText = "${command.functionName} $requestId ${command.parameters.joinToString(" ")}"
      val rawResult: String = executeCommandBlocking(requestId, commandText)
      command.parseResult(rawResult)
    }
    else command.defaultResult
  }

  private suspend fun executeCommandBlocking(reqId: Int, command: String): String {
    val resultFuture = CompletableFuture<String>()
    val disposable = Disposer.newDisposable()
    try {
      session.addCommandListener(object : ShellCommandListener {
        override fun generatorFinished(requestId: Int, result: String) {
          if (requestId == reqId) {
            resultFuture.complete(result)
          }
        }
      }, disposable)

      session.executeCommand(command)
      return resultFuture.await()
    }
    finally {
      Disposer.dispose(disposable)
      val model = session.model
      model.withContentLock {
        model.clearAll()
        model.setCursor(0, 1)
      }
    }
  }

  private interface DataProviderCommand<T> {
    val functionName: String
    val parameters: List<String>
    val defaultResult: T

    fun isAvailable(session: TerminalSession): Boolean
    fun parseResult(result: String): T
  }

  private class GetFilesCommand(path: String) : DataProviderCommand<List<String>> {
    override val functionName: String = "__jetbrains_intellij_get_directory_files"
    override val parameters: List<String> = listOf(path)
    override val defaultResult: List<String> = emptyList()

    override fun isAvailable(session: TerminalSession): Boolean {
      return session.isBashOrZsh()
    }

    override fun parseResult(result: String): List<String> {
      return result.split("\n")
    }
  }

  private class GetEnvironmentCommand : DataProviderCommand<ShellEnvironment?> {
    override val functionName: String = "__jetbrains_intellij_get_environment"
    override val parameters: List<String> = emptyList()
    override val defaultResult: ShellEnvironment? = null

    override fun isAvailable(session: TerminalSession): Boolean {
      return session.isBashOrZsh()
    }

    override fun parseResult(result: String): ShellEnvironment? {
      val rawEnv: ShellEnvCommandResult = try {
        Json.decodeFromString(result)
      }
      catch (t: Throwable) {
        LOG.error("Failed to parse shell env:\n$result", t)
        return null
      }
      return ShellEnvironment(
        envs = rawEnv.envs.split("\n"),
        keywords = rawEnv.keywords.split("\n"),
        builtins = rawEnv.builtins.split("\n"),
        functions = rawEnv.functions.split("\n"),
        commands = rawEnv.commands.split("\n"),
      )
    }

    @Serializable
    private data class ShellEnvCommandResult(
      val envs: String,
      val keywords: String,
      val builtins: String,
      val functions: String,
      val commands: String
    )
  }

  companion object {
    private val CUR_ID = AtomicInteger(0)
    private val LOG: Logger = logger<IJShellRuntimeDataProvider>()

    private fun TerminalSession.isBashOrZsh(): Boolean {
      val shellType = shellIntegration?.shellType
      return shellType == ShellType.ZSH || shellType == ShellType.BASH
    }
  }
}