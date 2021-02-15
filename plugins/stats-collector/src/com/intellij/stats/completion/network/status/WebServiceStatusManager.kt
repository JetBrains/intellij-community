// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.network.status

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry

object WebServiceStatusManager {
  private const val USE_ANALYTICS_PLATFORM_REGISTRY = "completion.stats.analytics.platform.send"
  private const val ANALYTICS_PLATFORM_URL_REGISTRY = "completion.stats.analytics.platform.url"
  private val LOG = logger<WebServiceStatusManager>()
  private val statuses: MutableMap<String, WebServiceStatus> = mutableMapOf()

  init {
    if (Registry.`is`(USE_ANALYTICS_PLATFORM_REGISTRY, false)) {
      registerAnalyticsPlatformStatus()
    }
  }

  fun getAllStatuses(): List<WebServiceStatus> = statuses.values.toList()

  private fun registerAnalyticsPlatformStatus() {
    try {
      val registry = Registry.get(ANALYTICS_PLATFORM_URL_REGISTRY)
      if (registry.isChangedFromDefault) {
        register(AnalyticsPlatformServiceStatus(registry.asString()))
        return
      }
    }
    catch (e: Throwable) {
      LOG.error("No url for Analytics Platform web status. Set registry: $ANALYTICS_PLATFORM_URL_REGISTRY")
    }
    register(AnalyticsPlatformServiceStatus.withDefaultUrl())
  }

  private fun register(status: WebServiceStatus) {
    val old = statuses[status.id]
    if (old != null) {
      LOG.warn("Service status with id [${old.id}] already created.")
      return
    }

    statuses[status.id] = status
  }
}