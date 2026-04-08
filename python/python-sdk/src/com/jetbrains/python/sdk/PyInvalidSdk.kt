// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import org.jetbrains.annotations.ApiStatus

/**
 * Marker for SDKs whose additional data could not be loaded (e.g., stale remote interpreters).
 * Extends [PythonSdkAdditionalData] so that [getOrCreateAdditionalData] recognizes it
 * and returns it early instead of crashing on flavor detection. See PY-88807.
 */
@ApiStatus.Internal
object PyInvalidSdk : PythonSdkAdditionalData()