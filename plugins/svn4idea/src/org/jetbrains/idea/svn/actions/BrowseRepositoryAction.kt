// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.svn.SvnBundle.messagePointer
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserDialog

@NonNls private const val REPOSITORY_BROWSER_TOOLWINDOW_ID = "SVN Repositories"

private fun getRepositoryBrowserToolWindow(project: Project): ToolWindow? =
  ToolWindowManager.getInstance(project).getToolWindow(REPOSITORY_BROWSER_TOOLWINDOW_ID)

class BrowseRepositoryAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project

    if (project == null) {
      RepositoryBrowserDialog(ProjectManager.getInstance().defaultProject).show()
    }
    else {
      val toolWindow = getRepositoryBrowserToolWindow(project) ?: registerRepositoryBrowserToolWindow(project)
      toolWindow.activate(null)
    }
  }

  private fun registerRepositoryBrowserToolWindow(project: Project): ToolWindow =
    ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask(
      id = REPOSITORY_BROWSER_TOOLWINDOW_ID,
      component = RepositoryToolWindowPanel(project),
      stripeTitle = messagePointer("toolwindow.stripe.SVN_Repositories")
    )).apply {
      helpId = "reference.svn.repository"
    }
}

private class RepositoryToolWindowPanel(private val project: Project) : BorderLayoutPanel(), Disposable {
  private val dialog = RepositoryBrowserDialog(project)

  init {
    addToCenter(dialog.createBrowserComponent(true))
    addToLeft(dialog.createToolbar(false))
  }

  override fun dispose() {
    dialog.disposeRepositoryBrowser()
    getRepositoryBrowserToolWindow(project)?.remove()
  }
}
