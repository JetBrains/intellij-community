// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class CondaPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager? =
    if (sdk.isCondaVirtualEnv) createCondaPackageManager(project, sdk) else null

  private fun createCondaPackageManager(project: Project, sdk: Sdk): PythonPackageManager =
    CondaPackageManager(project, sdk)
}