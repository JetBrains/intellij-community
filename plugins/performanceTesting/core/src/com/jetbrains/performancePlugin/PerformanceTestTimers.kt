// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.atomic.AtomicBoolean

/** The timer that runs from the first script command to the application exit. */
private const val TOTAL_TEST_TIMER_NAME: String = "test"

private val testTimerStarted = AtomicBoolean()

/**
 * Starts the test timer once, before the first script command runs.
 * [PlaybackRunnerExtended] calls it, so the start does not depend on the project opening or on the indexing.
 */
internal fun startTestTimerOnce() {
  if (ProjectLoaded.TEST_SCRIPT_FILE_PATH == null || !testTimerStarted.compareAndSet(false, true)) {
    return
  }
  startTimerUntilAppClose(TOTAL_TEST_TIMER_NAME)
}

private fun startTimerUntilAppClose(name: String) {
  val timer = Timer()
  timer.start(name, true)
  ApplicationManager.getApplication().messageBus.connect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
      timer.stop()
    }
  })
}
