// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import javax.swing.Icon

internal class MavenProjectOpenProcessor : ProjectOpenProcessor() {
  private val importProvider = MavenOpenProjectProvider()

  override val name: String
    get() = importProvider.getName()

  override val icon: Icon?
    get() = importProvider.getIcon()

  override fun canOpenProject(file: VirtualFile): Boolean = importProvider.canOpenProject(file)

  override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    return runUnderModalProgressIfIsEdt { importProvider.openProject(virtualFile, projectToClose, forceOpenInNewFrame) }
  }

  override suspend fun openProjectAsync(virtualFile: VirtualFile,
                                        projectToClose: Project?,
                                        forceOpenInNewFrame: Boolean): Project? {
    return importProvider.openProject(virtualFile, projectToClose, forceOpenInNewFrame)
  }

  override fun canImportProjectAfterwards(): Boolean = true

  override suspend fun importProjectAfterwardsAsync(project: Project, file: VirtualFile) {
    importProvider.linkToExistingProjectAsync(file, project)
  }
}