// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.poetry.backend.sdk

import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.jetbrains.python.sdk.PySdkProvider
import com.jetbrains.python.sdk.poetry.PyPoetrySdkAdditionalData
import org.jdom.Element

/**
 *  This source code is created by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

internal class PoetrySdkProvider : PySdkProvider {

  override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? {
    return PyPoetrySdkAdditionalData.load(element)
  }
}
