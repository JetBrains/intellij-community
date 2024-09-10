// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.PythonSimplePackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Install [packages] to [sdk]
 */
suspend fun installPackages(project: Project, sdk: Sdk, vararg packages: String) {
  val packageManager = PythonPackageManager.forSdk(project, sdk)
  supervisorScope { // Not install other packages if one failed
    for (packageName in packages) {
      launch {
        packageManager.installPackage(PythonSimplePackageSpecification(packageName, null, null), emptyList()).getOrThrow()
      }
    }
  }
}