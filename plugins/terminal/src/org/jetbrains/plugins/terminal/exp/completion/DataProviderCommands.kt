// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.completion.ShellEnvironment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.terminal.exp.BlockTerminalSession
import org.jetbrains.plugins.terminal.util.ShellType

internal class GetFilesCommand(path: String) : DataProviderCommand<List<String>> {
  override val functionName: String = "__jetbrains_intellij_get_directory_files"
  override val parameters: List<String> = listOf(path)
  override val defaultResult: List<String> = emptyList()

  override fun isAvailable(session: BlockTerminalSession): Boolean {
    return session.isBashZshPwsh()
  }

  override fun parseResult(result: String): List<String> {
    return result.split("\n")
  }
}

internal class GetEnvironmentCommand(private val session: BlockTerminalSession) : DataProviderCommand<ShellEnvironment?> {
  override val functionName: String = "__jetbrains_intellij_get_environment"
  override val parameters: List<String> = emptyList()
  override val defaultResult: ShellEnvironment? = null

  override fun isAvailable(session: BlockTerminalSession): Boolean {
    return session.isBashZshPwsh()
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
      envs = rawEnv.envs.splitIfNotEmpty("\n"),
      keywords = rawEnv.keywords.splitIfNotEmpty("\n"),
      builtins = rawEnv.builtins.splitIfNotEmpty("\n"),
      functions = rawEnv.functions.splitIfNotEmpty("\n"),
      commands = rawEnv.commands.splitIfNotEmpty("\n"),
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

  private fun String.splitIfNotEmpty(delimiter: String): List<String> {
    return if (isEmpty()) emptyList() else split(delimiter)
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

internal fun BlockTerminalSession.isBashZshPwsh(): Boolean {
  return shellIntegration.shellType == ShellType.ZSH
         || shellIntegration.shellType == ShellType.BASH
         || shellIntegration.shellType == ShellType.POWERSHELL
}