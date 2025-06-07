// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.vfs.toNioPathOrNull
import java.nio.file.Path

class UvOpenProvider() : AbstractOpenProjectProvider() {
  override val systemId: ProjectSystemId = UvConstants.SYSTEM_ID

  override fun isProjectFile(file: VirtualFile): Boolean {
    return file.name == UvConstants.UV_LOCK || isUvSpecificPyProjectToml(file)
  }

  private fun isUvSpecificPyProjectToml(file: VirtualFile): Boolean {
    return file.name == UvConstants.PYPROJECT_TOML && UV_TOOL_TABLE_HEADER.find(file.readText()) != null
  }

  override suspend fun linkProject(projectFile: VirtualFile, project: Project) {
    val projectDirectory = getProjectDirectory(projectFile) 
    val projectRootPath = projectDirectory.toNioPathOrNull() ?: Path.of(projectDirectory.path)
    project.service<UvSettings>().addLinkedProject(projectRootPath)
    UvProjectModelService.syncProjectModelRoot(project, projectRootPath)
  }

  override suspend fun unlinkProject(project: Project, externalProjectPath: String) {
    UvProjectModelService.forgetProjectModelRoot(project, Path.of(externalProjectPath))
  }

  companion object {
    val UV_TOOL_TABLE_HEADER: Regex = """\[tool\.uv[.\w-]*]""".toRegex()
  }
}
