// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.grazie.async

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.util.ui.UIUtil

internal object AsyncUtils {
  fun isNonAsyncMode(): Boolean {
    return ApplicationManager.getApplication().isUnitTestMode || ApplicationManager.getApplication().isHeadlessEnvironment
  }

  fun restartInspection(application: Application) {
    if (application.isDisposed) return

    for (project in ProjectManager.getInstance().openProjects.filter { it.isInitialized && it.isOpen && !it.isDefault }) {
      DaemonCodeAnalyzer.getInstance(project)?.restart()
    }
  }

  fun run(project: Project, body: () -> Unit) {
    if (isNonAsyncMode()) {
      body()
    } else {
      val toRun: () -> Unit = {
        val app = ApplicationManager.getApplication()

        app.executeOnPooledThread {
          if (app.isDisposed) return@executeOnPooledThread

          body()

          UIUtil.invokeLaterIfNeeded {
            restartInspection(app)
          }
        }
      }

      // prevent registration of startup activities for the default project
      if (project.isInitialized) {
        toRun()
      } else {
        StartupManager.getInstance(project).runWhenProjectIsInitialized { toRun() }
      }
    }
  }
}
