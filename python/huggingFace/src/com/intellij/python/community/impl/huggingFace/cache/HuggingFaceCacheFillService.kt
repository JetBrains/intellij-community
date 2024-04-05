// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.cache

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceApi
import com.intellij.python.community.impl.huggingFace.service.HuggingFaceCoroutine
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.time.Duration

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class HuggingFaceCacheFillService(private val project: Project) {
  private var wasFilled = false

  fun triggerCacheFillIfNeeded() = HuggingFaceCoroutine.Utils.ioScope.launch {
    if (wasFilled) return@launch
    val refreshState = project.getService(HuggingFaceLastCacheRefresh::class.java)
    val currentTime = System.currentTimeMillis()
    if (currentTime - refreshState.lastRefreshTime < Duration.ofDays(1).toMillis()) return@launch
    wasFilled = true

    try {
      val modelFill = async { HuggingFaceApi.fillCacheWithBasicApiData(HuggingFaceEntityKind.MODEL, HuggingFaceModelsCache, HuggingFaceConstants.MAX_MODELS_IN_CACHE) }
      val datasetFill = async { HuggingFaceApi.fillCacheWithBasicApiData(HuggingFaceEntityKind.DATASET, HuggingFaceDatasetsCache, HuggingFaceConstants.MAX_DATASETS_IN_CACHE) }
      modelFill.await()
      datasetFill.await()
      HuggingFaceCacheUpdateListener.notifyCacheUpdated()
      wasFilled = true
    } catch (e: IOException) {
      thisLogger().error(e)
      wasFilled = false
    }
  }
}
