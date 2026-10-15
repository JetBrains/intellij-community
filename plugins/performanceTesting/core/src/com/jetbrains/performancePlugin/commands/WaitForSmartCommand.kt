// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class WaitForSmartCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "waitForSmart"
    private val LOG = logger<WaitForSmartCommand>()

    /**
     * Wait for 3 seconds after the dumb mode and the scanning complete.
     * If the IDE is not in smart mode after the wait, wait again, up to [MAX_ATTEMPTS] times.
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
    private const val SMART_MODE_MINIMUM_DELAY_SECONDS = 3
    private const val MAX_ATTEMPTS = 5
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    for (attempt in 1..MAX_ATTEMPTS) {
      smartReadAction(project) {
      }
      LOG.info("%waitForSmart dumb mode finished, waiting $SMART_MODE_MINIMUM_DELAY_SECONDS seconds more... (attempt $attempt)")
      delay(SMART_MODE_MINIMUM_DELAY_SECONDS.seconds)
      if (DumbService.getInstance(project).canRunSmart()) {
        LOG.info("%waitForSmart command finished")
        return
      }
    }
    throw IllegalStateException("%waitForSmart: the IDE did not stay in smart mode for $SMART_MODE_MINIMUM_DELAY_SECONDS seconds " +
                                "in $MAX_ATTEMPTS attempts. Dumb mode or scanning starts again and again.")
  }
}
