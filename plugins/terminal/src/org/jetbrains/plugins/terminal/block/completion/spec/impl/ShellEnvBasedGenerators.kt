// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import com.intellij.terminal.block.completion.spec.ShellRuntimeDataGenerator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport

internal object ShellEnvBasedGenerators {
  private val LOG: Logger = logger<ShellEnvBasedGenerators>()

  fun aliasesGenerator(): ShellRuntimeDataGenerator<Map<String, String>> = ShellRuntimeDataGenerator { context ->
    val shellEnv = getShellEnv(context) ?: return@ShellRuntimeDataGenerator emptyMap()
    shellEnv.aliases
  }

  suspend fun getShellEnv(context: ShellRuntimeContext): ShellEnvironment? {
    val result = context.runShellCommand("__jetbrains_intellij_get_environment")
    if (result.exitCode != 0) {
      LOG.error("Get shell environment command failed with exit code ${result.exitCode}, output: ${result.output}")
      return null
    }

    val rawEnv: ShellEnvCommandResult = try {
      Json.decodeFromString(result.output)
    }
    catch (t: Throwable) {
      LOG.error("Failed to parse shell env:\n$result", t)
      return null
    }
    return ShellEnvironment(
      keywords = rawEnv.keywords.splitIfNotEmpty("\n"),
      builtins = rawEnv.builtins.splitIfNotEmpty("\n"),
      functions = rawEnv.functions.splitIfNotEmpty("\n"),
      commands = rawEnv.commands.splitIfNotEmpty("\n"),
      aliases = parseAliases(rawEnv.aliases, context.shellName.name)
    )
  }

  private fun parseAliases(text: String, shellName: String): Map<String, String> {
    val shellSupport = TerminalShellSupport.findByShellName(shellName)
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
}