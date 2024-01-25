package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.project.DumbService.Companion.getInstance
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.util.Alarm
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

class WaitForSmartCommand(text: String, line: Int) : AbstractCommand(text, line) {
  private val myAlarm = Alarm()

  override fun _execute(context: PlaybackContext): Promise<Any> {
    val completion = AsyncPromise<Any>()
    completeWhenSmartModeIsLongEnough(context.project, completion)
    return completion
  }

  private fun completeWhenSmartModeIsLongEnough(project: Project, completion: AsyncPromise<Any>) {
    getInstance(project).runWhenSmart {
      myAlarm.addRequest(
        {
          if (isDumb(project)) {
            completeWhenSmartModeIsLongEnough(project, completion)
          }
          else {
            completion.setResult(null)
          }
        }, SMART_MODE_MINIMUM_DELAY)
    }
  }

  companion object {
    const val PREFIX: String = CMD_PREFIX + "waitForSmart"

    /**
     * Wait for 3 seconds after the dumb mode completes.
     *
     *
     * There are background IDE processes (<backgroundPostStartupActivity></backgroundPostStartupActivity>)
     * that start after 5 seconds of project opening, or in general, at random time.
     *
     *
     * We would like to maximize the chances that the IDE and the project are fully ready for work,
     * to make our tests predictable and reproducible.
     *
     *
     * This is not a guaranteed solution: there may be services that schedule background tasks
     * (by means of `AppExecutorUtil#getAppExecutorService()`) and it may be hard to know that they all have completed.
     *
     *
     * TODO: a better place for this awaiting is in the ProjectLoaded test script runner.
     * But for now we use this `waitForSmart` only in indexes-related tests and
     * call it before every "comparing" command (checkIndices/compareIndexes/etc)
     */
    private const val SMART_MODE_MINIMUM_DELAY = 3000
  }
}
