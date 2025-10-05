// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.getOrCreateAdditionalData

internal val Sdk.isHatch: Boolean
  get() {
    if (!PythonSdkUtil.isPythonSdk(this)) {
      return false
    }

    return getOrCreateAdditionalData() is HatchSdkAdditionalData
  }

