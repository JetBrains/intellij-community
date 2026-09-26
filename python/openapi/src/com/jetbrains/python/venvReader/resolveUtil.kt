// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.venvReader

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.utils.Path
import com.jetbrains.python.sdk.CustomSdkHomePattern
import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Converts [str] to [Path] of str is real nio path. Returns null otherwise
 */
@JvmOverloads
@ApiStatus.Internal
fun tryResolvePath(str: String?, eelDescriptor: EelDescriptor? = null): Path? {
  val eelIsLocal = eelDescriptor == null || eelDescriptor == localEel.descriptor
  if (str == null || eelIsLocal && CustomSdkHomePattern.isCustomPythonSdkHomePath(str)) {
    return null
  }

  try {
    return if (eelIsLocal) Path(str) else Path(str, eelDescriptor)
  }
  catch (_: InvalidPathException) {
  }
  catch (_: EelPathException) {
  }
  return null
}
