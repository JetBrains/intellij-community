// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager.Companion.PACKAGE_MANAGEMENT_TOPIC
import java.time.Duration

@Service
class PyStubPackagesAdvertiserCache {

  private val cache: LoadingCache<Sdk, Cache<String, StubPackagesForSource>> = CacheBuilder.newBuilder()
    .maximumSize(3)
    .expireAfterAccess(Duration.ofMinutes(10))
    .build(
      CacheLoader.from { _ ->
        CacheBuilder.newBuilder()
          .maximumSize(50)
          .expireAfterAccess(Duration.ofMinutes(5))
          .build()
      }
    )

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
      override fun packagesChanged(sdk: Sdk) {
        cache.invalidate(sdk)
      }
    })
  }

  fun forSdk(sdk: Sdk): Cache<String, StubPackagesForSource> {
    return cache.get(sdk)
  }

  companion object {
    class StubPackagesForSource private constructor(val packages: Map<String, Pair<String, List<String>>>) { // name to (version and extra args)
      companion object {
        val EMPTY = StubPackagesForSource(emptyMap())

        fun create(requirements: Map<String, Pair<String, List<String>>>): StubPackagesForSource {
          return if (requirements.isEmpty()) EMPTY else StubPackagesForSource(requirements)
        }
      }
    }
  }
}
