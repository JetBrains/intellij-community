// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import javax.swing.Icon

internal class MavenProjectOpenProcessor : ProjectOpenProcessor() {
  private val importProvider = MavenOpenProjectProvider()

  override fun getName(): String = importProvider.builder.name

  override fun getIcon(): Icon? = importProvider.builder.icon

  override fun canOpenProject(file: VirtualFile): Boolean = importProvider.canOpenProject(file)

  override fun doOpenProject(projectFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    return importProvider.openProject(projectFile, projectToClose, forceOpenInNewFrame)
  }

  override fun canImportProjectAfterwards(): Boolean = true

  override fun importProjectAfterwards(project: Project, file: VirtualFile) {
    importProvider.linkToExistingProject(file, project)
  }
}