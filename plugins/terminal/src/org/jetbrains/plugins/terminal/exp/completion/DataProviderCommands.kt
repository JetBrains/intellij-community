// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.completion.ShellEnvironment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.terminal.exp.TerminalSession
import org.jetbrains.plugins.terminal.util.ShellType

internal interface DataProviderCommand<T> {
  val functionName: String
  val parameters: List<String>
  val defaultResult: T

  fun isAvailable(session: TerminalSession): Boolean
  fun parseResult(result: String): T
}

internal class GetFilesCommand(path: String) : DataProviderCommand<List<String>> {
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

internal class GetEnvironmentCommand(private val session: TerminalSession) : DataProviderCommand<ShellEnvironment?> {
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
      aliases = parseAliases(rawEnv.aliases)
    )
  }

  private fun parseAliases(text: String): Map<String, String> {
    val shellSupport = TerminalShellSupport.findByShellType(session.shellIntegration.shellType)
                       ?: return emptyMap()
    return try {
      shellSupport.parseAliases(text)
    }
    catch (t: Throwable) {
      LOG.error("Failed to parse aliases: $text")
      emptyMap()
    }
  }

  @Serializable
  private data class ShellEnvCommandResult(
    val envs: String,
    val keywords: String,
    val builtins: String,
    val functions: String,
    val commands: String,
    val aliases: String
  )

  companion object {
    private val LOG: Logger = logger<GetEnvironmentCommand>()
  }
}

private fun TerminalSession.isBashOrZsh(): Boolean {
  return shellIntegration.shellType == ShellType.ZSH
         || shellIntegration.shellType == ShellType.BASH
}