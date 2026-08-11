// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.module

//import com.intellij.platform.eel.provider.LocalEelMachine
//import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.util.Consumer
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.PyNames
import com.jetbrains.python.facet.PythonFacetSettings
import com.jetbrains.python.sdk.internal.PYTHON_MODULE_ID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class PyModuleServiceImpl(val project: Project, coroutineScope: CoroutineScope) : PyModuleService {
  private val isJpsProjectLoaded = CompletableDeferred<Unit>().also { deferred ->
    coroutineScope.launch {
      //GlobalWorkspaceModel.getInstance(LocalEelMachine).awaitSynchronizationWithJpsModel()
      deferred.complete(Unit)
    }
  }


  @PyInternalExecApi
  @RequiresWriteLock
  override fun setPythonSdk(module: Module, sdk: Sdk?) {
    val facetSdkSetter = if (!module.isPyModuleType) {
      ApplicationManager.getApplication().serviceOrNull<PySdkToFacetSetter>().also {
        if (it == null) {
          fileLogger().warn("Module $module is not python, but no facet setter registered, setting sdk to module directly")
        }
      }
    }
    else {
      null
    }

    if (facetSdkSetter != null) {
      facetSdkSetter.setPythonSdkToFacet(module, sdk)
    }
    else {
      // For python modules we set SDK directly
      ModuleRootModificationUtil.setModuleSdk(module, sdk)
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
    return module.isPyModuleType ||
           FacetManager.getInstance(module).allFacets.any { it.configuration is PythonFacetSettings }
  }

  private companion object {
    val Module.isPyModuleType: Boolean get() = ModuleType.get(this).id == PYTHON_MODULE_ID
  }
}
