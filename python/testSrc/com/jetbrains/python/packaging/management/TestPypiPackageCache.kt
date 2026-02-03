// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.jetbrains.python.Result
import com.jetbrains.python.packaging.pip.PypiPackageCache
import java.io.IOException

class TestPypiPackageCache : PypiPackageCache() {
  var testPackages: Set<String> = emptySet()
  override val packages: Set<String>
    get() {
      return testPackages
    }

  override suspend fun reloadCache(force: Boolean): Result<Unit, IOException> {
    return Result.success(Unit)
  }
}