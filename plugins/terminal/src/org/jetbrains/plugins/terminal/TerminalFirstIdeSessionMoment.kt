// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.util.BuildNumber
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed interface TerminalFirstIdeSessionMoment {
  @Serializable
  data class Version(
    @Serializable(with = BuildNumberSerializer::class)
    val build: BuildNumber,
  ) : TerminalFirstIdeSessionMoment

  @Serializable
  object BeforeTrackingStarted : TerminalFirstIdeSessionMoment
}

private object BuildNumberSerializer : KSerializer<BuildNumber> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BuildNumber", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: BuildNumber) {
    encoder.encodeString(value.asString())
  }

  override fun deserialize(decoder: Decoder): BuildNumber {
    return BuildNumber.fromString(decoder.decodeString())!!
  }
}