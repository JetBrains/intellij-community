// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageSpecificationBase
import com.jetbrains.python.packaging.common.PythonSimplePackageSpecification
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PythonPackagesInstaller {
  companion object {
    @JvmStatic
    fun installPackages(
      project: Project,
      sdk: Sdk,
      requirements: List<PyRequirement>?,
      extraArgs: List<String>,
      indicator: ProgressIndicator,
    ): ExecutionException? {
      runBlockingCancellable {
        val manager = PythonPackageManager.forSdk(project, sdk)

        return@runBlockingCancellable if (requirements.isNullOrEmpty()) {
          installWithoutRequirements(manager, indicator)
        }
        else {
          installWithRequirements(manager, requirements, extraArgs)
        }
      }.exceptionOrNull()?.let {
        return ExecutionException(it)
      }

      return null
    }

    private suspend fun installWithoutRequirements(
      manager: PythonPackageManager,
      indicator: ProgressIndicator,
    ): Result<Unit> {
      indicator.text = PyBundle.message("python.packaging.installing.packages")
      indicator.isIndeterminate = true

      val emptySpecification = PythonPackageSpecificationBase("", null, null, null)
      return manager.installPackage(emptySpecification, emptyList(), withBackgroundProgress = true).map { }
    }

    private suspend fun installWithRequirements(
      manager: PythonPackageManager,
      requirements: List<PyRequirement>,
      extraArgs: List<String>,
    ): Result<Unit> {
      val specs = requirements.map { requirement ->
         PythonSimplePackageSpecification(requirement.name, requirement.versionSpecs.firstOrNull()?.version, null)
      }
      return manager.installPackages(specs, extraArgs, withBackgroundProgress = true).map { }
    }

    @JvmStatic
    fun uninstallPackages(project: Project, sdk: Sdk, packages: List<PyPackage>, indicator: ProgressIndicator): ExecutionException? {
      runBlockingCancellable {
        indicator.isIndeterminate = true

        val manager = PythonPackageManager.forSdk(project, sdk)
        val pythonPackages = packages.map { it.toPythonPackage() }

        return@runBlockingCancellable uninstallPackagesProcess(manager, pythonPackages)
      }.exceptionOrNull()?.let {
        return ExecutionException(it)
      }

      return null
    }


    suspend fun uninstallPackagesProcess(manager: PythonPackageManager, packages: List<PythonPackage>): Result<Unit> {
      for (pkg in packages) {
        manager.uninstallPackage(pkg).onFailure {
          return Result.failure(it)
        }
      }

      return Result.success(Unit)
    }

    private fun PyPackage.toPythonPackage(): PythonPackage = PythonPackage(this.name, this.version, false)
  }
}