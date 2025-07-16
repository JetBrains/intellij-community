// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.getPresentablePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.actions.MavenAction
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider

class AddManagedFilesAction : MavenAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val cs = MavenCoroutineScopeProvider.getCoroutineScope(e.project)
    cs.launch { actionPerformedAsync(e) }
  }

  suspend fun actionPerformedAsync(e: AnActionEvent) {
    val project = MavenActionUtil.getProject(e.dataContext) ?: return
    val manager = MavenProjectsManager.getInstanceIfCreated(project) ?: return

    val singlePomSelection: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor()
      .withFileFilter { file -> MavenActionUtil.isMavenProjectFile(file) && !manager.isManagedFile(file) }

    val files = withContext(Dispatchers.EDT) {
      val fileToSelect = e.getData(CommonDataKeys.VIRTUAL_FILE)
      FileChooser.chooseFiles(singlePomSelection, project, fileToSelect)
    }

    if (files.size != 1)  {
      MavenLog.LOG.warn("Expected exactly one file but selected: $files")
      return
    }

    val projectFile = files[0]
    val selectedFiles = if (projectFile.isDirectory) projectFile.children else files
    if (selectedFiles.any { MavenActionUtil.isMavenProjectFile(it) }) {
      val openProjectProvider = MavenOpenProjectProvider()
      openProjectProvider.forceLinkToExistingProjectAsync(projectFile, project)
    }
    else {
      val projectPath = getPresentablePath(projectFile.path)

      val message = if (projectFile.isDirectory)
        MavenProjectBundle.message("maven.AddManagedFiles.warning.message.directory", projectPath)
      else MavenProjectBundle.message("maven.AddManagedFiles.warning.message.file", projectPath)

      val title = MavenProjectBundle.message("maven.AddManagedFiles.warning.title")
      withContext(Dispatchers.EDT) {
        Messages.showWarningDialog(project, message, title)
      }
    }
  }
}
