package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.commands.Waiter.checkCondition
import com.jetbrains.performancePlugin.profilers.Profiler
import java.util.*

/**
 * Command starts async or yourkit profiler based on system property integrationTests.profiler.
 * Command must be followed by activity name and optionally by parameters.
 *
 *
 * Syntax: %startProfile &lt;activityName&gt; [parameters]
 * Example: %startProfile magento_inspection event=alloc
 */
internal class StartProfileCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
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
    Profiler.getCurrentProfilerHandler().startProfiling(activityName, parameters)
    checkCondition { Profiler.isAnyProfilingStarted() }.await()
  }
}
