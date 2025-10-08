package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import org.jetbrains.annotations.Nls

internal class PyProjectOpenProcessor : ProjectOpenProcessor() {
  override val name: @Nls String = PyProjectTomlBundle.message("intellij.python.pyproject.name")

  override fun canOpenProject(file: VirtualFile): Boolean = projectModelEnabled && PyOpenProjectProvider.canOpenProject(file)

  override suspend fun openProjectAsync(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? = PyOpenProjectProvider.openProject(virtualFile, projectToClose, forceOpenInNewFrame)

  override fun canImportProjectAfterwards(): Boolean = true
  override suspend fun importProjectAfterwardsAsync(project: Project, file: VirtualFile) = PyOpenProjectProvider.linkToExistingProjectAsync(file, project)
}