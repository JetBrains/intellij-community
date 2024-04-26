// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.grazie.async

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object AsyncUtils {
  fun isNonAsyncMode(): Boolean {
    return ApplicationManager.getApplication().isUnitTestMode
  }

  fun restartInspection(application: Application) {
    if (application.isDisposed) {
      return
    }

    for (project in ProjectManager.getInstance().openProjects.filter { it.isInitialized && it.isOpen && !it.isDefault }) {
      DaemonCodeAnalyzer.getInstance(project)?.restart()
    }
  }

  fun run(project: Project, body: suspend () -> Unit) {
    if (isNonAsyncMode()) {
      runBlockingMaybeCancellable {
        body()
      }
      return
    }

    val toRun: () -> Unit = {
      (project as ComponentManagerEx).getCoroutineScope().launch {
        body()

        withContext(Dispatchers.EDT) {
          restartInspection(ApplicationManager.getApplication())
        }
      }
    }

    // prevent registration of startup activities for the default project
    if (project.isDefault) {
      toRun()
    }
    else {
      StartupManager.getInstance(project).runAfterOpened { toRun() }
    }
  }
}
