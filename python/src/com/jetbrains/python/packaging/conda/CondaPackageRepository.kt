// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.components.service
import com.intellij.python.requirements.PyPackageVersionComparator
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.common.ProjectUrl
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.repository.PyPackageRepositories
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.buildPackageDetailsBySimpleDetailsProtocol
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object CondaPackageRepository : PyPackageRepository("Conda", null, null) {
  override var enabled: Boolean
    get() = service<PyPackageRepositories>().isDefaultRepoEnabled(name)
    set(value) {
      service<PyPackageRepositories>().setDefaultRepoEnabled(name, value)
    }

  override suspend fun search(needle: String, pageSize: Int): PythonPackageSearchResult =
    service<CondaPackageCache>().search(needle, pageSize)

  override fun getSize(): Int =
    service<CondaPackageCache>().size

  override fun getProjectUrl(packageName: String): ProjectUrl {
    val encoded = URLEncoder.encode(packageName, StandardCharsets.UTF_8)
    return ProjectUrl("Anaconda", "https://anaconda.org/anaconda/$encoded")
  }

  override suspend fun buildPackageDetails(packageName: String): PyResult<PythonPackageDetails> {
    val versions = getVersionsForPackage(packageName)

    if (versions.isEmpty()) {
      return PyResult.failure(
        MessageError(PyBundle.message("python.packaging.cache.conda.no.versions.found", packageName))
      )
    }

    val pypiSimpleDetails = PyPiPackageRepository.buildPackageDetailsBySimpleDetailsProtocol(packageName).getOrNull()
    val condaDetails = pypiSimpleDetails.toCondaPackageDetails(packageName, versions)

    return PyResult.success(condaDetails)
  }

  override suspend fun hasPackage(name: String): Boolean =
    service<CondaPackageCache>().contains(name)

  override suspend fun hasPackage(pyPackage: PyRequirement): Boolean {
    val availableVersions = getVersionsForPackage(pyPackage.name)

    if (pyPackage.versionSpecs.isEmpty()) {
      return true
    }

    return availableVersions.any { version ->
      pyPackage.versionSpecs.all { it.matches(version) }
    }
  }

  suspend fun getVersionsForPackage(packageName: String): List<String> =
    service<CondaPackageCache>()
      .getVersionsOfPackage(packageName)
      ?.asSequence()
      ?.sortedWith(PyPackageVersionComparator.STR_COMPARATOR.reversed())
      ?.toList()
    ?: emptyList()
}
