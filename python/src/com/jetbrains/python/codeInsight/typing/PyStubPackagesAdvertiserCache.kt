// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.webcore.packaging.RepoPackage
import com.jetbrains.python.packaging.PyPackageManager
import java.time.Duration

class PyStubPackagesAdvertiserCache {

  private val cache = CacheBuilder.newBuilder()
    .maximumSize(3)
    .expireAfterAccess(Duration.ofMinutes(10))
    .build<Sdk, Cache<String, Set<RepoPackage>>>(
      CacheLoader.from { _ ->
        CacheBuilder.newBuilder()
          .maximumSize(50)
          .expireAfterAccess(Duration.ofMinutes(5))
          .build<String, Set<RepoPackage>>()
      }
    )

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(PyPackageManager.PACKAGE_MANAGER_TOPIC, PyPackageManager.Listener { cache.invalidate(it) })
  }

  fun forSdk(sdk: Sdk): Cache<String, Set<RepoPackage>> {
    return cache.get(sdk)
  }
}