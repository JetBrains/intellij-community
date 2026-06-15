// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.jetbrains.python.sdk.PySdkProvider
import org.jdom.Element

internal class PyPipEnvSdkProvider : PySdkProvider {
  override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? {
    return PyPipEnvSdkAdditionalData.load(element)
  }

}