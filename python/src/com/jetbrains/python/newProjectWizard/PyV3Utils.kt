// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
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
    val specifications = packages.map {
      packageManager.createPackageSpecification(it)
      ?: return@supervisorScope Result.failure(IllegalArgumentException(
        PyBundle.message("python.packaging.error.package.is.not.listed.in.repositories", it)
      ))
    }
    val requests = specifications.map { PythonPackageInstallRequest.ByRepositoryPythonPackageSpecification(it) }
    return@supervisorScope packageManager.installPackages(requests, emptyList(), withBackgroundProgress = true).map {
      // We don't care about result, we just want to fail if any package failed to install
    }
  }
}

