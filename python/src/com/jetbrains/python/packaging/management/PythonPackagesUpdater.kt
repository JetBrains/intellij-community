// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.conda.CondaPackageCache
import com.jetbrains.python.packaging.management.ui.notify
import com.jetbrains.python.packaging.pip.PipRepositoryManager
import com.jetbrains.python.packaging.pip.PyPiPackageCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.hours

internal class PythonPackagesUpdater : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode || Registry.`is`("disable.python.cache.update")) {
      throw ExtensionNotApplicableException.create()
    }

    ApplicationManager.getApplication().service<PackageCacheUpdaterService>()
  }

  override suspend fun execute(project: Project) {
    withContext(Dispatchers.IO) {
      thisLogger().debug("Updating Python package cache and ranking")
      project
        .serviceAsync<PipRepositoryManager>()
        .refreshCaches()
        .onFailure {
          fileLogger().warn("Failed to update Python package cache: ${it.message}")
          it.notify(project)
        }
    }
  }
}

@Service
private class PackageCacheUpdaterService(
  coroutineScope: CoroutineScope
) {
  init {
    coroutineScope.launch { 
      logger.debug { "launched package cache updater service" }
      
      while (true) {
        delay(CHECK_DELAY)
        tryReloadCache()
      }
    }    
  }
  
  private suspend fun tryReloadCache() {
    val application = ApplicationManager.getApplication()
    
    application.service<PyPiPackageCache>().reload().onFailure { 
      logger.warn("failed to reload PyPI cache: ${it.message}")
    }
    application.service<CondaPackageCache>().reload().onFailure {
      logger.warn("failed to reload Conda cache: ${it.message}")
    }
  }
  
  companion object {
    private val CHECK_DELAY = 1.hours
    private val logger = thisLogger()
  }
}