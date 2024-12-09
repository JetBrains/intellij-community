// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class PipPythonPackageManager(project: Project, sdk: Sdk) : PipBasedPackageManager(project, sdk) {
  override val repositoryManager: PipRepositoryManager = PipRepositoryManager(project, sdk)
}