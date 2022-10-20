// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.pip.PipBasedRepositoryManager
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class CondaRepositoryManger(project: Project, sdk: Sdk) : PipBasedRepositoryManager(project, sdk) {

  override val repositories: List<PyPackageRepository>
    get() = listOf(CondaPackageRepository) + super.repositories

  override fun allPackages(): List<String> = CondaPackageCache.packages

  override fun packagesFromRepository(repository: PyPackageRepository): List<String> {
    return if (repository is CondaPackageRepository) CondaPackageCache.packages else super.packagesFromRepository(repository)
  }

  override fun buildPackageDetails(rawInfo: String?, spec: PythonPackageSpecification): PythonPackageDetails {
    if (spec is CondaPackageSpecification) {
      val versions = CondaPackageCache[spec.name] ?: error("No conda package versions in cache")
      if (rawInfo == null) return CondaPackageDetails(spec.name, versions, PyBundle.message("conda.packaging.empty.pypi.info"))

      val detailsFromPyPI = super.buildPackageDetails(rawInfo, spec)

      return CondaPackageDetails(detailsFromPyPI.name,
                                 versions,
                                 detailsFromPyPI.summary,
                                 detailsFromPyPI.description,
                                 detailsFromPyPI.descriptionContentType,
                                 detailsFromPyPI.documentationUrl)

    }
    return super.buildPackageDetails(rawInfo, spec)
  }

  override suspend fun initCaches() {
    super.initCaches()
    if (CondaPackageCache.isEmpty()) {
      CondaPackageCache.refreshAll(sdk, project)
    }
  }

  override suspend fun refreshCashes() {
    super.refreshCashes()
    CondaPackageCache.refreshAll(sdk, project)
  }
}