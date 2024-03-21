// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.cache

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.ApiStatus
import java.time.Instant


@ApiStatus.Internal data class HuggingFaceMdCacheEntry(val data: String, val timeFetched: Instant)

@ApiStatus.Internal
@State(name = "HuggingFaceMdCardsCache", storages = [Storage("HuggingFaceMdCardsCache.xml")])
object HuggingFaceMdCardsCache: PersistentStateComponent<HuggingFaceMdCardsCache> {

  private val cacheMap: MutableMap<String, HuggingFaceMdCacheEntry> = mutableMapOf()
  private var serializedCacheData: String = ""

  override fun getState(): HuggingFaceMdCardsCache {
    return this
  }

  override fun loadState(state: HuggingFaceMdCardsCache) {
    XmlSerializerUtil.copyBean(state, this)
    val dataFromSerializedState = serializedCacheData
    deserializeCacheData(dataFromSerializedState)
  }

  private fun serializeCacheData(): String {
    return cacheMap.map { (id, entry) ->
      listOf(id, entry.data, entry.timeFetched.epochSecond).joinToString(separator = "|")
    }.joinToString(separator = "\n")
  }

  private fun deserializeCacheData(serialized: String) {
    cacheMap.clear()
    cacheMap.putAll(serialized.lines().mapNotNull { line ->
      val parts = line.split("|")
      if (parts.size == 3) {
        val id = parts[0]
        val data = parts[1]
        val instant = Instant.ofEpochSecond(parts[2].toLongOrNull() ?: return@mapNotNull null)
        id to HuggingFaceMdCacheEntry(data, instant)
      } else null
    }.toMap())
  }

  fun getData(name: String): HuggingFaceMdCacheEntry? = cacheMap[name]

  fun saveData(id: String, data: HuggingFaceMdCacheEntry) {
    cacheMap[id] = data
    serializedCacheData = serializeCacheData()  // Update the serialized data
  }
}
