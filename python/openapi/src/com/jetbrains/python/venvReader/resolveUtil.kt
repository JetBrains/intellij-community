// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.venvReader

import com.jetbrains.python.sdk.CustomSdkHomePattern
import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Converts [str] to [Path] of str is real nio path. Returns null otherwise
 */
@ApiStatus.Internal
fun tryResolvePath(str: String?): Path? {
  if (str == null || CustomSdkHomePattern.isCustomPythonSdkHomePath(str)) {
    return null
  }

  try {
    return Path.of(str)
  }
  catch (_: InvalidPathException) {
  }
  return null
}
