// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.PythonSimplePackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManager
import kotlinx.coroutines.supervisorScope
import org.jetbrains.annotations.CheckReturnValue

/**
 * Install [packages] to [sdk].
 * Returns error if packages couldn't be installed due to execution error
 */
@CheckReturnValue
suspend fun installPackages(project: Project, sdk: Sdk, vararg packages: String): Result<Unit> {
  val packageManager = PythonPackageManager.forSdk(project, sdk)
  return supervisorScope { // Not install other packages if one failed
    for (packageName in packages) {
      val packageSpecification = PythonSimplePackageSpecification(packageName, null, null)
      packageManager.installPackage(packageSpecification, emptyList<String>()).onFailure { return@supervisorScope Result.failure(it) }
    }
    return@supervisorScope Result.success(Unit)
  }
}

