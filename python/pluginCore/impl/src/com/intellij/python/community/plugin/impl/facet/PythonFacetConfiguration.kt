// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.impl.facet

import com.intellij.facet.FacetConfiguration
import com.intellij.facet.ui.FacetEditorContext
import com.intellij.facet.ui.FacetEditorTab
import com.intellij.facet.ui.FacetValidatorsManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled
import com.jetbrains.python.facet.PythonFacetSettings
import com.jetbrains.python.sdk.PythonSdkType
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus

/**
 * The configuration of the Python facet: the name of its SDK, which only the legacy flow reads. The interpreter library
 * flow migrates the name to the interpreter library entry of the module, see `migrateSdkReferences`, and has the
 * interpreter tab, see [PythonInterpreterEditorTab].
 */
@ApiStatus.Internal
class PythonFacetConfiguration : PythonFacetSettings(), FacetConfiguration {
  override fun createEditorTabs(editorContext: FacetEditorContext, validatorsManager: FacetValidatorsManager): Array<FacetEditorTab> =
    if (isInterpreterLibraryFlowEnabled()) arrayOf(PythonInterpreterEditorTab(editorContext)) else emptyArray()

  override fun readExternal(element: Element) {
    val sdkName = element.getAttributeValue(SDK_NAME)
    mySdk = if (sdkName.isNullOrEmpty()) null else ProjectJdkTable.getInstance().findJdk(sdkName, PythonSdkType.getInstance().name)
  }

  override fun writeExternal(element: Element) {
    element.setAttribute(SDK_NAME, mySdk?.name ?: "")
  }

  private companion object {
    const val SDK_NAME = "sdkName"
  }
}
