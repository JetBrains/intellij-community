// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.powershell

import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import com.intellij.terminal.completion.spec.isPowerShell
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.session.ShellCommandExecutionManagerImpl
import org.jetbrains.plugins.terminal.block.session.ShellIntegrationFunctions.GET_COMPLETIONS

internal fun powerShellCompletionGenerator(command: String, caretOffset: Int): ShellRuntimeDataGenerator<CompletionResult> {
  return ShellRuntimeDataGenerator(debugName = "powershell completion") { context ->
    assert(context.shellName.isPowerShell())

    val escapedCommand = ShellCommandExecutionManagerImpl.escapePowerShellParameter(command)
    val commandResult = context.runShellCommand("""${GET_COMPLETIONS.functionName} "$escapedCommand" $caretOffset""")
    if (commandResult.exitCode != 0) {
      logger<PowerShellCompletionContributor>().warn("PowerShell completion generator for command '$command' at offset $caretOffset failed with exit code ${commandResult.exitCode}, output: ${commandResult.output}")
      return@ShellRuntimeDataGenerator emptyCompletionResult()
    }
    val json = Json { ignoreUnknownKeys = true }
    try {
      json.decodeFromString<CompletionResult>(commandResult.output)
    }
    catch (t: Throwable) {
      logger<PowerShellCompletionContributor>().error("Failed to parse completions for command '$command' at offset $caretOffset: $commandResult", t)
      emptyCompletionResult()
    }
  }
}

private fun emptyCompletionResult(): CompletionResult = CompletionResult(0, 0, emptyList())

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
