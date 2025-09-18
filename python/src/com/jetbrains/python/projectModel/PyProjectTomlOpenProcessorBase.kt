// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel

import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor

/**
 * Automatically configures a new project without `.idea/` as a project managed by Poetry if there is
 * a top-level pyproject.toml at the project root.
 * The user will be asked if
 * - There are several possible build systems for the project.
 * - The top-level pyproject.toml is added afterward in a project with existing `.idea/`.
 * - pyproject.toml files are found in non-top-level directories (requires IJPL-180733).
 */
internal abstract class PyProjectTomlOpenProcessorBase : ProjectOpenProcessor() {
  abstract val importProvider: AbstractOpenProjectProvider

  final override fun canOpenProject(file: VirtualFile): Boolean = enablePyProjectToml && importProvider.canOpenProject(file)

  final override suspend fun openProjectAsync(
    virtualFile: VirtualFile,
    projectToClose: Project?,
    forceOpenInNewFrame: Boolean,
  ): Project? {
    return importProvider.openProject(virtualFile, projectToClose, forceOpenInNewFrame)
  }

  final override fun canImportProjectAfterwards(): Boolean = true

  // TODO Requires IJPL-180733 

  final override suspend fun importProjectAfterwardsAsync(project: Project, file: VirtualFile) {
    importProvider.linkToExistingProjectAsync(file, project)
  }
}