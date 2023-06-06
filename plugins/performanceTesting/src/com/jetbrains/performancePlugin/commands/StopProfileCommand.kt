package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.jetbrains.performancePlugin.profilers.Profiler.Companion.getCurrentProfilerHandler
import com.jetbrains.performancePlugin.profilers.Profiler.Companion.isAnyProfilingStarted
import com.jetbrains.performancePlugin.profilers.ProfilersController
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

/**
 * Command stops async or yourkit profiler based on system property *integrationTests.profiler*.
 *
 *
 * In case when yourkit profiler was used parameters are ignored.
 *
 *
 * Syntax: %stopProfile [parameters]
 * Example: %stopProfile collapsed,flamegraph,traces=5000
 */
class StopProfileCommand(text: String, line: Int) : AbstractCommand(text, line) {
  override fun _execute(context: PlaybackContext): Promise<Any> {
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
    ProfilersController.getInstance()
    if (!isAnyProfilingStarted()) actionCallback.reject("Profiling hasn't been started")
    try {
      val reportsPath = getCurrentProfilerHandler().stopProfileWithNotification(actionCallback, extractCommandArgument(PREFIX))
      ProfilersController.getInstance().reportsPath = reportsPath
      ProfilersController.getInstance().isStoppedByScript = true
      actionCallback.setDone()
    }
    catch (exception: Exception) {
      actionCallback.reject(exception.message)
      LOG.error(exception)
    }
    return actionCallback.toPromise()
  }

  companion object {
    const val PREFIX = CMD_PREFIX + "stopProfile"
    private val LOG = Logger.getInstance(StopProfileCommand::class.java)
  }
}
