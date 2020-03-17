// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ContentsUtil.addContent
import org.jetbrains.idea.svn.SvnUtil.toIoFiles
import org.jetbrains.idea.svn.SvnVcs
import org.jetbrains.idea.svn.dialogs.PropertiesComponent

class ShowPropertiesAction : BasicAction() {
  override fun getActionName(): String = "Show Properties"

  override fun needsAllFiles(): Boolean = false

  override fun isEnabled(vcs: SvnVcs, file: VirtualFile): Boolean {
    val status = FileStatusManager.getInstance(vcs.project).getStatus(file)
    return status != null && status != FileStatus.UNKNOWN && status != FileStatus.IGNORED
  }

  override fun perform(vcs: SvnVcs, file: VirtualFile, context: DataContext) =
    batchPerform(vcs, arrayOf(file), context)

  override fun batchPerform(vcs: SvnVcs, files: Array<VirtualFile>, context: DataContext) {
    val ioFiles = toIoFiles(files)
    var w = ToolWindowManager.getInstance(vcs.project).getToolWindow(PropertiesComponent.ID)
    val component: PropertiesComponent
    if (w == null) {
      w = ToolWindowManager.getInstance(vcs.project)
        .registerToolWindow(PropertiesComponent.ID, false, ToolWindowAnchor.BOTTOM, vcs.project, true)
      component = PropertiesComponent()
      val content = ContentFactory.SERVICE.getInstance().createContent(component, "", false)
      addContent(w.contentManager, content, true)
    }
    else {
      component = w.contentManager.contents[0].component as PropertiesComponent
    }
    w.title = ioFiles[0].name
    w.show(null)
    w.activate(Runnable { component.setFile(vcs, ioFiles[0]) })
  }

  override fun isBatchAction(): Boolean = false
}