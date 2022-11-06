// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.psi.impl.PsiManagerEx

private class PsiExternalResourceNotifier : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    project.messageBus.simpleConnect().subscribe(ExternalResourceListener.TOPIC, ExternalResourceListener {
      if (!project.isDisposed) {
        PsiManagerEx.getInstanceEx(project).beforeChange(true)
        DaemonCodeAnalyzer.getInstance(project).restart()
      }
    })
  }
}