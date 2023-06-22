// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil
import org.jetbrains.idea.maven.utils.actions.MavenAsyncAction
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider

class AddFileAsMavenProjectAction : MavenAsyncAction() {
  override suspend fun actionPerformedAsync(e: AnActionEvent) {
    val context = e.dataContext
    val project = MavenActionUtil.getProject(context)
    val file = getSelectedFile(context)
    if (project != null && file != null) {
      val openProjectProvider = MavenOpenProjectProvider()
      openProjectProvider.linkToExistingProjectAsync(file, project)
    }
  }

  override fun isAvailable(e: AnActionEvent): Boolean {
    val context = e.dataContext
    val file = getSelectedFile(context)
    return (super.isAvailable(e)
            && MavenActionUtil.isMavenProjectFile(file)
            && !isExistingProjectFile(context, file))
  }

  override fun isVisible(e: AnActionEvent): Boolean {
    return super.isVisible(e) && isAvailable(e)
  }

  companion object {
    private fun isExistingProjectFile(context: DataContext, file: VirtualFile?): Boolean {
      val manager = MavenActionUtil.getProjectsManager(context)
      return file != null && manager != null && manager.findProject(file) != null
    }

    private fun getSelectedFile(context: DataContext): VirtualFile? {
      return CommonDataKeys.VIRTUAL_FILE.getData(context)
    }
  }
}
