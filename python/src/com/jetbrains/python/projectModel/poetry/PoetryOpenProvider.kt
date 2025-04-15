// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import java.nio.file.Path

class PoetryOpenProvider() : AbstractOpenProjectProvider() {
  override val systemId: ProjectSystemId = PoetryConstants.SYSTEM_ID

  override fun isProjectFile(file: VirtualFile): Boolean = file.name == PoetryConstants.PYPROJECT_TOML

  override suspend fun linkProject(projectFile: VirtualFile, project: Project) {
    val projectDirectory = getProjectDirectory(projectFile) 
    val projectRootPath = projectDirectory.toNioPathOrNull() ?: Path.of(projectDirectory.path)
    project.service<PoetrySettings>().addLinkedProject(projectRootPath)
    PoetryProjectResolver.syncPoetryProject(project, projectRootPath)
  }

  override suspend fun unlinkProject(project: Project, externalProjectPath: String) {
    PoetryProjectResolver.forgetPoetryProject(project, Path.of(externalProjectPath))
  }
}

