// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.minor.facet

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.plugin.impl.facet.PythonFacetUtil
import com.jetbrains.python.configuration.PyActiveSdkConfigurable
import com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable
import com.jetbrains.python.sdk.removeTransferredRoots
import com.jetbrains.python.sdk.transferRoots
import org.jetbrains.annotations.ApiStatus

/**
 * The interpreter page of a module of another type, such as a CLion module with the Python facet. It sets the
 * interpreter as for a Python module, through `PyProject.setPythonInterpreter`. That writes the interpreter library
 * entry, and the facet gets the SDK for an older build, see [setSdkToFacet].
 */
internal class PyPluginSdkModuleConfigurable(project: Project?) : PyActiveSdkModuleConfigurable(project) {
  override fun createModuleConfigurable(module: Module): UnnamedConfigurable = object : PyActiveSdkConfigurable(module) {}
}

/**
 * Writes [item] as the SDK of the Python facet of [module], and adds the facet when the module has none. It is how a
 * module of another type stores its SDK for an older build. This build reads the interpreter library entry instead.
 */
@ApiStatus.Internal
fun setSdkToFacet(item: Sdk?, module: Module) {
  setSdkToFacetImpl(item, module)
}

private fun setSdkToFacetImpl(item: Sdk?, module: Module) {
  val facetManager = FacetManager.getInstance(module)
  val facet = facetManager.getFacetByType(MinorPythonFacet.ID)
  if (facet == null) {
    ApplicationManager.getApplication().runWriteAction {
      addFacet(facetManager, item, module)
    }
  }
  else {
    setFacetSdk(facet, item, module)
  }
}

private fun setFacetSdk(
  facet: MinorPythonFacet,
  item: Sdk?,
  module: Module,
) {
  removeTransferredRoots(module, facet.configuration.sdk)
  facet.configuration.sdk = item
  transferRoots(module, item)

  FacetManager.getInstance(module).facetConfigurationChanged(facet)
  PythonFacetUtil.updateLibrary(module, facet.configuration)
}

private fun addFacet(
  facetManager: FacetManager,
  sdk: Sdk?,
  module: Module,
) {
  val facet = facetManager.addFacet(
    MinorPythonFacetType.getInstance(), "Python facet", null)
  setFacetSdk(facet, sdk, module)
}
