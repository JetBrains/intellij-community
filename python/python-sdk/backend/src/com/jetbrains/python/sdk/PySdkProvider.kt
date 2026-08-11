// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * This API is subject to change in version 2020.3, please avoid using it. If you have to, your plugin has to set compatibility to 2020.2.2.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface PySdkProvider {
  /**
   * Try to load additional data for your SDK. Check for attributes, specific to your SDK before loading it. Return null if there is none.
   */
  fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? = null

  // Inspections


  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PySdkProvider> = ExtensionPointName.create("Pythonid.pySdkProvider")
  }
}

