// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.impl.facet

import com.intellij.facet.Facet
import com.intellij.facet.FacetType
import com.intellij.facet.FacetTypeId
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled
import com.jetbrains.python.PyNames
import com.jetbrains.python.facet.LibraryContributingFacet
import org.jetbrains.annotations.ApiStatus

/**
 * The Python facet, see [PythonFacetType].
 *
 * Only the legacy flow keeps an SDK in it. There the facet keeps the "interpreter library" of the SDK, see
 * [PythonFacetUtil.updateLibrary], and gives a module without a JDK the SDK as its module SDK, so that a reader of the
 * module SDK sees it. In the interpreter library flow the facet keeps nothing.
 */
@ApiStatus.Internal
class PythonFacet internal constructor(
  facetType: FacetType<*, *>,
  module: Module,
  name: String,
  configuration: PythonFacetConfiguration,
  underlyingFacet: Facet<*>?,
) : LibraryContributingFacet<PythonFacetConfiguration>(facetType, module, name, configuration, underlyingFacet) {
  override fun updateLibrary() {
    if (isInterpreterLibraryFlowEnabled()) return
    val sdk = configuration.sdk
    ApplicationManager.getApplication().runWriteAction {
      val model = ModuleRootManager.getInstance(module).modifiableModel
      val moduleSdk = model.sdk
      // A Java module keeps its JDK. A module of another IDE has no SDK or a Python one.
      if (moduleSdk != sdk && (moduleSdk == null || moduleSdk.sdkType.name == PyNames.PYTHON_SDK_ID_NAME)) {
        model.sdk = sdk
        model.commit()
      }
      else {
        model.dispose()
      }
    }
    PythonFacetUtil.updateLibrary(module, configuration)
  }

  override fun removeLibrary() {
    if (isInterpreterLibraryFlowEnabled()) return
    PythonFacetUtil.removeLibrary(module)
  }

  override fun initFacet() {
    updateLibrary()
  }

  companion object {
    @JvmField
    val ID: FacetTypeId<PythonFacet> = FacetTypeId("python")
  }
}
