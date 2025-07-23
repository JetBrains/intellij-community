// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pipenv

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.FilesModificationTrackerBase
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.sdk.pipenv.PIP_FILE_LOCK

@Service(Service.Level.PROJECT)
internal class PipEnvLockModificationTracker(project: Project) : FilesModificationTrackerBase(project) {
  override fun isFileSupported(virtualFile: VirtualFile): Boolean {
    return virtualFile.name == PIP_FILE_LOCK
  }

  companion object {
    fun getInstance(project: Project): PipEnvLockModificationTracker {
      return project.service()
    }
  }
}