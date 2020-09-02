// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.SdkAdditionalData
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PySdkAdditionalDataLoader {

  /**
   * Try to load additional data for your SDK. Check for attributes, specific to your SDK before loading it. Return null if there is none.
   */
  fun loadForSdk(element: Element): SdkAdditionalData?

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<PySdkAdditionalDataLoader>("Pythonid.sdkAdditionalDataLoader")
  }
}