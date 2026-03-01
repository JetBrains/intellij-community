// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.platform.rpc.topics.ApplicationRemoteTopic
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import org.jetbrains.annotations.ApiStatus
import javax.swing.KeyStroke

/**
 * Keymap changes performed on the backend are not automatically synced with the frontend.
 * So, this topic is a workaround for this problem.
 * It should be used on the backend to pass action shortcut change to the frontend.
 */
@ApiStatus.Internal
val TERMINAL_ACTION_SHORTCUT_CHANGED_TOPIC: ApplicationRemoteTopic<TerminalActionShortcutChangedEvent> =
  ApplicationRemoteTopic("org.jetbrains.plugins.terminal.actionShortcutChanged", TerminalActionShortcutChangedEvent.serializer())

@ApiStatus.Internal
@Serializable
data class TerminalActionShortcutChangedEvent(
  val actionId: String,
  @Serializable(with = KeyboardShortcutSerializer::class)
  val shortcut: KeyboardShortcut?,
)

@OptIn(ExperimentalSerializationApi::class)
private object KeyboardShortcutSerializer : KSerializer<KeyboardShortcut> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("KeyboardShortcut") {
    element<String>("firstKeyStroke")
    element<String?>("secondKeyStroke")
  }

  override fun serialize(encoder: Encoder, value: KeyboardShortcut) {
    encoder.encodeStructure(descriptor) {
      encodeStringElement(descriptor, 0, value.firstKeyStroke.toString())
      encodeNullableSerializableElement(descriptor, 1, serializer<String?>(), value.secondKeyStroke?.toString())
    }
  }

  override fun deserialize(decoder: Decoder): KeyboardShortcut {
    return decoder.decodeStructure(descriptor) {
      var firstKeyStroke: String? = null
      var secondKeyStroke: String? = null
      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          0 -> firstKeyStroke = decodeStringElement(descriptor, 0)
          1 -> secondKeyStroke = decodeNullableSerializableElement(descriptor, 1, serializer<String?>())
          CompositeDecoder.DECODE_DONE -> break
          else -> error("Unexpected index: $index")
        }
      }
      KeyboardShortcut(
        KeyStroke.getKeyStroke(firstKeyStroke!!),
        secondKeyStroke?.let { KeyStroke.getKeyStroke(it) }
      )
    }
  }
}