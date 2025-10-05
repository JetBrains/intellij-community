// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pipenv

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.sdk.pipenv.isPipEnv
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PipEnvPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager? =
    if (sdk.isPipEnv) PipEnvPackageManager(project, sdk) else null

}