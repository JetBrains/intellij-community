// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.extensions.ExtensionPointName

interface PyAddSdkProvider {
  fun createView(): PyAddSdkView

  companion object {
    val EP_NAME: ExtensionPointName<PyAddSdkProvider> = ExtensionPointName.create("Pythonid.pyAddSdkProvider")
  }
}