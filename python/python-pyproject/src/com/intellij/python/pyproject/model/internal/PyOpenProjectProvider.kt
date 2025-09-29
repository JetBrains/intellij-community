package com.intellij.python.pyproject.model.internal

import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.python.pyproject.PY_PROJECT_TOML
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object PyOpenProjectProvider : AbstractOpenProjectProvider() {
  override val systemId = SYSTEM_ID

  override fun isProjectFile(file: VirtualFile): Boolean = @Suppress("DEPRECATION") // We have no choice: API is broken
  (runUnderModalProgressIfIsEdt {
    file.isFile && file.name == PY_PROJECT_TOML || file.isDirectory && file.findChild(PY_PROJECT_TOML) != null
  })

  override suspend fun linkProject(projectFile: VirtualFile, project: Project) {
    val projectDirectory = withContext(Dispatchers.IO) {
      getProjectDirectory(projectFile)
    }
    linkProjectWithProgress(project, projectDirectory.toNioPath())
  }
}