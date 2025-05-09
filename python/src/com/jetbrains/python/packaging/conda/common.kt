// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.components.service
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.repository.PyPIPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.buildPackageDetailsBySimpleDetailsProtocol

class CondaPackage(
  name: String, version: String,
  editableMode: Boolean,
  val installedWithPip: Boolean = false,
) : PythonPackage(name, version, editableMode) {
  override fun toString(): String {
    return "CondaPackage(name='$name', version='$version', installedWithPip=$installedWithPip)"
  }
}

class CondaPackageDetails(
  override val name: String,
  override val availableVersions: List<String> = emptyList(),
  override val summary: String? = null,
  override val description: String? = null,
  override val descriptionContentType: String? = null,
  override val documentationUrl: String? = null,
) : PythonPackageDetails {
  override val repository: PyPackageRepository = CondaPackageRepository
}

fun PythonPackageDetails?.toCondaPackageDetails(packageName: String, availableVersions: List<String>): CondaPackageDetails = when (this) {
  null -> CondaPackageDetails(
    name = packageName,
    availableVersions = availableVersions,
    summary = PyBundle.message("conda.packaging.empty.pypi.info")
  )
  else -> CondaPackageDetails(
    availableVersions = availableVersions,
    name = packageName,
    summary = summary,
    description = description,
    descriptionContentType = descriptionContentType,
    documentationUrl = documentationUrl
  )
}


object CondaPackageRepository : PyPackageRepository("Conda", null, null) {
  override fun getPackages(): Set<String> {
    return service<CondaPackageCache>().packages
  }

  override fun buildPackageDetails(packageName: String): PyResult<PythonPackageDetails> {
    val versions = service<CondaPackageCache>()[packageName]
                   ?: return PyResult.failure(MessageError("No conda package versions in cache"))

    val pypiSimpleDetails = PyPIPackageRepository.buildPackageDetailsBySimpleDetailsProtocol(packageName).getOrNull()
    val condaDetails = pypiSimpleDetails.toCondaPackageDetails(packageName, versions)

    return PyResult.success(condaDetails)
  }
}