package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.jetbrains.performancePlugin.PerformanceTestSpan

class CaptureMemoryMetricsCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "captureMemoryMetrics"
    const val SPAN_NAME: String = "memoryUsage"
    private var prevHeapUsageMb: Long? = null
  }

  override suspend fun doExecute(context: PlaybackContext) {
   val postfix = extractCommandArgument(PREFIX);

    val memory = MemoryCapture.capture()
    val span = PerformanceTestSpan.getTracer(false)
      .spanBuilder(SPAN_NAME)
      .setParent(PerformanceTestSpan.getContext())
      span
        .setAttribute("usedHeapMemoryUsageMb${postfix}", memory.usedMb)
        .setAttribute("maxHeapMemoryUsageMb${postfix}", memory.maxMb)
    prevHeapUsageMb?.also {
     span.setAttribute("diffUsedHeapMemoryUsageMb${postfix}", it - memory.usedMb)
    }
    span.startSpan().end()
    prevHeapUsageMb = memory.usedMb
  }
}