// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

private const val TOTAL_TEST_TIMER_NAME: String = "test"

private class PerformanceTestTotalTimeTimer : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (ProjectLoaded.TEST_SCRIPT_FILE_PATH == null) {
      return
    }

    val timer = Timer()
    timer.start(TOTAL_TEST_TIMER_NAME, true)
    ApplicationManager.getApplication().messageBus.connect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
      override fun appWillBeClosed(isRestart: Boolean) {
        timer.stop()
      }
    })
  }
}
