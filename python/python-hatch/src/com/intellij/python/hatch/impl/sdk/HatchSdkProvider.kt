// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.impl.sdk

import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.jetbrains.python.hatch.sdk.HatchSdkAdditionalData
import com.jetbrains.python.sdk.PySdkProvider
import org.jdom.Element

internal class HatchSdkProvider : PySdkProvider {
  override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? = HatchSdkAdditionalData.createIfHatch(element)

}