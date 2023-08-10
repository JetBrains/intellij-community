// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.impl.PsiManagerEx

private class PsiExternalResourceNotifier : ProjectActivity {
  override suspend fun execute(project: Project) = blockingContext {
    project.messageBus.simpleConnect().subscribe(ExternalResourceListener.TOPIC, ExternalResourceListener {
      if (!project.isDisposed) {
        PsiManagerEx.getInstanceEx(project).beforeChange(true)
        DaemonCodeAnalyzer.getInstance(project).restart()
      }
    })
  }
}