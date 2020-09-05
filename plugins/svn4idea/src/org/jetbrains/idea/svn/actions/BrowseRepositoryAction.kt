// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserDialog
import java.awt.BorderLayout
import javax.swing.JPanel

private const val REPOSITORY_BROWSER_TOOLWINDOW = "SVN Repositories"

class BrowseRepositoryAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    if (project == null) {
      val dialog = RepositoryBrowserDialog(ProjectManager.getInstance().defaultProject)
      dialog.show()
    }
    else {
      val manager = ToolWindowManager.getInstance(project)
      var w = manager.getToolWindow(REPOSITORY_BROWSER_TOOLWINDOW)
      if (w == null) {
        val component = RepositoryToolWindowPanel(project)
        w = manager.registerToolWindow(REPOSITORY_BROWSER_TOOLWINDOW, true, ToolWindowAnchor.BOTTOM, project, true)
        w.helpId = "reference.svn.repository"
        val content = ContentFactory.SERVICE.getInstance().createContent(component, "", false)
        Disposer.register(content, component)
        w.contentManager.addContent(content)
      }
      w.show(null)
      w.activate(null)
    }
  }
}

private class RepositoryToolWindowPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
  private val dialog = RepositoryBrowserDialog(project)

  init {
    add(dialog.createBrowserComponent(true), BorderLayout.CENTER)
    add(dialog.createToolbar(false), BorderLayout.WEST)
  }

  override fun dispose() {
    dialog.disposeRepositoryBrowser()
    ToolWindowManager.getInstance(project).unregisterToolWindow(REPOSITORY_BROWSER_TOOLWINDOW)
  }
}
