// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.exists
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.cache.PythonPackageCache
import com.jetbrains.python.packaging.common.PythonRankingAwarePackageNameComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.io.path.deleteIfExists

@ApiStatus.Experimental
@Service
class PypiPackageCache : PythonPackageCache<String> {
  private val gson: Gson = Gson()
  private val mutex = Mutex()

  override val packages: List<String>
    get() = cache.toList()

  @Volatile
  private var cache: TreeSet<String> = TreeSet(PythonRankingAwarePackageNameComparator())

  val filePath: Path
    get() = Paths.get(PathManager.getSystemPath(), "python_packages", "packages_v2.json")

  suspend fun loadFromFile() {
    withContext(Dispatchers.IO) {
      val type = object : TypeToken<List<String>>() {}.type
      val packageList = mutex.withLock {
        Files.newBufferedReader(filePath, StandardCharsets.UTF_8)
          .use { gson.fromJson<List<String>>(it, type) }
      }
      thisLogger().info("Package list loaded from file with ${packageList.size} entries")
      val newCache = TreeSet(PythonRankingAwarePackageNameComparator())
      newCache.addAll(packageList)
      cache = newCache
    }
  }

  private suspend fun store() {
    mutex.withLock {
      filePath.deleteIfExists()
      Files.createDirectories(filePath.parent)
      Files.newBufferedWriter(filePath, StandardCharsets.UTF_8).use { writer ->
        gson.toJson(cache.toList(), writer)
      }
    }
  }

  internal suspend fun loadCache() {
    withContext(Dispatchers.IO) {
      val log = thisLogger()
      log.info("Updating PyPI packages cache")
      if (filePath.exists()) {
        val fileTime = Files.getLastModifiedTime(filePath)
        if (fileTime.toInstant().plus(Duration.ofDays(1)).isAfter(Instant.now())) {
          log.info("Cache file is not expired, reading packages locally")
          try {
            loadFromFile()
          }
          catch (ex: JsonSyntaxException) {
            log.info("Corrupted cache file, will reload packages from web")
            refresh()
          }
          return@withContext
        }
        log.info("Cache expired, rebuilding it")
        refresh()
        return@withContext
      }
      log.info("Cache file does not exist, reading packages from PyPI")
      refresh()
    }
  }

  internal suspend fun refresh() {
    withContext(Dispatchers.IO) {
      thisLogger().info("Loading python packages from PyPi")
      val pypiList = PyPIPackageUtil.parsePyPIListFromWeb(PyPIPackageUtil.PYPI_LIST_URL)
      val newCache = TreeSet(PythonRankingAwarePackageNameComparator())
      newCache.addAll(pypiList)

      cache = newCache
      store()
    }
  }

  override operator fun contains(key: String): Boolean = key in cache

  override fun isEmpty(): Boolean = cache.isEmpty()
}