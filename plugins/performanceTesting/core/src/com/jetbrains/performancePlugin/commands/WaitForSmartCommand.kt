// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.util.Alarm
import kotlinx.coroutines.CompletableDeferred

class WaitForSmartCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "waitForSmart"

    /**
     * Wait for 3 seconds after the dumb mode completes.
     *
     * There are background IDE processes (<backgroundPostStartupActivity></backgroundPostStartupActivity>)
     * that start after 5 seconds of project opening, or in general, at random time.
     *
     * We would like to maximize the chances that the IDE and the project are fully ready for work,
     * to make our tests predictable and reproducible.
     *
     * This is not a guaranteed solution: there may be services that schedule background tasks
     * (by means of `AppExecutorUtil#getAppExecutorService()`) and it may be hard to know that they all have completed.
     *
     * TODO: a better place for this awaiting is in the ProjectLoaded test script runner.
     * But for now we use this `waitForSmart` only in indexes-related tests and
     * call it before every "comparing" command (checkIndices/compareIndexes/etc)
     */
    private const val SMART_MODE_MINIMUM_DELAY = 3000
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val alarm = Alarm(context.project.service<CodeAnalysisStateListener>().cs)
    val completion = CompletableDeferred<Unit>()
    completeWhenSmartModeIsLongEnough(context.project, completion, alarm)
    completion.await()
  }

  private fun completeWhenSmartModeIsLongEnough(project: Project, completion: CompletableDeferred<Unit>, alarm: Alarm) {
    DumbService.getInstance(project).runWhenSmart {
      alarm.addRequest(
        {
          if (runReadAction { isDumb(project) }) {
            completeWhenSmartModeIsLongEnough(project, completion, alarm)
          }
          else {
            completion.complete(Unit)
          }
        }, SMART_MODE_MINIMUM_DELAY)
    }
  }
}
