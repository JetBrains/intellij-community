// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.google.common.io.Resources
import com.google.gson.Gson
import com.intellij.openapi.components.Service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service
@ApiStatus.Internal
class PyPIPackageRanking {
  private val lock = ReentrantReadWriteLock()
  private var myPackageRank: Map<String, Int> = emptyMap()
    get() = lock.read { field }
    set(value) {
      lock.write { field = value }
    }

  val packageRank: Map<String, Int>
    get() = myPackageRank
  val names: Sequence<String>
    get() = myPackageRank.asSequence().map { it.key }

  suspend fun reload() {
    withContext(Dispatchers.IO) {
      val gson = Gson()
      val resource = PyPIPackageRanking::class.java.getResource("/packaging/pypi-ranking.json") ?: error("Python package ranking not found")
      val array = Resources.asCharSource(resource, Charsets.UTF_8).openBufferedStream().use {
        gson.fromJson(it, Array<Array<String>>::class.java)
      }
      val newRanked = array.asSequence()
        .map { Pair(it[0].lowercase(), it[1].toInt()) }
        .toMap(LinkedHashMap())
      withContext(Dispatchers.Main) {
        myPackageRank = Collections.unmodifiableMap(newRanked)
      }
    }
  }
}