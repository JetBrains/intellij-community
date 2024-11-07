// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.packaging.PyPIPackageRanking
import com.jetbrains.python.packaging.pip.PypiPackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PythonPackagesUpdater : ProjectActivity {

  override suspend fun execute(project: Project) {
    if (ApplicationManager.getApplication().isUnitTestMode || Registry.`is`("disable.python.cache.update")) return
    withContext(Dispatchers.IO) {
      thisLogger().debug("Updating PyPI cache and ranking")
      service<PyPIPackageRanking>().reload()
      service<PypiPackageCache>().loadCache()
    }
  }
}