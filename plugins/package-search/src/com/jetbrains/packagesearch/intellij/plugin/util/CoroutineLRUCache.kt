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

package com.jetbrains.packagesearch.intellij.plugin.util

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import org.apache.commons.collections.map.LRUMap

@Suppress("UNCHECKED_CAST")
class CoroutineLRUCache<K : Any, V>(val maxSize: Int, initialValues: Map<K, V> = emptyMap()) {

    companion object {

        inline fun <reified K : Any, reified V> serializer(
            keySerializer: KSerializer<K> = serializer<K>(),
            valueSerializer: KSerializer<V> = serializer<V>()
        ) = object : KSerializer<CoroutineLRUCache<K, V>> {

            private val mapSerializer = MapSerializer(keySerializer, valueSerializer)

            override val descriptor = buildClassSerialDescriptor(
                "${CoroutineLRUCache::class.qualifiedName}<${K::class.qualifiedName}, ${V::class.qualifiedName}>"
            ) {
                element<Int>("maxSize")
                element("map", mapSerializer.descriptor)
            }

            override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
                var maxSize: Int? = null
                var map: Map<K, V>? = null
                loop@ while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> maxSize = decodeIntElement(descriptor, 0)
                        1 -> map = decodeSerializableElement(descriptor, 1, mapSerializer)
                        CompositeDecoder.DECODE_DONE -> break@loop
                        else -> throw SerializationException("Unexpected index $index")
                    }
                }
                CoroutineLRUCache(requireNotNull(maxSize), requireNotNull(map))
            }

            override fun serialize(encoder: Encoder, value: CoroutineLRUCache<K, V>) = encoder.encodeStructure(descriptor) {
                encodeIntElement(descriptor, 0, value.maxSize)
                encodeSerializableElement(descriptor, 1, mapSerializer, runBlocking { value.cachedElements() })
            }
        }
    }

    private val cache = LRUMap(maxSize).apply { putAll(initialValues) }
    private val syncMutex = Mutex()

    suspend fun get(key: K): V? = syncMutex.withLock { cache[key] as? V }

    suspend fun getValue(key: K): V = checkNotNull(get(key)) { "Key $key not available" }

    suspend fun put(key: K, value: V) {
        syncMutex.withLock { cache.put(key, value) }
    }

    suspend fun getOrTryPutDefault(key: K, default: suspend () -> V) = syncMutex.withLock {
        val value = cache[key] as? V
        value ?: default().also { cache[key] = it }
    }

    suspend fun clear() {
        syncMutex.withLock { cache.clear() }
    }

    suspend fun cachedElements() =
        syncMutex.withLock { cache.map { (k, v) -> k as K to v as V }.toMap() }
}
