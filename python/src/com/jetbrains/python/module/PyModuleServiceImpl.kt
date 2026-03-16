// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.module

import com.intellij.facet.FacetManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.util.Consumer
import com.intellij.workspaceModel.ide.JpsProjectLoadingManager
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.facet.PythonFacetSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

internal class PyModuleServiceImpl(val project: Project, private val coroutineScope: CoroutineScope) : PyModuleService {
  // Lazy to avoid calling JpsProjectLoadingManager.jpsProjectLoaded during service construction.
  // jpsProjectLoaded acquires a @Synchronized lock on JpsProjectLoadingManagerImpl, which may
  // already be held by a worker thread running JpsProjectLoadedListenerImpl.loaded() (triggered
  // by ModuleBridgeLoaderService after modules finish loading). If the service is instantiated
  // on EDT, EDT blocks on that lock.
  private val isJpsProjectLoaded by lazy {
    CompletableDeferred<Unit>().also { deferred ->
      JpsProjectLoadingManager.getInstance(project).jpsProjectLoaded { deferred.complete(Unit) }

      // Safety net: if the callback is never invoked (e.g. due to a platform bug), unblock waiters after a timeout.
      coroutineScope.launch {
        // JpsGlobalModelSynchronizerImpl has a 5-second delay and ModuleManagerComponentBridgeInitializer has a 1-second delay;
        delay(10.seconds)
        if (deferred.complete(Unit)) {
          thisLogger().error("JPS project loaded callback was not called within 10 seconds, proceeding anyway")
        }
      }
    }
  }

  override suspend fun findPythonSdkWaitingForProjectModel(module: Module): Sdk? {
    isJpsProjectLoaded.await()
    return findPythonSdk(module)
  }

  override fun findPythonSdk(module: Module): Sdk? {
    val moduleSdk = ModuleRootManager.getInstance(module).sdk
    moduleSdk?.takeIf { (PyNames.PYTHON_SDK_ID_NAME == it.getSdkType().getName()) }?.let { return it }

    for (facet in FacetManager.getInstance(module).allFacets) {
      val configuration = facet.configuration
      if (configuration is PythonFacetSettings) {
        return configuration.sdk
      }
    }

    return null
  }

  override fun forAllFacets(module: Module, facetConsumer: Consumer<Any>) {
    for (facet in FacetManager.getInstance(module).allFacets) {
      facetConsumer.consume(facet)
    }
  }

  override fun isPythonModule(module: Module): Boolean {
    val type = ModuleType.get(module)
    if (type is PythonModuleTypeBase || type.id == PyNames.PYTHON_MODULE_ID) {
      return true
    }
    return FacetManager.getInstance(module).allFacets.any { it.configuration is PythonFacetSettings }
  }
}
