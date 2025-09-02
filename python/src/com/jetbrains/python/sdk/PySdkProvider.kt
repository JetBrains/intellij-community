// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.util.NlsSafe
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * This API is subject to change in version 2020.3, please avoid using it. If you have to, your plugin has to set compatibility to 2020.2.2.
 */
@ApiStatus.Experimental
interface PySdkProvider {
  // SDK
  /**
   * Additional info to be displayed with the SDK's name.
   */
  fun getSdkAdditionalText(sdk: Sdk): String? = null

  fun getSdkIcon(sdk: Sdk): Icon? = null

  /**
   * Try to load additional data for your SDK. Check for attributes, specific to your SDK before loading it. Return null if there is none.
   */
  fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? = null

  // Inspections
  /**
   * Quickfix that makes the existing environment available to the module, or null.
   */
  fun createEnvironmentAssociationFix(
    module: Module,
    sdk: Sdk,
    isPyCharm: Boolean,
    associatedModulePath: @NlsSafe String?,
  ): PyInterpreterInspectionQuickFixData? = null


  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PySdkProvider> = ExtensionPointName.create<PySdkProvider>("Pythonid.pySdkProvider")
  }
}

@ApiStatus.Experimental
data class PyInterpreterInspectionQuickFixData(val quickFix: LocalQuickFix, @InspectionMessage val message: @InspectionMessage String)