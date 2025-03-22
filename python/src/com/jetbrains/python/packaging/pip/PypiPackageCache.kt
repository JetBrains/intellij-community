// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.SafeFileOutputStream
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.cache.PythonPackageCache
import com.jetbrains.python.packaging.common.PythonRankingAwarePackageNameComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.BufferedReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.io.path.exists

private val LOG = logger<PypiPackageCache>()

@ApiStatus.Internal
@Service
class PypiPackageCache : PythonPackageCache<String> {
  override val packages: Set<String>
    get() = cache

  override operator fun contains(key: String): Boolean = key in cache

  override fun isEmpty(): Boolean = cache.isEmpty()

  @Volatile
  private var cache: Set<String> = emptySet()

  private val lock = Mutex()
  private var loadInProgress: Boolean = false

  private val gson: Gson = Gson()

  val filePath: Path = Paths.get(PathManager.getSystemPath(), "python_packages", "packages_v2.json")

  suspend fun forceReloadCache() {
    return reloadCache(true)
  }

  suspend fun reloadCache(force: Boolean = false) {
    lock.withLock {
      if ((cache.isNotEmpty() && !force) || loadInProgress) {
        return
      }

      loadInProgress = true
    }

    try {
      withContext(Dispatchers.IO) {
        if (!tryLoadFromFile()) {
          refresh()
        }
      }
    }
    finally {
      lock.withLock {
        loadInProgress = false
      }
    }
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
      cache = packageList
      true
    }
  }

  private suspend fun refresh() {
    withContext(Dispatchers.IO) {
      LOG.info("Loading python packages from PyPi")
      val pypiList = service<PypiPackageLoader>().loadPackages()
      val newCache = TreeSet(PythonRankingAwarePackageNameComparator())
      newCache.addAll(pypiList)

      cache = newCache
      store()
    }
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

  @Service
  class PypiPackageLoader {
    fun loadPackages(): Collection<String> = PyPIPackageUtil.parsePyPIListFromWeb(PyPIPackageUtil.PYPI_LIST_URL)
  }
}