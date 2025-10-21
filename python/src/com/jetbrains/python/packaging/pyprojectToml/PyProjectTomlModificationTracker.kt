// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pyprojectToml

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.FilesModificationTrackerBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.PY_PROJECT_TOML

@Service(Service.Level.PROJECT)
internal class PyProjectTomlModificationTracker(project: Project) : FilesModificationTrackerBase(project) {
  override fun isFileSupported(virtualFile: VirtualFile): Boolean = virtualFile.name == PY_PROJECT_TOML

  companion object {
    fun getInstance(project: Project): PyProjectTomlModificationTracker = project.service()
  }

}