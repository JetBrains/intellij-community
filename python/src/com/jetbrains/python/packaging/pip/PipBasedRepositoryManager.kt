// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.gson.Gson
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.HttpRequests
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.PyPackageVersionComparator
import com.jetbrains.python.packaging.cache.PythonSimpleRepositoryCache
import com.jetbrains.python.packaging.common.EmptyPythonPackageDetails
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.management.packagesByRepository
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.common.PythonSimplePackageDetails
import com.jetbrains.python.packaging.repository.*
import com.jetbrains.python.packaging.repository.withBasicAuthorization
import org.jetbrains.annotations.ApiStatus
import java.time.Duration

@ApiStatus.Experimental
abstract class PipBasedRepositoryManager(project: Project, sdk: Sdk) : PythonRepositoryManager(project, sdk) {

  override val repositories: List<PyPackageRepository>
    get() = listOf(PyPIPackageRepository) + service<PythonSimpleRepositoryCache>().repositories

  private val gson = Gson()
  private val packageDetailsCache = Caffeine.newBuilder()
    .maximumSize(10)
    .expireAfterWrite(Duration.ofHours(1))
    .build<PythonPackageSpecification, PythonPackageDetails> {
      // todo[akniazev] make it possible to show info from several repos
      val repositoryUrl = it.repository?.repositoryUrl ?: PyPIPackageRepository.repositoryUrl!!
      val result = runCatching {

        val packageUrl = repositoryUrl.replace("simple", "pypi/${it.name}/json")
        HttpRequests.request(packageUrl)
          .withBasicAuthorization(it.repository)
          .readTimeout(3000)
          .readString()
      }
      if (result.isFailure) thisLogger().debug("Request failed for package $it.name")

      buildPackageDetails(result.getOrNull(), it)
    }


  override fun buildPackageDetails(rawInfo: String?, spec: PythonPackageSpecification): PythonPackageDetails {
    if (rawInfo == null) {
      val versions = tryParsingVersionsFromPage(spec.name, spec.repository?.repositoryUrl)
      val repository = if (spec.repository !is PyEmptyPackagePackageRepository) spec.repository else PyPIPackageRepository
      val repositoryName = repository?.name ?: PyPIPackageRepository.name!!
      return if (versions != null) PythonSimplePackageDetails(spec.name,
                                                              versions.sortedWith(PyPackageVersionComparator.STR_COMPARATOR.reversed()),
                                                              spec.repository!!,
                                                              description = PyBundle.message("python.packages.no.details.in.repo", repositoryName))
      else EmptyPythonPackageDetails(spec.name, PyBundle.message("python.packages.no.details.in.repo", repositoryName))
    }

    try {
      val packageDetails = gson.fromJson(rawInfo, PyPIPackageUtil.PackageDetails::class.java)
      return PythonSimplePackageDetails(spec.name,
                                        packageDetails.releases.sortedWith(PyPackageVersionComparator.STR_COMPARATOR.reversed()),
                                        spec.repository!!,
                                        packageDetails.info.summary,
                                        packageDetails.info.description,
                                        packageDetails.info.descriptionContentType,
                                        packageDetails.info.projectUrls["Documentation"])

    }
    catch (ex: Exception) {
      thisLogger().error(ex)
      return EmptyPythonPackageDetails(spec.name, PyBundle.message("python.packaging.could.not.parse.response", spec.name, spec.repository?.name))
    }
  }

  private fun tryParsingVersionsFromPage(name: String, repositoryUrl: String?): List<String>? {
    val actualUrl = repositoryUrl ?: PyPIPackageRepository.repositoryUrl!!
    val versions = runCatching {
      val url = StringUtil.trimEnd(actualUrl, "/") + "/" + name
      PyPIPackageUtil.parsePackageVersionsFromArchives(url, name)
    }
    return versions.getOrNull()
  }


  override suspend fun initCaches() {
    service<PypiPackageCache>().apply {
      if (isEmpty()) loadCache()
    }

    val repositoryService = service<PyPackageRepositories>()
    val repositoryCache = service<PythonSimpleRepositoryCache>()
    if (repositoryService.repositories.isNotEmpty() && repositoryCache.isEmpty()) {
      repositoryCache.refresh()
    }
  }

  override suspend fun refreshCashes() {
    service<PypiPackageCache>().refresh()
    service<PythonSimpleRepositoryCache>().refresh()
  }

  override fun allPackages(): List<String> {
    // todo[akniazev] check if it is even needed
    return packagesByRepository().flatMap { it.second }.distinct().toList()
  }

  override fun packagesFromRepository(repository: PyPackageRepository): List<String> {
    return if (repository is PyPIPackageRepository) service<PypiPackageCache>().packages
    else service<PythonSimpleRepositoryCache>()[repository] ?: error("No packages for requested repository in cache")
  }

  override suspend fun getPackageDetails(pkg: PythonPackageSpecification): PythonPackageDetails {
    return packageDetailsCache[pkg]
  }
}