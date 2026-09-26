// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Marker for SDKs whose additional data could not be loaded (e.g., stale remote interpreters).
 * Extends [PythonSdkAdditionalData] so that [pySdkAdditionalData] recognizes it
 * and returns it early instead of crashing on flavor detection. See PY-88807.
 */
@ApiStatus.Internal
@PyInternalExecApi
class PyInvalidSdk : PythonSdkAdditionalData(
  PyFlavorAndData(PyFlavorData.Empty, PythonSdkFlavor.UnknownFlavor.INSTANCE), Path.of(""),
)