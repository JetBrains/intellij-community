// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.jetbrains.python.Result
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.cache.impl.InMemorySearchPage
import com.jetbrains.python.packaging.pip.PyPiPackageCache
import java.io.IOException

class TestPypiPackageCache : PyPiPackageCache() {
  var testPackages: Set<String> = emptySet()

  override fun search(prefix: String, pageSize: Int): PythonPackageSearchResult {
    val list = testPackages.toList()
    return InMemorySearchPage.resultFromMatches(list, list.size)
  }

  override fun contains(name: String): Boolean {
    return name in testPackages
  }

  override suspend fun reloadCache(force: Boolean): Result<Unit, IOException> {
    return Result.success(Unit)
  }
}