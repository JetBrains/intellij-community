// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.gson.Gson
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.HttpRequests
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.PyPackageVersionComparator
import com.jetbrains.python.packaging.PyPackageVersionNormalizer
import com.jetbrains.python.packaging.cache.PythonSimpleRepositoryCache
import com.jetbrains.python.packaging.common.EmptyPythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.common.PythonSimplePackageDetails
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.normalizePackageName
import com.jetbrains.python.packaging.repository.PyEmptyPackagePackageRepository
import com.jetbrains.python.packaging.repository.PyPIPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepositories
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.withBasicAuthorization
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.time.Duration

@ApiStatus.Experimental
internal abstract class PipBasedRepositoryManager() : PythonRepositoryManager {

  override val repositories: List<PyPackageRepository>
    get() = listOf(PyPIPackageRepository) + service<PythonSimpleRepositoryCache>().repositories

  private val gson = Gson()
  private val packageDetailsCache = Caffeine.newBuilder()
    .maximumSize(10)
    .expireAfterWrite(Duration.ofHours(1))
    .build<PythonPackageSpecification, PythonPackageDetails> {
      // todo[akniazev] make it possible to show info from several repos
      val repositoryUrl = it.repository?.repositoryUrl ?: PyPIPackageRepository.repositoryUrl ?: ""
      val result = runCatching {
        val packageDetailsUrl = PyPIPackageUtil.buildDetailsUrl(repositoryUrl, it.name)
        HttpRequests.request(packageDetailsUrl)
          .withBasicAuthorization(it.repository)
          .readTimeout(3000)
          .readString()
      }
      if (result.isFailure) thisLogger().debug("Request failed for package $it.name")

      buildPackageDetails(result.getOrNull(), it)
    }

  private val latestVersions = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofDays(1))
    .build<PythonPackageSpecification, PyPackageVersion?> {
      val details = packageDetailsCache.getIfPresent(it)
      val cachedDetailsVersion = details?.availableVersions?.firstOrNull()
      if (cachedDetailsVersion != null) {
        return@build PyPackageVersionNormalizer.normalize(cachedDetailsVersion)
      }

      val versions = tryParsingVersionsFromPage(it.name, it.repository?.repositoryUrl)
      val latest = versions?.firstOrNull()
      if (latest != null) {
        return@build PyPackageVersionNormalizer.normalize(latest)
      }

      val fromDetails = packageDetailsCache.get(it).availableVersions.firstOrNull() ?: return@build null
      return@build PyPackageVersionNormalizer.normalize(fromDetails)
    }


  override fun buildPackageDetails(rawInfo: String?, spec: PythonPackageSpecification): PythonPackageDetails {
    if (rawInfo == null) {
      val versions = tryParsingVersionsFromPage(spec.name, spec.repository?.repositoryUrl)
      val repository = if (spec.repository !is PyEmptyPackagePackageRepository) spec.repository else PyPIPackageRepository
      val repositoryName = repository?.name ?: PyPIPackageRepository.name
      if (versions != null) {
        return PythonSimplePackageDetails(
          spec.name,
          versions.sortedWith(PyPackageVersionComparator.STR_COMPARATOR.reversed()),
          spec.repository!!,
          description = PyBundle.message("python.packages.no.details.in.repo", repositoryName))
      }
      else {
        return EmptyPythonPackageDetails(
          spec.name,
          PyBundle.message("python.packages.no.details.in.repo", repositoryName))
      }
    }

    try {
      val packageDetails = gson.fromJson(rawInfo, PyPIPackageUtil.PackageDetails::class.java)
      return PythonSimplePackageDetails(
        spec.name,
        packageDetails.releases.sortedWith(PyPackageVersionComparator.STR_COMPARATOR.reversed()),
        spec.repository!!,
        packageDetails.info.summary,
        packageDetails.info.description,
        packageDetails.info.descriptionContentType,
        packageDetails.info.projectUrls["Documentation"],
        packageDetails.info.author,
        packageDetails.info.authorEmail,
        packageDetails.info.homePage)

    }
    catch (ex: Exception) {
      thisLogger().error(ex)
      return EmptyPythonPackageDetails(spec.name, PyBundle.message("python.packaging.could.not.parse.response", spec.name, spec.repository?.name))
    }
  }

  private fun tryParsingVersionsFromPage(name: String, repositoryUrl: String?): List<String>? {
    val actualRepositoryUrl = repositoryUrl ?: PyPIPackageRepository.repositoryUrl
                              ?: error("Can't resolve repository url for $name")
    val versions = runCatching {
      PyPIPackageUtil.parsePackageVersionsFromRepository(actualRepositoryUrl, name)
    }
    return versions.getOrNull()
  }

  @Throws(IOException::class)
  override suspend fun initCaches() {
    service<PypiPackageCache>().reloadCache().orThrow()

    val repositoryService = service<PyPackageRepositories>()
    val repositoryCache = service<PythonSimpleRepositoryCache>()
    if (repositoryService.repositories.isNotEmpty() && repositoryCache.isEmpty()) {
      repositoryCache.refresh()
    }
  }
  @Throws(IOException::class)
  override suspend fun refreshCaches() {
    service<PypiPackageCache>().reloadCache(force = true).orThrow()
    service<PythonSimpleRepositoryCache>().refresh()
  }

  override fun allPackages(): Set<String> =
    repositories.flatMap { it.getPackages() }.toSet()

  override suspend fun getPackageDetails(pkg: PythonPackageSpecification): PythonPackageDetails {
    return packageDetailsCache[pkg]
  }

  override suspend fun getLatestVersion(spec: PythonPackageSpecification): PyPackageVersion? {
    return latestVersions[spec]
  }

  override fun searchPackages(query: String, repository: PyPackageRepository): List<String> {
    val normalizedQuery = normalizePackageName(query)
    return repository.getPackages().filter { StringUtil.containsIgnoreCase(normalizePackageName(it), normalizedQuery) }
  }

  override fun searchPackages(query: String): Map<PyPackageRepository, List<String>> {
    return repositories.associateWith { searchPackages(query, it) }
  }
}