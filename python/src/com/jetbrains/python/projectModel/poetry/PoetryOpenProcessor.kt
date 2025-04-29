// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.Nls

/**
 * Automatically configures a new project without `.idea/` as a project managed by Poetry if there is
 * a top-level pyproject.toml at the project root. 
 * The user will be asked if
 * - There are several possible build systems for the project.
 * - The top-level pyproject.toml is added afterward in a project with existing `.idea/`.
 * - pyproject.toml files are found in non-top-level directories (requires IJPL-180733).
 */
class PoetryOpenProcessor: ProjectOpenProcessor() {
  private val importProvider = PoetryOpenProvider()
  
  override val name: @Nls String = PyBundle.message("python.project.model.poetry")

  override fun canOpenProject(file: VirtualFile): Boolean {
    return Registry.`is`("python.project.model.poetry") && importProvider.canOpenProject(file)
  }

  override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    return runUnderModalProgressIfIsEdt { importProvider.openProject(virtualFile, projectToClose, forceOpenInNewFrame) }
  }

  override suspend fun openProjectAsync(virtualFile: VirtualFile,
                                        projectToClose: Project?,
                                        forceOpenInNewFrame: Boolean): Project? {
    return importProvider.openProject(virtualFile, projectToClose, forceOpenInNewFrame)
  }

  override fun canImportProjectAfterwards(): Boolean = true

  // TODO Requires IJPL-180733 
  override suspend fun importProjectAfterwardsAsync(project: Project, file: VirtualFile) {
    importProvider.linkToExistingProjectAsync(file, project)
  }
}