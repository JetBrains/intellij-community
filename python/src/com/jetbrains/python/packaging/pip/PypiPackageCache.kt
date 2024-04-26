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

@ApiStatus.Experimental
@Service
class PypiPackageCache : PythonPackageCache<String> {

  private val gson: Gson = Gson()

  override val packages: List<String>
    get() = cache.toList()

  @Volatile
  private var cache: Set<String> = emptySet()

  val filePath: Path
    get() = Paths.get(PathManager.getSystemPath(), "python_packages", "packages_v2.json")

  private suspend fun loadFromFile() {
    withContext(Dispatchers.IO) {
      val type = object : TypeToken<LinkedHashSet<String>>() {}.type
      val packageList = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)
        .use<BufferedReader, LinkedHashSet<String>> { gson.fromJson(it, type) }
      LOG.info("Package list loaded from file with ${packageList.size} entries")
      cache = packageList
    }
  }

  private fun store() {
    Files.createDirectories(filePath.parent)
    SafeFileOutputStream(filePath).writer(StandardCharsets.UTF_8).use { writer ->
      gson.toJson(cache.toList(), writer)
    }
  }

  suspend fun loadCache() {
    if (cache.isNotEmpty()) {
      return
    }
    withContext(Dispatchers.IO) {
      LOG.info("Updating PyPI packages cache")
      if (filePath.exists()) {
        if (!isExpired()) {
          LOG.info("Cache file is not expired, reading packages locally")
          try {
            loadFromFile()
          }
          catch (ex: JsonSyntaxException) {
            LOG.info("Corrupted cache file, will reload packages from web")
            refresh()
          }
          return@withContext
        }
        LOG.info("Cache expired, rebuilding it")
        refresh()
        return@withContext
      }
      LOG.info("Cache file does not exist, reading packages from PyPI")
      refresh()
    }
  }

  internal suspend fun refresh() {
    withContext(Dispatchers.IO) {
      LOG.info("Loading python packages from PyPi")
      val pypiList = service<PypiPackageLoader>().loadPackages()
      val newCache = TreeSet(PythonRankingAwarePackageNameComparator())
      newCache.addAll(pypiList)

      cache = newCache
      store()
    }
  }

  override operator fun contains(key: String): Boolean = key in cache

  override fun isEmpty(): Boolean = cache.isEmpty()

  private fun isExpired(): Boolean {
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