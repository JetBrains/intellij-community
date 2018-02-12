/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.minor.sdk

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.configuration.PyActiveSdkConfigurable
import com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable
import com.jetbrains.python.facet.PythonFacetUtil
import com.jetbrains.python.minor.facet.PythonFacet
import com.jetbrains.python.minor.facet.PythonFacetType

/**
 * @author traff
 */


class PyPluginSdkModuleConfigurable(project: Project?) : PyActiveSdkModuleConfigurable(project) {
  override fun createModuleConfigurable(module: Module): UnnamedConfigurable {
    return object : PyActiveSdkConfigurable(module) {
      override fun setSdk(item: Sdk?) {
        val facetManager = FacetManager.getInstance(module)
        val facet = facetManager.getFacetByType(PythonFacet.ID)
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
        val facet = facetManager.getFacetByType(PythonFacet.ID)
        return facet?.configuration?.sdk
      }
    }
  }

  private fun setFacetSdk(facet: PythonFacet,
                          item: Sdk?,
                          module: Module) {
    facet.configuration.sdk = item
    PythonFacetUtil.updateLibrary(module, facet.configuration)
  }

  private fun addFacet(facetManager: FacetManager,
                       sdk: Sdk?,
                       module: Module) {
    val facet = facetManager.addFacet(PythonFacetType.getInstance(), "Python facet", null)
    setFacetSdk(facet, sdk, module)
  }
}