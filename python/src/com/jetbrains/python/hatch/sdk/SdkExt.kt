// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.sdk

import com.intellij.openapi.projectRoots.Sdk

internal val Sdk.isHatch: Boolean
  get() = sdkAdditionalData is HatchSdkAdditionalData

