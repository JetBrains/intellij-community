// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.javaee.ExternalResourceListener
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.PsiManagerEx

internal class PsiExternalResourceChangeListener(val project: Project) : ExternalResourceListener {
  override fun externalResourceChanged() {
    if (!project.isDisposed) {
      PsiManagerEx.getInstanceEx(project).beforeChange(true)
      DaemonCodeAnalyzer.getInstance(project).restart()
    }
  }
}