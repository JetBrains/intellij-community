// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.cache.impl.InMemorySearchPage
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.conda.toCondaPackageDetails
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.management.PythonRepositoryManager.PythonRepositoryIOError
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.PythonRepositoryManagerBase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

internal class PixiRepositoryManager(
  override val project: Project,
  private val pythonRepositoryManager: PythonRepositoryManager,
  private val resolveInstalledPackage: suspend (String) -> PyResult<PixiPackage?> = { PyResult.success(null) },
  private val loadNativeDetails: suspend (String) -> PyResult<PythonPackageDetails?> = { PyResult.success(null) },
  private val nativeSearchContext: suspend () -> PyResult<PixiPackageSearchContext?> = { PyResult.success(null) },
  private val nativeNameCacheClock: () -> Long = System::nanoTime,
  private val searchNativePackage: suspend (String) -> PyResult<List<PixiNativePackage>>,
) : PythonRepositoryManagerBase() {
  private val nativeNamesMutex = Mutex()
  private var nativeNamesSnapshot: NativeNamesSnapshot? = null
  val nativeRepository = PixiNativeRepository(this)
  override val builtInRepositories: List<PyPackageRepository>
    get() = listOf(nativeRepository) + pythonRepositoryManager.builtInRepositories
  override val allRepositories: List<PyPackageRepository>
    get() = listOf(nativeRepository) + pythonRepositoryManager.allRepositories

  init {
    if (!shouldBeInitInstantly()) initializationJob.start()
  }

  override suspend fun getPackageDetails(packageName: String, repository: PyPackageRepository?): PyResult<PythonPackageDetails> =
    packageDetails(packageName, repository, includeMetadata = true)

  private suspend fun packageDetails(
    packageName: String, repository: PyPackageRepository?, includeMetadata: Boolean,
  ): PyResult<PythonPackageDetails> {
    val installed = if (repository == null || repository.isPixiNativeRepository()) {
      resolveInstalledPackage(packageName).getOr { return it }
    }
    else null
    val selectedRepository = repository ?: nativeRepository.takeIf { installed?.kind == "conda" }
    if (!selectedRepository.isPixiNativeRepository()) return pythonRepositoryManager.getPackageDetails(packageName, selectedRepository)
    val rawName = installed?.takeIf { it.kind == "conda" }?.name ?: packageName
    val packages = searchNativePackage(rawName).getOr { return it }.filter { it.name == rawName }
    if (packages.isEmpty()) {
      return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.search.empty", packageName))
    }
    val versions = packages.map { it.version }.distinct().filter(::isPixiNativeVersionSelectable)
      .sortedWith(PIXI_CONDA_VERSION_COMPARATOR.reversed())
    val metadata = if (includeMetadata) loadNativeDetails(rawName).getOrNull() else null
    val details = metadata.toCondaPackageDetails(packages.first().name, versions)
    return PyResult.success(object : PythonPackageDetails by details {
      override val repository: PyPackageRepository = nativeRepository
      override suspend fun toPackageSpecification(version: String?): PythonRepositoryPackageSpecification =
        PythonRepositoryPackageSpecification(nativeRepository, details.name, version)
    })
  }

  override suspend fun getVersions(packageName: String, repository: PyPackageRepository?): List<String>? {
    if (repository != null && !repository.isPixiNativeRepository()) {
      return pythonRepositoryManager.getVersions(packageName, repository)
    }
    if (repository == null) {
      val installed = resolveInstalledPackage(packageName).getOr { return null }
      if (installed?.kind != "conda") return pythonRepositoryManager.getVersions(packageName, null)
    }
    return packageDetails(packageName, repository, includeMetadata = false).getOrNull()?.availableVersions
  }

  override suspend fun searchPackages(repository: PyPackageRepository, needle: String, pageSize: Int): PythonPackageSearchResult {
    if (!repository.isPixiNativeRepository()) return pythonRepositoryManager.searchPackages(repository, needle, pageSize)
    val names = nativeNamesMutex.withLock {
      val context = nativeSearchContext()
        .onFailure { thisLogger().warn("Failed to resolve Pixi channels: $it") }.getOr {
          nativeNamesSnapshot = null
          nativeRepository.packageCount = 0
          return@withLock emptyList()
        }
      val snapshot = nativeNamesSnapshot
      if (snapshot != null && snapshot.context == context &&
          nativeNameCacheClock() - snapshot.createdAt < NATIVE_NAMES_TTL.inWholeNanoseconds) {
        return@withLock if (needle.isBlank()) snapshot.names else snapshot.names.filter { it.contains(needle, ignoreCase = true) }
      }
      nativeNamesSnapshot = null
      nativeRepository.packageCount = 0
      val packages = searchNativePackage(if (needle.isBlank()) "*" else "*${needle.lowercase()}*")
        .onFailure { thisLogger().warn("Failed to search Pixi channels: $it") }.getOr { return@withLock emptyList() }
      val names = packages.map { it.name }.distinct().sorted()
      if (needle.isBlank()) {
        nativeNamesSnapshot = NativeNamesSnapshot(context, names, nativeNameCacheClock())
        nativeRepository.packageCount = names.size
      }
      names
    }
    return InMemorySearchPage.resultFromMatches(names, pageSize)
  }

  override suspend fun findPackageSpecification(
    requirement: PyRequirement,
    repository: PyPackageRepository?,
  ): PythonRepositoryPackageSpecification? {
    val installed = if (repository == null) resolveInstalledPackage(requirement.name).getOr { return null } else null
    val selectedRepository = repository ?: when (installed?.kind) {
      "conda" -> nativeRepository
      else -> null
    }
    if (!selectedRepository.isPixiNativeRepository()) awaitReady()
    if (selectedRepository != null && !selectedRepository.isPixiNativeRepository()) {
      return pythonRepositoryManager.findPackageSpecification(requirement, selectedRepository)
    }
    if (selectedRepository == null) {
      pythonRepositoryManager.findPackageSpecification(requirement, null)
        ?.let { return it }
    }
    if (selectedRepository.isPixiNativeRepository() || nativeRepository in repositories) {
      val requestedName = requirement.presentableTextWithoutVersion
      val resolved = installed ?: resolveInstalledPackage(requestedName).getOr { return null }
      if (resolved?.kind == "conda") return PythonRepositoryPackageSpecification(nativeRepository, requirement)
      val query = requestedName + requirement.versionSpecs.joinToString(",") { it.presentableText }
      val packages = searchNativePackage(query).getOrNull().orEmpty()
      if (packages.any { it.name == requestedName }) {
        return PythonRepositoryPackageSpecification(nativeRepository, requirement)
      }
      if (selectedRepository.isPixiNativeRepository()) return null
    }
    return null
  }

  override suspend fun hasPackageSnapshot(packageName: String): Boolean {
    return (nativeRepository in repositories &&
            searchNativePackage(packageName).getOrNull().orEmpty().any { it.name == packageName }) ||
           pythonRepositoryManager.hasPackageSnapshot(packageName)
  }

  override suspend fun initCaches(): Result<Unit, PythonRepositoryIOError> =
    pythonRepositoryManager.initCaches()

  override suspend fun refreshCaches(): Result<Unit, PythonRepositoryIOError> {
    nativeNamesMutex.withLock {
      nativeNamesSnapshot = null
      nativeRepository.packageCount = 0
    }
    return pythonRepositoryManager.refreshCaches()
  }

  private data class NativeNamesSnapshot(val context: PixiPackageSearchContext?, val names: List<String>, val createdAt: Long)
}

private val NATIVE_NAMES_TTL = 5.minutes
private val SEARCH_JSON = Json { ignoreUnknownKeys = true }

internal fun parsePixiNativePackageSearch(output: String): PyResult<List<PixiNativePackage>> = try {
  PyResult.success(SEARCH_JSON.decodeFromString<Map<String, List<PixiNativePackage>>>(output).values.flatten())
}
catch (_: SerializationException) {
  PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.search.invalid.output"))
}

@Serializable
internal data class PixiNativePackage(val name: String, val version: String)
