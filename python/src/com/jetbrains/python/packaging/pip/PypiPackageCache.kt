// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.SafeFileOutputStream
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.cache.PythonPackageCache
import com.jetbrains.python.packaging.common.PythonRankingAwarePackageNameComparator
import com.jetbrains.python.packaging.normalizePackageName
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
import java.util.*
import kotlin.io.path.exists

private val LOG = logger<PypiPackageCache>()
private val ALPHABET_REGEX = Regex("[-a-z0-9]+")

@ApiStatus.Internal
@Service
class PypiPackageCache : PythonPackageCache<String> {
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

  val filePath: Path = Paths.get(PathManager.getSystemPath(), "python_packages", "packages_v2.json")

  @CheckReturnValue
  suspend fun reloadCache(force: Boolean = false): Result<Unit, IOException> {
    lock.withLock {
      if ((cache.isNotEmpty() && !force) || loadInProgress) {
        return Result.success(Unit)
      }

      loadInProgress = true
    }

    try {
      withContext(Dispatchers.IO) {
        if (!tryLoadFromFile()) {
          return@withContext refresh()
        }
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
        return@withContext false
      }

      var packageList = emptySet<String>()
      try {
        val type = object : TypeToken<LinkedHashSet<String>>() {}.type
        packageList = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)
          .use<BufferedReader, LinkedHashSet<String>> {
            gson.fromJson(it, type)
          }
      }
      catch (e: JsonSyntaxException) {
        LOG.warn("Corrupted pypi cache file: $e")
        return@withContext false
      }

      LOG.info("Package list loaded from file with ${packageList.size} entries")
      cache = packageList.map { normalizePackageName(it) }.toSet()
      true
    }
  }

  @CheckReturnValue
  private suspend fun refresh(): Result<Unit, IOException> {
    withContext(Dispatchers.IO) {
      LOG.info("Loading python packages from PyPi")
      val pypiList = service<PypiPackageLoader>().loadPackages().getOr { return@withContext it }
      val newCache = TreeSet(PythonRankingAwarePackageNameComparator())
      newCache.addAll(pypiList)

      cache = newCache
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
      val pypiPackages = PyPIPackageUtil.parsePyPIListFromWeb(PyPIPackageUtil.PYPI_LIST_URL)
        .map { normalizePackageName(it) }.toSet()
      Result.success(pypiPackages)
    }
    catch (e: IOException) {
      Result.failure(e)
    }
  }
}