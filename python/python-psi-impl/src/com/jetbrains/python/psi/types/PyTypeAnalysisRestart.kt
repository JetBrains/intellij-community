// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiManager

/**
 * Runs the analysis again in each open project after a setting that changes the result of the type inference.
 *
 * The cached types must go before the analysis runs again. [PsiManager.dropPsiCaches] drops them, and
 * [DaemonCodeAnalyzer.restart] then highlights the open files with the new setting.
 */
internal fun restartTypeAnalysis(reason: String) {
  if (ApplicationManager.getApplication().isUnitTestMode) return

  ApplicationManager.getApplication().invokeLater {
    for (project in ProjectManager.getInstance().openProjects) {
      if (project.isDisposed) continue
      PsiManager.getInstance(project).dropPsiCaches()
      DaemonCodeAnalyzer.getInstance(project).restart(reason)
    }
  }
}
