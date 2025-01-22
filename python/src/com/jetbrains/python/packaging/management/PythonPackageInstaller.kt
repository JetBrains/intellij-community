// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonPackageSpecificationBase
import com.jetbrains.python.packaging.common.PythonSimplePackageSpecification

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
          installWithRequirements(manager, requirements, extraArgs, indicator)
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
      manager.installPackage(emptySpecification, emptyList()).getOrElse {
        return Result.failure(it)
      }

      return Result.success(Unit)
    }

    private suspend fun installWithRequirements(
      manager: PythonPackageManager,
      requirements: List<PyRequirement>,
      extraArgs: List<String>,
      indicator: ProgressIndicator,
    ): Result<Unit> {
      requirements.forEachIndexed { index, requirement ->
        indicator.text = PyBundle.message("python.packaging.progress.text.installing.specific.package", requirement.presentableText)
        updateProgress(indicator, index, requirements.size)

        val specification = PythonSimplePackageSpecification(requirement.name, requirement.versionSpecs.firstOrNull()?.version, null)
        manager.installPackage(specification, extraArgs).onFailure {
          return Result.failure(it)
        }
      }

      return Result.success(Unit)
    }

    private fun updateProgress(indicator: ProgressIndicator, index: Int, total: Int) {
      indicator.isIndeterminate = index == 0
      if (total > 0) {
        indicator.fraction = index.toDouble() / total
      }
    }
  }
}
