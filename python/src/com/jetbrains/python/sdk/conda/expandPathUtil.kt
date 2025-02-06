// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.conda

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Expands `~` in **nix and `%ENV%` on Windows for local system only.
 * it is a hack to be used to speed-up conda search. Do not use.
 */
@Internal
fun expandPathLocally(path: FullPathOnTarget): FullPathOnTarget {
  var result = path
  when (SystemInfoRt.isWindows) {
    true -> {
      for ((env, value) in System.getenv()) {
        val pattern = "%${env}%"
        if (pattern in result) {
          result = result.replace(pattern, value)
        }
      }
    }
    false -> { // posix
      result = path.replace("~", SystemProperties.getUserHome())
    }
  }
  return result
}