// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.PyPackageVersionNormalizer
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.pip.PipBasedRepositoryManager
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class CondaRepositoryManger(
  override val project: Project,
  @Deprecated("Don't use sdk from here") override val sdk: Sdk
) : PipBasedRepositoryManager() {

  override val repositories: List<PyPackageRepository>
    get() = listOf(CondaPackageRepository) + super.repositories

  override fun buildPackageDetails(rawInfo: String?, spec: PythonPackageSpecification): PythonPackageDetails {
    if (spec is CondaPackageSpecification) {
      val versions = service<CondaPackageCache>()[spec.name] ?: error("No conda package versions in cache")
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

  override suspend fun getLatestVersion(spec: PythonPackageSpecification): PyPackageVersion? {
    if (spec is CondaPackageSpecification) {
      if (spec.name == "python") return null
      val versions = service<CondaPackageCache>()[spec.name]
      if (versions.isNullOrEmpty()) {
        thisLogger().info("No versions in conda cache for package ${spec.name}")
        return null
      }
      return PyPackageVersionNormalizer.normalize(versions.first())
    }
    return super.getLatestVersion(spec)
  }

  override suspend fun refreshCaches() {
    super.refreshCaches()
    service<CondaPackageCache>().forceReloadCache(sdk, project)
  }

  override suspend fun initCaches() {
    super.initCaches()
    service<CondaPackageCache>().reloadCache(sdk, project)
  }

  override fun searchPackages(query: String, repository: PyPackageRepository): List<String> {
    return if (repository is CondaPackageRepository) {
      service<CondaPackageCache>().packages
        .filter { StringUtil.containsIgnoreCase(it, query) }
    }
    else {
      super.searchPackages(query, repository)
    }
  }
}