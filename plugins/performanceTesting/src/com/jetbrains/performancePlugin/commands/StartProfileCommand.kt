package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.commands.Waiter.checkCondition
import com.jetbrains.performancePlugin.profilers.Profiler
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import java.util.*

/**
 * Command starts async or yourkit profiler based on system property integrationTests.profiler.
 * Command must be followed by activity name and optionally by parameters.
 *
 *
 * Syntax: %startProfile &lt;activityName&gt; [parameters]
 * Example: %startProfile magento_inspection event=alloc
 */
class StartProfileCommand(text: String, line: Int) : AbstractCommand(text, line) {
  override fun _execute(context: PlaybackContext): Promise<Any> {
    val myActionCallback: ActionCallback = ActionCallbackProfilerStopper()
    val executedCommand = extractCommandArgument(PREFIX).split("\\s+".toRegex(), limit = 2).toTypedArray()
    val myActivityName = executedCommand[0] //activityName should go right after %startProfile
    if (StringUtil.isEmpty(myActivityName)) {
      myActionCallback.reject(PerformanceTestingBundle.message("command.start.error"))
    }
    else {
      try {
        val parameters = if (executedCommand.size > 1) Arrays.asList(
          *executedCommand[1].trim { it <= ' ' }.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        else ArrayList()
        Profiler.getCurrentProfilerHandler().startProfiling(myActivityName, parameters)
        checkCondition { Profiler.isAnyProfilingStarted() }.await()
        myActionCallback.setDone()
      }
      catch (e: Throwable) {
        myActionCallback.reject(e.message)
      }
    }
    return myActionCallback.toPromise()
  }

  companion object {
    const val PREFIX = CMD_PREFIX + "startProfile"
  }
}
