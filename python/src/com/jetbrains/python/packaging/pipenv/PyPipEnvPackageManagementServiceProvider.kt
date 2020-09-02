// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.pipenv

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageManagementServiceProvider
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.sdk.pipenv.isPipEnv

class PyPipEnvPackageManagementServiceProvider : PyPackageManagementServiceProvider {
  override fun tryCreateForSdk(project: Project, sdk: Sdk): PyPackageManagementService? {
    return if (sdk.isPipEnv) PyPipEnvPackageManagementService(project, sdk) else null
  }
}