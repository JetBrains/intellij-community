// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.SafeFileOutputStream
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.cache.PythonPackageCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import java.io.BufferedReader
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import kotlin.io.path.exists


private val ALPHABET_REGEX = Regex("[-a-z0-9]+")

@ApiStatus.Internal
open class PypiPackageCache : PythonPackageCache<String> {
  override val packages: Set<String>
    get() = cache

  override operator fun contains(key: String): Boolean = key in cache

  fun lookup(word: String): LookupStatus {
    if (word in cache) return LookupStatus.Present
    if (!ALPHABET_REGEX.matches(word.lowercase())) return LookupStatus.Alien
    return LookupStatus.Absent
  }

  override fun isEmpty(): Boolean = cache.isEmpty()

  @Volatile
  private var cache: Set<String> = emptySet()

  private val lock = Mutex()
  private var loadInProgress: Boolean = false

  private val gson: Gson = Gson()

  val filePath: Path = getCachePath()

  private fun getCachePath(): Path {
    val overridePath = System.getProperty(PACKAGE_INDEX_CACHE_PROPERTY)
    if (overridePath != null) {
      thisLogger().debug("Using package index cache path from property: $overridePath")
      return Paths.get(overridePath)
    }

    val path = Paths.get(PathManager.getSystemPath(), "python_packages", "packages_v2.json")
    thisLogger().debug("Using package index cache path: $path")
    return path
  }

  @CheckReturnValue
  open suspend fun reloadCache(force: Boolean = false): Result<Unit, IOException> {
    lock.withLock {
      if ((cache.isNotEmpty() && !force) || loadInProgress) {
        return Result.success(Unit)
      }

      loadInProgress = true
    }

    try {
      LOG.info("Reloading Pypi package cache")
      withContext(Dispatchers.IO) {
        if (!tryLoadFromFile()) {
          return@withContext refresh()
        }
      }
      if (packages.isEmpty()) {
        LOG.warn("Empty Pypi loaded package cache")
      }
    }
    finally {
      lock.withLock {
        loadInProgress = false
      }
    }
    return Result.success(Unit)
  }

  private suspend fun tryLoadFromFile(): Boolean {
    return withContext(Dispatchers.IO) {
      if (isFileCacheExpired()) {
        thisLogger().debug("Pypi package cache file is expired")
        return@withContext false
      }

      var packageList: Set<String>
      try {
        val type = object : TypeToken<LinkedHashSet<String>>() {}.type
        packageList = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)
          .use<BufferedReader, LinkedHashSet<String>> {
            gson.fromJson(it, type)
          }

        LOG.info("Package list loaded from file ${filePath} with ${Files.size(filePath) / 1024}Kb size with ${packageList.size} entries")

      }
      catch (e: JsonSyntaxException) {
        LOG.warn("Corrupted pypi cache file: $e")
        return@withContext false
      }
      cache = packageList.map { PyPackageName.normalizePackageName(it) }.toSet()
      true
    }
  }

  @CheckReturnValue
  private suspend fun refresh(): Result<Unit, IOException> {
    withContext(Dispatchers.IO) {
      LOG.info("Loading python packages from PyPi Repository")
      val pypiList = service<PypiPackageLoader>().loadPackages().getOr { return@withContext it }
      LOG.info("Loaded ${pypiList.size} python packages from PyPi Repository")
      cache = pypiList.toSet()
      store()
    }
    return Result.success(Unit)
  }

  private fun store() {
    Files.createDirectories(filePath.parent)
    SafeFileOutputStream(filePath).writer(StandardCharsets.UTF_8).use { writer ->
      gson.toJson(cache.toList(), writer)
    }
  }

  private fun isFileCacheExpired(): Boolean {
    if (!filePath.exists()) {
      return true
    }

    val fileTime = Files.getLastModifiedTime(filePath)
    val expirationTime = fileTime.toInstant().plus(Duration.ofDays(1))

    return expirationTime.isBefore(Instant.now())
  }


  @ApiStatus.Internal
  @Service
  class PypiPackageLoader {
    @RequiresBackgroundThread
    fun loadPackages(): Result<Collection<String>, IOException> = try {
      val pypiPackages = loadPackagesFromPypi().map { PyPackageName.normalizePackageName(it) }.toSet()
      Result.success(pypiPackages)
    }
    catch (e: IOException) {
      Result.failure(e)
    }
    catch (t: Throwable) {
      thisLogger().warn("Cannot load pypiList from internet", t)
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
        thisLogger().debug("Attempt ${it + 1} to load Pypi packages list")
        val loaded = try {
          PyPIPackageUtil.parsePyPIListFromWeb(PyPIPackageUtil.PYPI_LIST_URL)
        }
        catch (t: CancellationException) {
          throw t
        }
        catch (t: Throwable) {
          thisLogger().warn("Attempt ${it + 1} Cannot load Pypi packages list", t)
          error = t
          return@repeat
        }
        thisLogger().debug("Attempt ${it + 1} Loaded ${loaded.size} Pypi packages")
        if (loaded.size > 2) {
          return loaded
        }
        thisLogger().debug("Attempt ${it + 1} Return TOO SMALL Pypi packages list. Loaded ${loaded}")
      }
      if (error != null) {
        throw error
      }
      thisLogger().warn("Return empty Pypi packages list after $maxAttempts attempts")
      return emptyList()
    }

  }

  companion object {
    private val LOG = thisLogger()
    const val PACKAGE_INDEX_CACHE_PROPERTY = "python.packages.cache.index.path"
  }
}