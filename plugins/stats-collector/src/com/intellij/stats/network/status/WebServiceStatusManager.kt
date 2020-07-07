// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.network.status

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry

object WebServiceStatusManager {
  private const val USE_ANALYTICS_PLATFORM_REGISTRY = "stats.collector.analytics.platform.send"
  private val LOG = logger<WebServiceStatusManager>()
  private val statuses: MutableMap<String, WebServiceStatus> = mutableMapOf()

  init {
    register(JetStatServiceStatus())
    if (Registry.`is`(USE_ANALYTICS_PLATFORM_REGISTRY, false)) {
      register(AnalyticsPlatformServiceStatus())
    }
  }

  fun getAllStatuses(): List<WebServiceStatus> = statuses.values.toList()

  private fun register(status: WebServiceStatus) {
    val old = statuses[status.id]
    if (old != null) {
      LOG.warn("Service status with id [${old.id}] already created.")
      return
    }

    statuses[status.id] = status
  }
}