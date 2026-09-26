// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.freezes

import com.intellij.diagnostic.LogMessage
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.registry.Registry

@Service(Service.Level.APP)
internal class PluginFreezeWatcher {
  @Volatile
  private var currentFreeze: FreezeReason? = null

  companion object {
    fun getInstance(): PluginFreezeWatcher = service()
  }

  fun getFreezeReason(): FreezeReason? = currentFreeze

  fun reset() {
    currentFreeze = null
  }

  fun processFreeze(event: LogMessage, problematicPluginId: PluginId, durationMs: Long): FreezeReason? {
    if (!isWorthReportingToUser(problematicPluginId)) {
      return FreezeReason(problematicPluginId, event, durationMs, reportToUser = false)
    }

    val freezeStorageService = PluginsFreezesService.getInstance()
    if (freezeStorageService.shouldBeIgnored(problematicPluginId)) {
      return FreezeReason(problematicPluginId, event, durationMs, reportToUser = false)
    }
    freezeStorageService.setLatestFreezeDate(problematicPluginId)

    currentFreeze = FreezeReason(problematicPluginId, event, durationMs, reportToUser = true)

    return currentFreeze
  }

  private fun isWorthReportingToUser(@Suppress("UNUSED_PARAMETER") plugin: PluginId): Boolean {
    return Registry.`is`("ide.diagnostics.notification.freezes.in.plugins")
  }
}

internal data class FreezeReason(
  val pluginId: PluginId,
  val event: LogMessage,
  val durationMs: Long,
  val reportToUser: Boolean,
)