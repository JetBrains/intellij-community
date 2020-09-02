// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
interface PySdkRenderingExtension {

  /**
   * Additional info to be displayed with the SDK's name.
   */
  fun getAdditionalData(sdk: Sdk): String?

  fun getIcon(sdk: Sdk): Icon?

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<PySdkRenderingExtension>("Pythonid.sdkRendering")
  }
}