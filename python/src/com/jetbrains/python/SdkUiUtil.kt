// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor


// These functions get SDK type without touching IO, hence fast and save to be called from EDT

internal val Sdk.isVirtualEnv: Boolean get() = (sdkAdditionalData as? PythonSdkAdditionalData)?.flavor is VirtualEnvSdkFlavor
internal val Sdk.isCondaVirtualEnv: Boolean get() = (sdkAdditionalData as? PythonSdkAdditionalData)?.flavor is CondaEnvSdkFlavor
