// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.freezes

import com.intellij.diagnostic.FreezeAnalysis
import com.intellij.diagnostic.LogMessage
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
import com.intellij.ide.plugins.PluginUtils
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.diagnostic.freezeAnalyzer.FreezeAnalyzer
import com.intellij.util.application

@Service(Service.Level.APP)
internal class PluginFreezeWatcher {
  @Volatile
  private var currentFreeze: FreezeReason? = null

  companion object {
    @JvmStatic
    fun getInstance(): PluginFreezeWatcher = service()
  }

  fun getFreezeReason(): FreezeReason? = currentFreeze

  fun reset() {
    currentFreeze = null
  }

  fun processFreeze(event: LogMessage, durationMs: Long): FreezeReason? {
    val frozenPlugin = PluginUtil.getInstance().findPluginId(event.throwable) ?: return null
    val pluginDescriptor = PluginManagerCore.getPlugin(frozenPlugin) ?: return null

    if (!isWorthReportingToUser(pluginDescriptor, frozenPlugin)) {
      return FreezeReason(frozenPlugin, event, durationMs, reportToUser = false)
    }

    val freezeStorageService = PluginsFreezesService.getInstance()
    if (freezeStorageService.shouldBeIgnored(frozenPlugin)) {
      return FreezeReason(frozenPlugin, event, durationMs, reportToUser = false)
    }
    freezeStorageService.setLatestFreezeDate(frozenPlugin)

    currentFreeze = FreezeReason(frozenPlugin, event, durationMs, reportToUser = true)

    return currentFreeze
  }

  private fun isWorthReportingToUser(plugin: IdeaPluginDescriptor, frozenPlugin: PluginId): Boolean {
    if (application.isInternal || application.isEAP) return true
    if (Registry.`is`("ide.diagnostics.notification.freezes.in.bundled.plugins")) return true

    return !plugin.isBundled
           && !plugin.isImplementationDetail
           && !ApplicationInfoImpl.getShadowInstance().isEssentialPlugin(frozenPlugin)
  }
}

internal class PluginFreezeAnalysis : FreezeAnalysis {
  override fun analyzeFreeze(dump: String): FreezeAnalysis.Result? {
    return analyzeFreezeCausingPlugin(dump)
  }
}

private fun analyzeFreezeCausingPlugin(dump: String): FreezeAnalysis.Result? {
  val freezeCause = FreezeAnalyzer.analyzeFreezeCause(dump) ?: return null
  for (element in freezeCause.stackFrames) {
    val descriptor = PluginUtils.getPluginDescriptorOrPlatformByClassName(element.className) ?: continue
    if (descriptor.pluginId == PluginManagerCore.CORE_ID) continue
    return FreezeAnalysis.Result(descriptor.pluginId, freezeCause.topCallable)
  }
  return FreezeAnalysis.Result(null, freezeCause.topCallable)
}

internal data class FreezeReason(
  val pluginId: PluginId,
  val event: LogMessage,
  val durationMs: Long,
  val reportToUser: Boolean,
)