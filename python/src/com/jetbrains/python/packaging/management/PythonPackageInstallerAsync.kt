// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.common.PythonPackageSpecificationBase
import com.jetbrains.python.packaging.common.PythonSimplePackageSpecification
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PythonPackagesInstallerAsync {

  companion object {
    fun installPackages(
      project: Project,
      requirements: List<PyRequirement>?,
      extraArgs: List<String>,
      indicator: ProgressIndicator,
    ) {
      val packageService = PyPackagingToolWindowService.getInstance(project)

      packageService.serviceScope.launch(Dispatchers.IO) {
        if (requirements.isNullOrEmpty()) {
          installWithoutRequirements(packageService, extraArgs, indicator)
        }
        else {
          installWithRequirements(packageService, requirements, extraArgs, indicator)
        }
      }
    }

    private suspend fun installWithoutRequirements(
      packageService: PyPackagingToolWindowService,
      extraArgs: List<String>,
      indicator: ProgressIndicator,
    ) {
      indicator.text = PyBundle.message("python.packaging.installing.packages")
      indicator.isIndeterminate = true

      val emptySpecification = PythonPackageSpecificationBase("", null, null, null)
      packageService.installPackage(emptySpecification, extraArgs)
    }

    private suspend fun installWithRequirements(
      packageService: PyPackagingToolWindowService,
      requirements: List<PyRequirement>,
      extraArgs: List<String>,
      indicator: ProgressIndicator,
    ) {
      requirements.forEachIndexed { index, requirement ->
        indicator.text = PyBundle.message("python.packaging.progress.text.installing.specific.package", requirement.presentableText)
        updateProgress(indicator, index, requirements.size)

        val specification = createSpecificationForRequirement(requirement)
        packageService.installPackage(specification, extraArgs)
      }
    }

    private fun updateProgress(indicator: ProgressIndicator, index: Int, total: Int) {
      indicator.isIndeterminate = index == 0
      if (total > 0) {
        indicator.fraction = index.toDouble() / total
      }
    }

    private fun createSpecificationForRequirement(
      requirement: PyRequirement,
    ): PythonPackageSpecification {
      val packageName = requirement.name
      return PythonSimplePackageSpecification(packageName, null, null)
    }
  }
}
