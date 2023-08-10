/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.buildsystem.model.unified.UnifiedDependency
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

internal object UnifiedCoordinatesSerializer : KSerializer<UnifiedCoordinates> {

    override val descriptor = buildClassSerialDescriptor(UnifiedCoordinates::class.qualifiedName!!) {
        element<String>("groupId", isOptional = true)
        element<String>("artifactId", isOptional = true)
        element<String>("version", isOptional = true)
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var groupId: String? = null
        var artifactId: String? = null
        var version: String? = null
        loop@ while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break@loop
                0 -> groupId = decodeStringElement(descriptor, 0)
                1 -> artifactId = decodeStringElement(descriptor, 1)
                2 -> version = decodeStringElement(descriptor, 2)
                else -> throw SerializationException("Unexpected index $index")
            }
        }
        UnifiedCoordinates(groupId, artifactId, version)
    }

    override fun serialize(encoder: Encoder, value: UnifiedCoordinates) = encoder.encodeStructure(descriptor) {
        value.groupId?.let { encodeStringElement(descriptor, 0, it) }
        value.artifactId?.let { encodeStringElement(descriptor, 1, it) }
        value.version?.let { encodeStringElement(descriptor, 2, it) }
    }
}

internal object UnifiedDependencySerializer : KSerializer<UnifiedDependency> {

    override val descriptor = buildClassSerialDescriptor(UnifiedDependency::class.qualifiedName!!) {
        element("coordinates", UnifiedCoordinatesSerializer.descriptor)
        element<String>("scope", isOptional = true)
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var coordinates: UnifiedCoordinates? = null
        var scope: String? = null
        loop@ while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break@loop
                0 -> coordinates = decodeSerializableElement(descriptor, 0, UnifiedCoordinatesSerializer)
                1 -> scope = decodeStringElement(descriptor, 1)
                else -> throw SerializationException("Unexpected index $index")
            }
        }
        UnifiedDependency(
            coordinates = requireNotNull(coordinates) { "coordinates property missing while deserializing ${UnifiedDependency::class.qualifiedName}" },
            scope = scope
        )
    }

    override fun serialize(encoder: Encoder, value: UnifiedDependency) = encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, UnifiedCoordinatesSerializer, value.coordinates)
        value.scope?.let { encodeStringElement(descriptor, 1, it) }
    }
}
