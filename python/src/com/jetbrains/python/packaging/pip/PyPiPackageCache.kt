// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.SafeFileOutputStream
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.cache.PythonPackageCache
import com.jetbrains.python.packaging.cache.PythonPackageSearchPage
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.pip.PackedAsciiStringSet.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import java.io.IOException
import java.lang.foreign.Arena
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.exists

private const val CONTAINS_CACHE_SIZE = 512L
private const val SEARCH_CACHE_SIZE = 128L

@Service
@ApiStatus.Internal
class PyPiPackageCache : PythonPackageCache {
  val filePath: Path = getCachePath()

  override val size: Int
    @RequiresBackgroundThread
    get() =
      rwLock.read {
        set.size
      }

  private val rwLock = ReentrantReadWriteLock()
  private var arena = Arena.ofShared()
  private var set = PackedAsciiStringSet.create(ByteBuffer.allocate(0)).orThrow()

  private var containsCache = createContainsCache()
  private var searchCache = createSearchCache()

  private val reloadLock = Mutex()
  private var loadInProgress: Boolean = false

  @RequiresBackgroundThread
  override operator fun contains(name: String): Boolean =
    rwLock.read {
      containsCache.get(name) {
        name in set
      }
    }

  @RequiresBackgroundThread
  override fun search(prefix: String, pageSize: Int): PythonPackageSearchResult =
    rwLock.read {
      val searchResult = searchCache.get(prefix) {
        set.searchByPrefix(prefix.lowercase(), pageSize)
      }

      PythonPackageSearchResult(
        searchResult.total,
        searchResult.pages.map { MemoryMappedSearchPage(it) },
        pageSize,
      )
    }

  @CheckReturnValue
  suspend fun reloadCache(force: Boolean = false): Result<Unit, PyPiPackageCacheError> {
    reloadLock.withLock {
      if ((set.size > 0 && !force) || loadInProgress) {
        return Result.success(Unit)
      }

      loadInProgress = true
    }

    try {
      logger.info("Reloading PyPI package cache")
      withContext(Dispatchers.IO) {
        if (!tryLoadFromFile()) {
          return@withContext refresh()
        }

        return@withContext Result.Success(Unit)
      }.getOr { return it }
      
      if (set.size == 0) {
        logger.warn("Loaded empty PyPI package cache")
      }
    }
    finally {
      reloadLock.withLock {
        loadInProgress = false
      }
    }

    return Result.success(Unit)
  }

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

  private suspend fun tryLoadFromFile(): Boolean {
    return withContext(Dispatchers.IO) {
      if (isFileCacheExpired()) {
        logger.debug { "PyPI package cache file is expired" }
        return@withContext false
      }

      remap().getOr { error ->
        logger.warn("Corrupted PyPI cache file: $error")
        return@withContext false
      }

      logger.info("Package list loaded from file ${filePath} with ${Files.size(filePath) / 1024}Kb size with ${size} entries")

      true
    }
  }

  @CheckReturnValue
  private suspend fun refresh(): Result<Unit, PyPiPackageCacheError> {
    withContext(Dispatchers.IO) {
      logger.info("Fetching python packages from the PyPI repository")
      val pyPiList = service<PyPiPackageLoader>().loadPackages().getOr { return@withContext it }
      logger.info("Fetched ${pyPiList.size} python packages from the PyPI Repository")
      remap(pyPiList)
    }.getOr { return it }

    return Result.success(Unit)
  }

  private fun isFileCacheExpired(): Boolean {
    if (!filePath.exists()) {
      return true
    }

    val fileTime = Files.getLastModifiedTime(filePath)
    val expirationTime = fileTime.toInstant().plus(Duration.ofDays(1))

    return expirationTime.isBefore(Instant.now())
  }

