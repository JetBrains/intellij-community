// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lombok.backend.action.lombok

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil

internal class BackendLombokActionGroup : DefaultActionGroup(), ActionRemoteBehaviorSpecification.BackendOnly, DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val project: Project? = event.project
    val shouldShow =
      event.getData<PsiFile?>(CommonDataKeys.PSI_FILE) is PsiJavaFile && project != null && LombokLibraryUtil.hasLombokLibrary(project)
    event.presentation.setEnabledAndVisible(shouldShow)
  }
}
