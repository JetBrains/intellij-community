// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager

class CaptureMemoryMetricsCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "captureMemoryMetrics"
    const val SPAN_NAME: String = "memoryUsage"
    private var prevHeapUsageMb: Long? = null
  }

  val otelMeter = TelemetryManager.getMeter(Scope("test", PlatformMetrics))

  override suspend fun doExecute(context: PlaybackContext) {
    val postfix = extractCommandArgument(PREFIX)
    val heapUsageMb = otelMeter.counterBuilder("JVM.heapUsageMb$postfix").build()
    val diffHeapUsageMb = otelMeter.counterBuilder("JVM.diffHeapUsageMb$postfix").build()
    val memory = MemoryCapture.capture()
    heapUsageMb.add(memory.usedMb)
    prevHeapUsageMb?.also {
      if (it > 0) {
        diffHeapUsageMb.add(it - memory.usedMb)
      }
    }
    prevHeapUsageMb = memory.usedMb
  }
}