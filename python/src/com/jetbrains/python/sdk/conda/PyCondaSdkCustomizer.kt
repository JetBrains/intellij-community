// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.conda

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyCondaSdkCustomizer {
  val preferCondaEnvironments: Boolean
    get() = false

  val detectBaseEnvironment: Boolean
    get() = false

  val preferExistingEnvironments: Boolean
    get() = false

  val sharedEnvironmentsByDefault: Boolean
    get() = false

  val suggestSharedCondaEnvironments: Boolean
    get() = false

  val fallbackInterpreterFix: LocalQuickFix?
    get() = null

  companion object {
    val EP_NAME: ExtensionPointName<PyCondaSdkCustomizer> = ExtensionPointName.create("Pythonid.condaSdkCustomizer")
    val instance: PyCondaSdkCustomizer
      get() = EP_NAME.extensionList.first()
  }
}