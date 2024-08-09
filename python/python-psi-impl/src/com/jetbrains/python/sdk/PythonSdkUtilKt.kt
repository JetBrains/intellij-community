package com.jetbrains.python.sdk

import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

// TODO: Move to PythonSdkUtil when rewritten in Kotlin

/**
 * Converts [str] to [Path] of str is real nio path. Returns null otherwise
 */
@ApiStatus.Internal
fun tryResolvePath(str: String): Path? {
  if (PythonSdkUtil.isCustomPythonSdkHomePath(str)) return null
  try {
    return Paths.get(str)
  }
  catch (_: InvalidPathException) {
  }
  return null
}
