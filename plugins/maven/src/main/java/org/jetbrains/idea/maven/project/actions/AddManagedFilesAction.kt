/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
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

    val singlePomSelection: FileChooserDescriptor = object : FileChooserDescriptor(true, true, false, false, false, false) {
      override fun isFileSelectable(file: VirtualFile?): Boolean {
        return super.isFileSelectable(file) && !manager.isManagedFile(file!!)
      }

      override fun isFileVisible(file: VirtualFile, showHiddenFiles: Boolean): Boolean {
        return if (!file.isDirectory && !MavenActionUtil.isMavenProjectFile(file)) false else super.isFileVisible(file, showHiddenFiles)
      }
    }

    val files = withContext(Dispatchers.EDT) {
      val fileToSelect = e.getData(CommonDataKeys.VIRTUAL_FILE)
      FileChooser.chooseFiles(singlePomSelection, project, fileToSelect)
    }

    if (files.size != 1) return

    val projectFile = files[0]
    val selectedFiles = if (projectFile.isDirectory) projectFile.children else files
    if (selectedFiles.any { MavenActionUtil.isMavenProjectFile(it) }) {
      val openProjectProvider = MavenOpenProjectProvider()
      openProjectProvider.linkToExistingProjectAsync(projectFile, project)
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