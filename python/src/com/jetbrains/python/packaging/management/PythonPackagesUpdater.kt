// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.pip.PypiPackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class PythonPackagesUpdater : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode || Registry.`is`("disable.python.cache.update")) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    withContext(Dispatchers.IO) {
      thisLogger().debug("Updating PyPI cache and ranking")
      when (val r = serviceAsync<PypiPackageCache>().reloadCache()) {
        is Result.Success -> Unit
        is Result.Failure -> {
          // TODO: Implement background UI notification
          fileLogger().warn("Failed to update packages in background, check your Internet connection", r.error)
        }
      }
    }
  }
}