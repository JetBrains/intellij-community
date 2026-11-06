// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CondaSdkUtil")

package com.jetbrains.python

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import org.jetbrains.annotations.ApiStatus

// It reads the SDK flavor without IO, so it is fast and safe on the EDT.

@get:ApiStatus.Internal
val Sdk.isCondaVirtualEnv: Boolean get() = (sdkAdditionalData as? PythonSdkAdditionalData)?.flavor is CondaEnvSdkFlavor

@get:ApiStatus.Internal
val PythonInterpreter.isCondaVirtualEnv: Boolean
  @Suppress("DEPRECATION") // The flavor lives in the SDK additional data.
  get() = getSdkAPI().isCondaVirtualEnv
