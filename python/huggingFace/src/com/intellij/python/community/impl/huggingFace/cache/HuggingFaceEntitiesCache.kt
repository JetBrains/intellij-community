// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.cache

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceEntityBasicApiData
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
abstract class HuggingFaceCache(private val maxSize: Int) : PersistentStateComponent<HuggingFaceCache> {
  private var nameSet: MutableSet<String> = mutableSetOf()
  private val hotCacheMaxSize = 32

  private var cacheMap: LinkedHashMap<String, HuggingFaceEntityBasicApiData> =
    object : LinkedHashMap<String, HuggingFaceEntityBasicApiData>(maxSize, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HuggingFaceEntityBasicApiData>?): Boolean {
      return size > maxSize
      }
    }

  private val hotCache: LinkedHashMap<String, HuggingFaceEntityBasicApiData> =
    object : LinkedHashMap<String, HuggingFaceEntityBasicApiData>(16, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HuggingFaceEntityBasicApiData>?): Boolean {
        return size > hotCacheMaxSize
      }
    }

  @Synchronized
  @RequiresBackgroundThread
  fun saveEntities(entitiesMap: Map<String, HuggingFaceEntityBasicApiData>) {
    cacheMap.putAll(entitiesMap)
    entitiesMap.keys.forEach { key ->
      nameSet.add(key)
    }
  }

  @Synchronized
  @RequiresBackgroundThread
  fun saveEntity(entityData: HuggingFaceEntityBasicApiData) {
    cacheMap[entityData.itemId] = entityData
    nameSet.add(entityData.itemId)
  }

  @Synchronized
  fun isInCache(entityId: String): Boolean {
    if (hotCache.containsKey(entityId)) return true

    return if (cacheMap.containsKey(entityId)) {
      hotCache[entityId] = cacheMap[entityId]!!
      true
    } else false
  }

  @Synchronized
  fun getBasicData(entityId: String): HuggingFaceEntityBasicApiData? = cacheMap[entityId]

  @Synchronized
  fun getPipelineTagForEntity(entityId: String): String? {
    hotCache[entityId]?.let { return it.pipelineTag }
    return cacheMap[entityId]?.pipelineTag
  }

  @Synchronized
  fun getCacheSize() = cacheMap.size
  override fun getState(): HuggingFaceCache = this

  override fun loadState(state: HuggingFaceCache) {
    XmlSerializerUtil.copyBean(state, this)
    cacheMap = state.cacheMap
    nameSet = state.nameSet
    HuggingFaceCacheUpdateListener.notifyCacheUpdated()
  }
}

@State(
  name = "HuggingFaceModelsCache",
  storages = [Storage("huggingFaceModelsCache.xml")]
)
object HuggingFaceModelsCache : HuggingFaceCache(HuggingFaceConstants.MAX_MODELS_IN_CACHE)


@State(
  name = "HuggingFaceDatasetsCache",
  storages = [Storage("huggingFaceDatasetsCache.xml")]
)
object HuggingFaceDatasetsCache : HuggingFaceCache(HuggingFaceConstants.MAX_DATASETS_IN_CACHE)
