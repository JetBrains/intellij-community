package com.jetbrains.python.sdk

import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.nio.file.Path

// TODO: Move to PythonSdkUtil when rewritten in Kotlin

/**
 * Converts [str] to [Path] of str is real nio path. Returns null otherwise
 */
@ApiStatus.Internal
fun tryResolvePath(str: String?): Path? {
  if (str == null || PythonSdkUtil.isCustomPythonSdkHomePath(str)) {
    return null
  }

  try {
    return Path.of(str)
  }
  catch (_: InvalidPathException) {
  }
  return null
}
