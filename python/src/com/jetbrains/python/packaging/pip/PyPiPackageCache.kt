// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.cache.MappedPackedAsciiStringSetCacheFile
import com.jetbrains.python.packaging.cache.PythonPackageCache
import com.jetbrains.python.packaging.cache.PythonPackageCacheIOError
import com.jetbrains.python.packaging.cache.PythonPackageCacheIOError.FailedToFetchPackages
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.conda.PackageVersionMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.measureTimedValue

@Service
@ApiStatus.Internal
class PyPiPackageCache : PythonPackageCache {
  val filePath: Path = getCachePath()
  private var containsCache = Caffeine.newBuilder().maximumSize(CONTAINS_CACHE_SIZE).build<String, Boolean>()
  private var searchCache = Caffeine.newBuilder().maximumSize(SEARCH_CACHE_SIZE).build<String, PythonPackageSearchResult>()
  private val packageCache =
    MappedPackedAsciiStringSetCacheFile(filePath) {
      val timedValue = measureTimedValue {
        containsCache.invalidateAll()
        searchCache.invalidateAll()
        service<PyPiPackageLoader>().loadPackages()
      }
      
      logger.info("PyPI package cache refetched in ${timedValue.duration}")
      
      timedValue.value
    }

  override val size: Int
    get() =
      packageCache.size

  override suspend fun contains(name: String): Boolean =
    withContext(Dispatchers.IO) {
      containsCache.get(name) {
        name in packageCache
      }
    }
  
  fun tryContains(name: String): Boolean? {
    containsCache.getIfPresent(name)?.also { 
      return it
    }
    
    return packageCache.tryContains(name)?.also { 
      containsCache.put(name, it)
    }
  }

  override suspend fun search(prefix: String, pageSize: Int): PythonPackageSearchResult =
    withContext(Dispatchers.IO) {
      val lowercasePrefix = prefix.lowercase()

      searchCache.get(lowercasePrefix) {
        packageCache.search(lowercasePrefix, pageSize)
      }
    }

  @CheckReturnValue
  override suspend fun reload(): Result<Unit, PythonPackageCacheIOError> =
    packageCache.reload()

  @CheckReturnValue
  override suspend fun fullRefetchAndReload(): Result<Unit, PythonPackageCacheIOError> =
    packageCache.reload(forceRefetch = true)

  private fun getCachePath(): Path {
    val overridePath = System.getProperty(PACKAGE_INDEX_CACHE_PROPERTY)

    if (overridePath != null) {
      logger.debug { "Using package index cache path from property: $overridePath" }
      return Paths.get(overridePath)
    }

    val path = Paths.get(PathManager.getSystemPath(), CACHE_FOLDER, DEFAULT_CACHE_FILE_NAME)
    logger.debug { "Using package index cache path: $path" }
    return path
  }

  @ApiStatus.Internal
  @Service
  class PyPiPackageLoader {
    @RequiresBackgroundThread
    @CheckReturnValue
    fun loadPackages(): Result<PackageVersionMap, FailedToFetchPackages> =
      try {
        val pypiPackages = loadPackagesFromPypi().map { PyPackageName.normalizePackageName(it) }.associateWith { emptySet<String>() }
        Result.success(pypiPackages)
      }
      catch (e: IOException) {
        Result.failure(FailedToFetchPackages(e.message ?: "IOException"))
      }
      catch (t: Throwable) {
        logger.warn("Cannot fetch PyPI packages from the internet", t)
        throw t
      }

    /**
     * In some tests we have a problem that pypi return 0 packages without any error.
     * So we need to retry this operation.
     */
    private fun loadPackagesFromPypi(): List<String> {
      var error: Throwable? = null
      val maxAttempts = 3

      repeat(maxAttempts) {
        logger.debug { "PyPI packages fetch attempt ${it + 1}" }

        val fetched =
          try {
            PyPIPackageUtil.parsePyPIListFromWeb(PyPIPackageUtil.PYPI_LIST_URL)
          }
          catch (t: IOException) {
            logger.warn("Failed to fetch PyPI packages on attempt ${it + 1}", t)
            error = t
            return@repeat
          }

        logger.debug { "Fetched PyPI packages on attempt ${it + 1}" }

        if (fetched.size > 2) {
          return fetched
        }

        logger.debug { "Fetched PyPI packages list on attempt ${it + 1} is too small. Fetched ${fetched}" }
      }

      if (error != null) {
        throw error
      }

      logger.warn("Empty PyPI packages list returned after $maxAttempts attempts")

      return emptyList()
    }

    companion object {
      private val logger = logger<PyPiPackageLoader>()
    }
  }

  companion object {
    const val PACKAGE_INDEX_CACHE_PROPERTY: String = "python.packages.cache.index.path"
    private const val CACHE_FOLDER = "python_packages"
    private const val DEFAULT_CACHE_FILE_NAME = "packages_v3.txt"
    private const val CONTAINS_CACHE_SIZE = 512L
    private const val SEARCH_CACHE_SIZE = 128L
    private val logger = logger<PyPiPackageCache>()
  }
}
