package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.jetbrains.performancePlugin.profilers.Profiler.Companion.getCurrentProfilerHandler
import com.jetbrains.performancePlugin.profilers.Profiler.Companion.isAnyProfilingStarted
import com.jetbrains.performancePlugin.profilers.ProfilersController

/**
 * Command stops async or yourkit profiler based on system property *integrationTests.profiler*.
 * In case when yourkit profiler was used parameters are ignored.
 * Syntax: %stopProfile [parameters]
 * Example: %stopProfile collapsed,flamegraph,traces=5000
 */
internal class StopProfileCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "stopProfile"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val profilerController = ProfilersController.getInstance()
    check(isAnyProfilingStarted()) {
      "Profiling hasn't been started"
    }

    val reportPath = getCurrentProfilerHandler().stopProfileAsyncWithNotification(extractCommandArgument(PREFIX))
    profilerController.reportsPath = reportPath
    profilerController.isStoppedByScript = true
  }
}
