// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.setupPy

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.FilesModificationTrackerBase
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
internal class SetupPyModificationTracker(project: Project) : FilesModificationTrackerBase(project) {
  override fun isFileSupported(virtualFile: VirtualFile): Boolean = virtualFile.name == SetupPyHelpers.SETUP_PY

  companion object {
    fun getInstance(project: Project): SetupPyModificationTracker = project.service()
  }
}