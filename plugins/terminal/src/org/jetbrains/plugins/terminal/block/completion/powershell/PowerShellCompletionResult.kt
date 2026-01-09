// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.powershell

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.annotations.ApiStatus

// https://learn.microsoft.com/en-us/dotnet/api/system.management.automation.commandcompletion
@ApiStatus.Internal
@Serializable
data class PowerShellCompletionResult(
  @SerialName("ReplacementIndex") val replacementIndex: Int,
  @SerialName("ReplacementLength") val replacementLength: Int,
  @SerialName("CompletionMatches") val matches: List<PowerShellCompletionItem>,
)

// https://learn.microsoft.com/en-us/dotnet/api/system.management.automation.completionresult
@ApiStatus.Internal
@Serializable
data class PowerShellCompletionItem(
  @SerialName("CompletionText") val value: String,
  @SerialName("ListItemText") val presentableText: String? = null,
  @SerialName("ToolTip") val description: String? = null,
  @SerialName("ResultType") val type: PowerShellCompletionResultType,
)

// https://learn.microsoft.com/en-us/dotnet/api/system.management.automation.completionresulttype
@ApiStatus.Internal
@Serializable(with = CompletionResultTypeSerializer::class)
enum class PowerShellCompletionResultType(val value: Int) {
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
 * Encodes and decodes the enum using [PowerShellCompletionResultType.value] field
 */
private class CompletionResultTypeSerializer : KSerializer<PowerShellCompletionResultType> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(PowerShellCompletionResultType::class.java.name, PrimitiveKind.INT)

  override fun serialize(encoder: Encoder, value: PowerShellCompletionResultType) {
    encoder.encodeInt(value.value)
  }

  override fun deserialize(decoder: Decoder): PowerShellCompletionResultType {
    val value: Int = decoder.decodeInt()
    return PowerShellCompletionResultType.entries.find { it.value == value } ?: PowerShellCompletionResultType.TEXT
  }
}