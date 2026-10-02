// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus

@get:ApiStatus.Internal
val Sdk.isUv: Boolean
  get() = PythonSdkUtil.isPythonSdk(this) && uvFlavorData != null

/** Whether this interpreter is a uv environment. */
@get:ApiStatus.Internal
val PythonInterpreter.isUv: Boolean
  @Suppress("DEPRECATION") // The uv flavor data lives in the SDK additional data.
  get() = getSdkAPI().isUv

@get:ApiStatus.Internal
val Sdk.uvFlavorData: UvSdkFlavorData?
  get() {
    return when (val data = pySdkAdditionalData) {
      is UvSdkAdditionalData -> data.flavorData
      is PyTargetAwareAdditionalData -> data.flavorAndData.data as? UvSdkFlavorData
      else -> null
    }
  }
