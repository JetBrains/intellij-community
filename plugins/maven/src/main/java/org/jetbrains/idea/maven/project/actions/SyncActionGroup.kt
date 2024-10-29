// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil

class SyncActionGroup : DefaultActionGroup(), DumbAware {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.setEnabled(isEnabled(e))
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private fun isEnabled(e: AnActionEvent): Boolean {
    if (!MavenActionUtil.hasProject(e.dataContext)) return false
    val projectsManager = MavenActionUtil.getProjectsManager(e.dataContext)
    return projectsManager != null && projectsManager.isMavenizedProject
  }
}
