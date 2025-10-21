// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pyprojectToml

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.FilesModificationTrackerBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.jetbrains.python.packaging.dependencies.cache.PythonDependenciesManagerCached
import com.jetbrains.python.sdk.associatedModuleDir
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class PyProjectTomlManager(project: Project, val sdk: Sdk) : PythonDependenciesManagerCached(project) {
  override fun getDependenciesFile(): VirtualFile? {
    val moduleDir = sdk.associatedModuleDir ?: return null
    return moduleDir.findChild(PY_PROJECT_TOML)
  }

  override fun getModificationTracker(): FilesModificationTrackerBase {
    return PyProjectTomlModificationTracker.getInstance(project)
  }
}