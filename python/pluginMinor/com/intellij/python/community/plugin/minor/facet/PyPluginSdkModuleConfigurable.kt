// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.minor.facet

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.configuration.PyActiveSdkConfigurable
import com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable
import com.intellij.python.community.plugin.impl.facet.PythonFacetUtil
import com.intellij.python.community.plugin.minor.facet.MinorPythonFacet
import com.intellij.python.community.plugin.minor.facet.MinorPythonFacetType
import com.jetbrains.python.sdk.removeTransferredRoots
import com.jetbrains.python.sdk.transferRoots

internal class PyPluginSdkModuleConfigurable(project: Project?) : PyActiveSdkModuleConfigurable(project) {
  override fun createModuleConfigurable(module: Module): UnnamedConfigurable {
    return object : PyActiveSdkConfigurable(module) {
      override fun setSdk(item: Sdk?) {
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

      override fun getSdk(): Sdk? {
        val facetManager = FacetManager.getInstance(module)
        val facet = facetManager.getFacetByType(MinorPythonFacet.ID)
        return facet?.configuration?.sdk
      }
    }
  }

  private fun setFacetSdk(facet: MinorPythonFacet,
                          item: Sdk?,
                          module: Module) {
    removeTransferredRoots(module, facet.configuration.sdk)
    facet.configuration.sdk = item
    transferRoots(module, item)

    FacetManager.getInstance(module).facetConfigurationChanged(facet)
    PythonFacetUtil.updateLibrary(module, facet.configuration)
  }

  private fun addFacet(facetManager: FacetManager,
                       sdk: Sdk?,
                       module: Module) {
    val facet = facetManager.addFacet(
      MinorPythonFacetType.getInstance(), "Python facet", null)
    setFacetSdk(facet, sdk, module)
  }
}