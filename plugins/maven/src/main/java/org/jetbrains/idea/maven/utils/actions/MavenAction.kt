// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

abstract class MavenAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    val p = e.presentation
    p.isEnabled = isAvailable(e)
    p.isVisible = isVisible(e)
  }

  protected open fun isAvailable(e: AnActionEvent): Boolean {
    return MavenActionUtil.hasProject(e.dataContext)
  }

  protected open fun isVisible(e: AnActionEvent): Boolean {
    return true
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}