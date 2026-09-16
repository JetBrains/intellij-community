// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.common

import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlin.reflect.KClass

/** Supplies a serializer for one Python tool configuration type. */
interface PyToolConfigurationSerializerProvider<C : PyToolConfigurationDto> {
  val configurationClass: KClass<C>
  val serializer: KSerializer<C>
}

/** Serializes configuration types supplied through the Python Tools extension point. */
object PyToolConfigurationSerializer : KSerializer<PyToolConfigurationDto> {
  private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
  }

  override val descriptor: SerialDescriptor = PyToolConfigurationEnvelope.serializer().descriptor

  override fun serialize(encoder: Encoder, value: PyToolConfigurationDto) {
    val envelope = when (value) {
      is UnknownPyToolConfigurationDto -> value.envelope
      else -> {
        val provider = PyToolConfigurationSerializers.find(value)
        provider?.encode(value) ?: PyToolConfigurationEnvelope(value.javaClass.name, JsonNull)
      }
    }
    encoder.encodeSerializableValue(PyToolConfigurationEnvelope.serializer(), envelope)
  }

  override fun deserialize(decoder: Decoder): PyToolConfigurationDto {
    val envelope = decoder.decodeSerializableValue(PyToolConfigurationEnvelope.serializer())
    val provider = PyToolConfigurationSerializers.find(envelope.serialName)
                   ?: return UnknownPyToolConfigurationDto(envelope)
    return try {
      provider.decode(envelope.payload)
    }
    catch (_: SerializationException) {
      UnknownPyToolConfigurationDto(envelope)
    }
    catch (_: IllegalArgumentException) {
      UnknownPyToolConfigurationDto(envelope)
    }
  }

  private fun <C : PyToolConfigurationDto> PyToolConfigurationSerializerProvider<C>.encode(
    value: PyToolConfigurationDto,
  ): PyToolConfigurationEnvelope {
    if (!configurationClass.isInstance(value)) {
      throw SerializationException("The serializer does not support ${value.javaClass.name}")
    }
    return PyToolConfigurationEnvelope(
      serialName = serializer.descriptor.serialName,
      payload = json.encodeToJsonElement(serializer, configurationClass.java.cast(value)),
    )
  }

  private fun <C : PyToolConfigurationDto> PyToolConfigurationSerializerProvider<C>.decode(payload: JsonElement): C =
    json.decodeFromJsonElement(serializer, payload)
}

internal object PyToolConfigurationSerializers {
  private val EP_NAME = ExtensionPointName.create<PyToolConfigurationSerializerProvider<*>>(
    "com.intellij.python.pytools.configurationSerializerProvider"
  )

  fun find(value: PyToolConfigurationDto): PyToolConfigurationSerializerProvider<*>? =
    EP_NAME.extensionList.firstOrNull { it.configurationClass.isInstance(value) }

  fun find(serialName: String): PyToolConfigurationSerializerProvider<*>? =
    EP_NAME.extensionList.firstOrNull { it.serializer.descriptor.serialName == serialName }
}

@Serializable
private data class PyToolConfigurationEnvelope(
  val serialName: String,
  val payload: JsonElement,
)

private class UnknownPyToolConfigurationDto(
  val envelope: PyToolConfigurationEnvelope,
) : PyToolConfigurationDto
