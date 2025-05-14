// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.PyBundle
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal

internal class PythonHeadlessSdkUpdater : ProjectActivity, DumbAware {
  private val DELAY = 10.seconds

  companion object {
    private val LOG: Logger = logger<PythonHeadlessSdkUpdater>()
  }

  override suspend fun execute(project: Project) {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode) return
    if (!dropUpdaterInHeadless()) return // see PythonSdkUpdateProjectActivity

    scheduleTasks(project)
    waitForTasks(project)
  }

  private fun scheduleTasks(project: Project) {
    PythonSdkUpdater.getPythonSdks(project).forEach { sdk ->
      LOG.info("Scheduling update: ${sdk.homePath}")
      PythonSdkUpdater.scheduleUpdate(sdk, project)
    }
  }

  private suspend fun waitForTasks(project: Project) {
    val title = PyBundle.message("sdk.gen.updating.interpreter")
    val start = System.currentTimeMillis()

    LOG.info("Waiting for $title tasks...")
    while (PythonSdkUpdater.getPythonSdks(project).any { PythonSdkUpdater.isUpdateScheduled(it) }) {
      delay(DELAY)
    }

    val duration = StringUtil.formatDuration(System.currentTimeMillis() - start)
    LOG.info("Completed waiting for $title tasks in $duration")
  }
}