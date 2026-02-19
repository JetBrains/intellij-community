// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.cache

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
@State(name = "HuggingFaceLastCacheRefresh", storages = [Storage("HuggingFaceLastCacheRefresh.xml")])
class HuggingFaceLastCacheRefresh : PersistentStateComponent<HuggingFaceLastCacheRefresh> {

  var lastRefreshTime: Long = 0L

  override fun getState(): HuggingFaceLastCacheRefresh = this

  override fun loadState(state: HuggingFaceLastCacheRefresh) {
    XmlSerializerUtil.copyBean(state, this)
  }
}
