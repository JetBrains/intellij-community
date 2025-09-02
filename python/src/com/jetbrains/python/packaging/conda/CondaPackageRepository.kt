// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.components.service
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.repository.PyPIPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.buildPackageDetailsBySimpleDetailsProtocol
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec

object CondaPackageRepository : PyPackageRepository("Conda", null, null) {
  override fun getPackages(): Set<String> {
    return service<CondaPackageCache>().packages
  }

  override fun buildPackageDetails(packageName: String): PyResult<PythonPackageDetails> {
    val versions = getVersionForPackage(packageName)
                   ?: return PyResult.failure(MessageError("No conda package versions in cache"))

    val pypiSimpleDetails = PyPIPackageRepository.buildPackageDetailsBySimpleDetailsProtocol(packageName).getOrNull()
    val condaDetails = pypiSimpleDetails.toCondaPackageDetails(packageName, versions)

    return PyResult.success(condaDetails)
  }

  override fun hasPackage(
    packageName: String,
    versionSpecs: PyRequirementVersionSpec?,
  ): Boolean {
    val availableVersions = getVersionForPackage(packageName) ?: return false

    if (versionSpecs == null)
      return true

    return availableVersions.any {
      versionSpecs.matches(it)
    }
  }

  private fun getVersionForPackage(packageName: String) = service<CondaPackageCache>()[packageName]
}