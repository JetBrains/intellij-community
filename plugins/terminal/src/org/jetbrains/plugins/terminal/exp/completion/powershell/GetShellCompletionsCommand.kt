// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion.powershell

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.terminal.exp.BlockTerminalSession
import org.jetbrains.plugins.terminal.exp.completion.DataProviderCommand
import org.jetbrains.plugins.terminal.util.ShellType

internal class GetShellCompletionsCommand(command: String, caretPosition: Int) : DataProviderCommand<CompletionResult?> {
  override val functionName: String = "__JetBrainsIntellijGetCompletions"
  override val parameters: List<String> = listOf(escapePowerShellCommand(command), caretPosition.toString())
  override val defaultResult: CompletionResult? = null

  override fun isAvailable(session: BlockTerminalSession): Boolean {
    return session.shellIntegration.shellType == ShellType.POWERSHELL
  }

  override fun parseResult(result: String): CompletionResult? {
    if (result.isEmpty()) {
      return null
    }
    val json = Json { ignoreUnknownKeys = true }
    val completionResult = try {
      json.decodeFromString<CompletionResult>(result)
    }
    catch (t: Throwable) {
      LOG.error("Failed to parse completions: $result", t)
      return null
    }
    return completionResult
  }

  companion object {
    private val LOG: Logger = logger<GetShellCompletionsCommand>()

    private val charsToEscape: Map<Char, String> = mapOf(
      '`' to "``",
      '\"' to "`\"",
      '\u0000' to "`0",
      '\u0007' to "`a",
      '\u0008' to "`b",
      '\u000c' to "`f",
      '\n' to "`n",
      '\r' to "`r",
      '\t' to "`t",
      '\u000B' to "'v",
      '$' to "`$"
    )

    private fun escapePowerShellCommand(command: String): String {
      return buildString(command.length) {
        for (ch in command) {
          append(charsToEscape[ch] ?: ch)
        }
      }
    }
  }
}

// https://learn.microsoft.com/en-us/dotnet/api/system.management.automation.commandcompletion
@Serializable
internal data class CompletionResult(
  @SerialName("ReplacementIndex") val replacementIndex: Int,
  @SerialName("ReplacementLength") val replacementLength: Int,
  @SerialName("CompletionMatches") val matches: List<CompletionItem>
)

// https://learn.microsoft.com/en-us/dotnet/api/system.management.automation.completionresult
@Serializable
internal data class CompletionItem(
  @SerialName("CompletionText") val value: String,
  @SerialName("ListItemText") val presentableText: String? = null,
  @SerialName("ToolTip") val description: String? = null,
  @SerialName("ResultType") val type: CompletionResultType
)

// https://learn.microsoft.com/en-us/dotnet/api/system.management.automation.completionresulttype
@Serializable(with = CompletionResultTypeSerializer::class)
internal enum class CompletionResultType(val value: Int) {
  TEXT(0),
  HISTORY(1),
  COMMAND(2),
  PROVIDER_ITEM(3),
  PROVIDER_CONTAINER(4),
  PROPERTY(5),
  METHOD(6),
  PARAMETER_NAME(7),
  PARAMETER_VALUE(8),
  VARIABLE(9),
  NAMESPACE(10),
  TYPE(11),
  KEYWORD(12),
  DYNAMIC_KEYWORD(13)
}

/**
 * Encodes and decodes the enum using [CompletionResultType.value] field
 */
private class CompletionResultTypeSerializer : KSerializer<CompletionResultType> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(CompletionResultType::class.java.name, PrimitiveKind.INT)

  override fun serialize(encoder: Encoder, value: CompletionResultType) {
    encoder.encodeInt(value.value)
  }

  override fun deserialize(decoder: Decoder): CompletionResultType {
    val value: Int = decoder.decodeInt()
    return CompletionResultType.entries.find { it.value == value } ?: CompletionResultType.TEXT
  }
}
