// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.freezes

import com.intellij.diagnostic.FreezeAnalysis
import com.intellij.diagnostic.LogMessage
import com.intellij.diagnostic.ThreadDump
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtils
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.registry.Registry
import com.intellij.performanceTesting.freezes.diogen.FreezeTitleGenerator
import com.intellij.performanceTesting.freezes.diogen.ThreadDumpParser
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

  fun dumpedThreads(event: LogMessage, dump: ThreadDump, durationMs: Long): FreezeReason? {
    val frozenPlugin = analyzeFreezeCausingPlugin(dump.rawDump)?.plugin ?: return null
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

private val stackTracePattern: Regex = """at (\S+)\.(\S+)\(([^:]+):(\d+)\)""".toRegex()

private fun analyzeFreezeCausingPlugin(dump: String): FreezeAnalysis.Result? {
  val threads = ThreadDumpParser.getStackTraces(dump)
  val cause = ThreadDumpParser.analyzeFreeze(threads).cause ?: return null
  val topCallable = FreezeTitleGenerator.selectCallable(cause) ?: return null

  val lines = cause.lines
  val startIndex = lines.indexOfFirst { line ->
    line.toString().trim().removePrefix("at ").startsWith(topCallable)
  }
  if (startIndex < 0) return null

  for (i in startIndex until lines.size) {
    val element = parseStackTraceElement(lines[i].toString()) ?: continue
    val descriptor = PluginUtils.getPluginDescriptorOrPlatformByClassName(element.className) ?: continue
    if (descriptor.pluginId == PluginManagerCore.CORE_ID) continue
    return FreezeAnalysis.Result(descriptor.pluginId, topCallable)
  }
  return FreezeAnalysis.Result(null, topCallable)
}

private fun parseStackTraceElement(stackTrace: String): StackTraceElement? {
  return stackTracePattern.find(stackTrace.trim())?.let { matchResult ->
    val (className, methodName, fileName, lineNumber) = matchResult.destructured
    StackTraceElement(className, methodName, fileName, lineNumber.toInt())
  }
}

internal data class FreezeReason(
  val pluginId: PluginId,
  val event: LogMessage,
  val durationMs: Long,
  val reportToUser: Boolean,
)