  @RequiresBackgroundThread
  @CheckReturnValue
  private fun remap(storeBeforeLoading: List<String>? = null): Result<Unit, PyPiPackageCacheError> {
    var fileToMap = filePath

    storeBeforeLoading?.also { packages ->
      Files.createDirectories(filePath.parent)
      fileToMap = filePath.parent.resolve(TEMPORARY_CACHE_FILE_NAME)
      SafeFileOutputStream(fileToMap).writer(StandardCharsets.US_ASCII).use { writer ->
        for (pkg in packages) {
          writer.write(pkg)
          writer.write('\n'.code)
        }
      }
    }

    rwLock.write {
      set = PackedAsciiStringSet.create(ByteBuffer.allocate(0)).orThrow()
      arena.close()
      containsCache.invalidateAll()
      searchCache.invalidateAll()

      FileChannel.open(fileToMap, StandardOpenOption.READ).use { channel ->
        arena = Arena.ofShared()
        set = if (Registry.`is`("disable.python.cache.memory.mapping")) {
          val buffer = ByteBuffer.allocate(channel.size().toInt())
          channel.read(buffer)

          PackedAsciiStringSet.create(buffer)
            .getOr { return Result.Failure(PyPiPackageCacheError.InvalidCacheFileFormat(it.error.message)) }
        }
        else {
          PackedAsciiStringSet.create(
            channel
              .map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena)
              .asByteBuffer()
          ).getOr { return Result.Failure(PyPiPackageCacheError.InvalidCacheFileFormat(it.error.message)) }
        }

        containsCache = createContainsCache()
        searchCache = createSearchCache()
      }
    }

    storeBeforeLoading?.also {
      Files.deleteIfExists(filePath)
      Files.move(fileToMap, filePath)
    }

    return Result.Success(Unit)
  }

  /**
   * A search page that gets its data from a memory-mapped buffer.
   */
  @ApiStatus.Internal
  class MemoryMappedSearchPage internal constructor(
    private val searchPageIterable: Iterable<String>,
  ) : PythonPackageSearchPage {
    /**
     * Gets contents of the page as a list. Returns null if the buffer that the page references was unmapped.
     */
    override fun contents(): Result<List<String>, PythonPackageSearchPage.DataInvalidatedError> =
      try {
        Result.Success(searchPageIterable.toList())
      }
      catch (_: IllegalStateException) {
        Result.Failure(PythonPackageSearchPage.DataInvalidatedError)
      }
  }

  @ApiStatus.Internal
  @Service
  class PyPiPackageLoader {
    @RequiresBackgroundThread
    @CheckReturnValue
    fun loadPackages(): Result<List<String>, PyPiPackageCacheError> =
      try {
        val pypiPackages = loadPackagesFromPypi().map { PyPackageName.normalizePackageName(it) }.sorted().toList()
        Result.success(pypiPackages)
      }
      catch (e: IOException) {
        Result.failure(PyPiPackageCacheError.FailedToFetchPackages(e.message ?: "IOException"))
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

  @ApiStatus.Internal
  sealed interface PyPiPackageCacheError {
    val message: String

    @ApiStatus.Internal
    data class FailedToFetchPackages(override val message: String) : PyPiPackageCacheError

    @ApiStatus.Internal
    data class InvalidCacheFileFormat(override val message: String) : PyPiPackageCacheError
  }

  companion object {
    const val PACKAGE_INDEX_CACHE_PROPERTY: String = "python.packages.cache.index.path"
    private const val CACHE_FOLDER = "python_packages"
    private const val TEMPORARY_CACHE_FILE_NAME = "pypi_tmp.txt"
    private const val DEFAULT_CACHE_FILE_NAME = "packages_v3.txt"
    private val logger = logger<PyPiPackageCache>()

    private fun createContainsCache() =
      Caffeine.newBuilder().maximumSize(CONTAINS_CACHE_SIZE).build<String, Boolean>()

    private fun createSearchCache() =
      Caffeine.newBuilder().maximumSize(SEARCH_CACHE_SIZE).build<String, SearchResult>()
  }
}
