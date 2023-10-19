package com.jetbrains.performancePlugin

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

internal class PerformanceTestTotalTimeTimer : StartupActivity.DumbAware {
  override fun runActivity(project: Project) {
    if (ProjectLoaded.TEST_SCRIPT_FILE_PATH != null) {
      val myTimer = Timer()
      myTimer.start(TOTAL_TEST_TIMER_NAME, true)
      val connection = ApplicationManager.getApplication().messageBus.connect()
      connection.subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
        override fun appWillBeClosed(isRestart: Boolean) {
          myTimer.stop()
        }
      })
    }
  }

  companion object {
    const val TOTAL_TEST_TIMER_NAME: String = "test"
  }
}
