// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.idea.svn.SvnBundle.message
import org.jetbrains.idea.svn.SvnBundle.messagePointer
import org.jetbrains.idea.svn.SvnUtil.toIoFiles
import org.jetbrains.idea.svn.SvnVcs
import org.jetbrains.idea.svn.dialogs.PropertiesComponent

class ShowPropertiesAction : BasicAction() {
  override fun getActionName(): String = message("action.name.show.properties")

  override fun needsAllFiles(): Boolean = false

  override fun isBatchAction(): Boolean = false

  override fun isEnabled(vcs: SvnVcs, file: VirtualFile): Boolean {
    val status = FileStatusManager.getInstance(vcs.project).getStatus(file)
    return status != null && status != FileStatus.UNKNOWN && status != FileStatus.IGNORED
  }

  override fun perform(vcs: SvnVcs, file: VirtualFile, context: DataContext) =
    batchPerform(vcs, arrayOf(file), context)

  override fun batchPerform(vcs: SvnVcs, files: Array<VirtualFile>, context: DataContext) {
    val toolWindow = getToolWindow(vcs.project) ?: registerToolWindow(vcs.project)
    val component = toolWindow.contentManager.contents[0].component as PropertiesComponent
    val file = toIoFiles(files)[0]

    toolWindow.title = file.name
    toolWindow.activate(Runnable { component.setFile(vcs, file) })
  }

  private fun getToolWindow(project: Project): ToolWindow? =
    ToolWindowManager.getInstance(project).getToolWindow(PropertiesComponent.ID)

  private fun registerToolWindow(project: Project): ToolWindow =
    ToolWindowManager.getInstance(project).registerToolWindow(
      RegisterToolWindowTask(
        PropertiesComponent.ID,
        component = PropertiesComponent(),
        canCloseContent = false,
        stripeTitle = messagePointer("toolwindow.stripe.SVN_Properties")
      )
    )
}