// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonPackage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue

@ApiStatus.Internal
class PythonPackagesInstaller {
  companion object {
    @JvmStatic
    @RequiresBackgroundThread
    @CheckReturnValue
    fun installPackages(
      project: Project,
      sdk: Sdk,
      requirements: List<PyRequirement>?,
      extraArgs: List<String>,
      indicator: ProgressIndicator,
    ): PyResult<Unit> = runBlockingCancellable {
      val manager = PythonPackageManager.forSdk(project, sdk)

      return@runBlockingCancellable if (requirements.isNullOrEmpty()) {
        installWithoutRequirements(manager, indicator)
      }
      else {
        installWithRequirements(manager, requirements, extraArgs)
      }
    }

    @CheckReturnValue
    private suspend fun installWithoutRequirements(
      manager: PythonPackageManager,
      indicator: ProgressIndicator,
    ): PyResult<Unit> {
      indicator.text = PyBundle.message("python.packaging.installing.packages")
      indicator.isIndeterminate = true

      val installAllRequirementsSpecification = PythonPackageInstallRequest.AllRequirements
      return manager.installPackage(installAllRequirementsSpecification, emptyList()).mapSuccess { }
    }

    @CheckReturnValue
    suspend fun installWithRequirements(
      manager: PythonPackageManager,
      requirements: Collection<PyRequirement>,
      extraArgs: List<String>,
    ): PyResult<Unit> {
      manager.waitForInit()
      val packageSpecifications = requirements.map { requirement ->
        manager.findPackageSpecificationWithVersionSpec(requirement.name, versionSpec = requirement.versionSpecs.firstOrNull())
        ?: return PyResult.localizedError(PyBundle.message("python.packaging.error.package.is.not.listed.in.repositories", requirement.name))
      }
      val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(packageSpecifications)
      return manager.installPackage(request, extraArgs).mapSuccess { }
    }

    @JvmStatic
    @CheckReturnValue
    fun uninstallPackages(project: Project, sdk: Sdk, packages: List<PyPackage>, indicator: ProgressIndicator): PyResult<Unit> = runBlockingCancellable {
      indicator.isIndeterminate = true

      val manager = PythonPackageManager.forSdk(project, sdk)
      val pythonPackages = packages.map { it.toPythonPackage() }

      return@runBlockingCancellable uninstallPackagesProcess(manager, pythonPackages)
    }


    @CheckReturnValue
    suspend fun uninstallPackagesProcess(manager: PythonPackageManager, packages: List<PythonPackage>): PyResult<Unit> {
      return manager.uninstallPackage(*packages.map { it.name }.toTypedArray()).mapSuccess {}
    }

    private fun PyPackage.toPythonPackage(): PythonPackage = PythonPackage(this.name, this.version, false)
  }
}