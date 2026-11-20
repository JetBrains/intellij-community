// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.uv.backend.UvPyTool
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus

@get:ApiStatus.Internal
val Sdk.isUv: Boolean
  get() = PythonSdkUtil.isPythonSdk(this) && uvFlavorData != null

/** Whether uv manages the environment of this interpreter, see [com.intellij.python.sdk.common.PyInterpreterRef.manager]. */
@get:ApiStatus.Internal
val PythonInterpreter.isUv: Boolean
  get() = ref.manager == UvPyTool.getInstance().fusId

@get:ApiStatus.Internal
val Sdk.uvFlavorData: UvSdkFlavorData?
  get() {
    return when (val data = pySdkAdditionalData) {
      is UvSdkAdditionalData -> data.flavorData
      is PyTargetAwareAdditionalData -> data.flavorAndData.data as? UvSdkFlavorData
      else -> null
    }
  }
