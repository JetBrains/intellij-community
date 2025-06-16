// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.profilers.Profiler
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Command starts async or yourkit profiler based on system property integrationTests.profiler.
 * Command must be followed by activity name and optionally by parameters.
 *
 *
 * Syntax: %startProfile &lt;activityName&gt; [parameters]
 * Example: %startProfile magento_inspection event=alloc
 */
class StartProfileCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX = "${CMD_PREFIX}startProfile"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val executedCommand = extractCommandArgument(PREFIX).split("\\s+".toRegex(), limit = 2)
    // activityName should go right after %startProfile
    val activityName = executedCommand[0]
    if (activityName.isEmpty()) {
      throw IllegalStateException(PerformanceTestingBundle.message("command.start.error"))
    }

    val parameters = if (executedCommand.size > 1) executedCommand[1].trim().split(',').dropLastWhile { it.isEmpty() } else emptyList()
    Profiler.getCurrentProfilerHandler().startProfilingAsync(activityName, parameters)
    withTimeout(2.minutes) {
      while (!Profiler.isAnyProfilingStarted()) {
        delay(10.milliseconds)
      }
    }
  }
}
