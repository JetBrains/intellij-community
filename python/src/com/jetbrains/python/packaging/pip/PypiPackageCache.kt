// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.InstanceCreator
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.exists
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.cache.PythonPackageCache
import com.jetbrains.python.packaging.common.RANKING_AWARE_PACKAGE_NAME_COMPARATOR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.*

@ApiStatus.Experimental
object PypiPackageCache : PythonPackageCache<String> {
  private val gson: Gson = GsonBuilder()
    .registerTypeAdapter(object : TypeToken<TreeSet<String>>() {}.type,
                         object : InstanceCreator<TreeSet<String>> {
                           override fun createInstance(type: Type?): TreeSet<String> {
                             return TreeSet<String>(RANKING_AWARE_PACKAGE_NAME_COMPARATOR)
                           }
                         })
    .create()

  override val packages: List<String>
    get() = cache.toList()

  private var cache: TreeSet<String> = TreeSet(RANKING_AWARE_PACKAGE_NAME_COMPARATOR)

  val filePath: Path
    get() = Paths.get(PathManager.getSystemPath(), "python_packages", "packages_v2.json")

  suspend fun loadFromFile() {
    withContext(Dispatchers.IO) {
      val type = object : TypeToken<TreeSet<String>>() {}.type
      val newCache = Files.newBufferedReader(filePath, StandardCharsets.UTF_8).use { reader ->
        val newCache: TreeSet<String> = gson.fromJson(reader, type)
        newCache
      }
      withContext(Dispatchers.Main) {
        cache = newCache
      }
    }
  }

  suspend fun store() {
    withContext(Dispatchers.IO) {
      Files.createDirectories(filePath.parent)
      Files.newBufferedWriter(filePath, StandardCharsets.UTF_8).use { writer ->
        gson.toJson(cache, writer)
      }
    }
  }

  internal suspend fun loadCache() {
    withContext(Dispatchers.IO) {
      thisLogger().debug("Updating PyPI packages cache")
      if (filePath.exists()) {
        val fileTime = Files.getLastModifiedTime(filePath)
        if (fileTime.toInstant().plus(Duration.ofDays(1)).isAfter(Instant.now())) {
          thisLogger().debug("Cache file is not expired, reading packages locally")
          loadFromFile()
          return@withContext
        }
        thisLogger().debug("Cache expired, rebuilding it")
        refresh()
        return@withContext
      }
      thisLogger().debug("Cache file does not exist, reading packages from PyPI")
      refresh()
    }
  }

  suspend fun refresh() {
    cache.clear()
    withContext(Dispatchers.IO) {
      cache.addAll(PyPIPackageUtil.parsePyPIListFromWeb(PyPIPackageUtil.PYPI_LIST_URL))
      store()
    }
  }

  override operator fun contains(key: String): Boolean = key in cache

  override fun isEmpty(): Boolean = cache.isEmpty()
}