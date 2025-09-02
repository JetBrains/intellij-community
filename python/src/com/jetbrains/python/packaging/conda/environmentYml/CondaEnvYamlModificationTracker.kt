// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda.environmentYml

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.FilesModificationTrackerBase
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
internal class CondaEnvYamlModificationTracker(project: Project) : FilesModificationTrackerBase(project) {
  override fun isFileSupported(virtualFile: VirtualFile): Boolean {
    return virtualFile.name in setOf(CondaEnvironmentYmlSdkUtils.ENV_YML_FILE_NAME, CondaEnvironmentYmlSdkUtils.ENV_YAML_FILE_NAME)
  }

  companion object {
    fun getInstance(project: Project): CondaEnvYamlModificationTracker {
      return project.service()
    }
  }
}