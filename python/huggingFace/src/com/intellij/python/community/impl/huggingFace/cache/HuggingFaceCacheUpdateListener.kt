// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.cache

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic

interface HuggingFaceCacheUpdateListener {
  fun cacheUpdated()

  companion object {
    val TOPIC: Topic<HuggingFaceCacheUpdateListener> = Topic.create("HuggingFaceCacheUpdateEvent", HuggingFaceCacheUpdateListener::class.java)

    fun notifyCacheUpdated() {
      ApplicationManager.getApplication().messageBus
        .syncPublisher(TOPIC)
        .cacheUpdated()
    }
  }
}
