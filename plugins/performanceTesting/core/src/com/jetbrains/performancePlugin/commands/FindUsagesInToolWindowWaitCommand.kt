// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.usages.UsageView
import com.intellij.usages.UsageViewManager
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.commands.FindUsagesInToolWindowCommand.Companion.FIRST_USAGE_SPAN_NAME
import com.jetbrains.performancePlugin.commands.FindUsagesInToolWindowCommand.Companion.SPAN_NAME
import com.jetbrains.performancePlugin.commands.FindUsagesInToolWindowCommand.Companion.TOOL_WINDOW_SPAN_NAME
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Command to wait for find usages in the tool window (not in the popup) to complete. See [FindUsagesInToolWindowCommand].
 */
class FindUsagesInToolWindowWaitCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME: String = "findUsagesInToolWindowWait"
    const val PREFIX: String = CMD_PREFIX + NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project

    val tracer = PerformanceTestSpan.getTracer(isWarmupMode)
    val parent = PerformanceTestSpan.getContext()

    val span = tracer.spanBuilder(SPAN_NAME).setParent(parent).startSpan()
    val firstUsageSpan = tracer.spanBuilder(FIRST_USAGE_SPAN_NAME).setParent(parent).startSpan()
    val toolWindowSpan = tracer.spanBuilder(TOOL_WINDOW_SPAN_NAME).setParent(parent).startSpan()

    var usageView: UsageView? = null

    try {
      withTimeout(10.seconds) {
        usageView = withContext(Dispatchers.EDT) {
          UsageViewManager.getInstance(project).selectedUsageView
        }
        while (usageView == null) {
          delay(50.milliseconds)
          usageView = withContext(Dispatchers.EDT) {
            UsageViewManager.getInstance(project).selectedUsageView
          }
        }
      }
    }
    catch (_: TimeoutCancellationException) {
      throw Exception("Timeout while waiting for the usage view to open")
    }

    toolWindowSpan!!.end()
    firstUsageSpan?.end()

    while (usageView!!.isSearchInProgress) {
      delay(50.milliseconds)
    }

    span!!.setAttribute("number", usageView.usages.size.toLong())
    span.end()

    FindUsagesDumper.storeMetricsDumpFoundUsages(usageView.usages.toMutableList(), project)
  }

  override fun getName(): String = PREFIX
}
