// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.cache

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.XMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls


@ApiStatus.Internal data class HuggingFaceMdCacheEntry(@Nls val data: String, val timeFetched: Long)

@ApiStatus.Internal
@State(name = "HuggingFaceMdCardsCache", storages = [Storage("HuggingFaceMdCardsCache.xml")])
object HuggingFaceMdCardsCache: PersistentStateComponent<HuggingFaceMdCardsCache> {

  @XMap(propertyElementName = "entry", keyAttributeName = "id")
  private var cacheMap: MutableMap<String, HuggingFaceMdCacheEntry> = mutableMapOf()

  override fun getState(): HuggingFaceMdCardsCache {
    return this
  }

  override fun loadState(state: HuggingFaceMdCardsCache) {
    XmlSerializerUtil.copyBean(state, this)
  }

  fun getData(name: String): HuggingFaceMdCacheEntry? = cacheMap[name]

  fun saveData(id: String, data: HuggingFaceMdCacheEntry) {
    cacheMap[id] = data
  }
}
