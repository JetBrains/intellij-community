// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.util.io.exists
import com.jetbrains.extensions.hasPython
import com.jetbrains.python.packaging.PyPIPackageRanking
import com.jetbrains.python.packaging.pip.PypiPackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

class PythonPackagesUpdater : ProjectPostStartupActivity {

  override suspend fun execute(project: Project) {
    if (ApplicationManager.getApplication().isUnitTestMode || !project.hasPython) return
    withContext(Dispatchers.IO) {
      thisLogger().debug("Updating PyPI cache and ranking")
      PyPIPackageRanking.reload()

      if (PypiPackageCache.filePath.exists() && !cacheExpired(PypiPackageCache.filePath)) PypiPackageCache.loadFromFile()
      else PypiPackageCache.refresh()
    }
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  private fun cacheExpired(path: Path): Boolean {
    val fileTime = Files.getLastModifiedTime(path)
    val expirationTime = fileTime.toInstant().plus(Duration.ofDays(1))

    return expirationTime.isBefore(Instant.now())
  }
}