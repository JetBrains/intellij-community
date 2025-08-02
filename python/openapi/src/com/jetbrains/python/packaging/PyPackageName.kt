// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@JvmInline
value class PyPackageName private constructor(val name: String) {
  companion object {
    fun from(name: String): PyPackageName =
      PyPackageName(normalizePackageName(name))

    @JvmStatic
    fun normalizePackageName(packageName: String): String {
      var name = packageName.trim()
        .removePrefix("\"")
        .removeSuffix("\"")

      // e.g. __future__
      if (!name.startsWith("_")) {
        name = name.replace('_', '-')
      }

      return name
        .replace('.', '-')
        .lowercase()
    }
  }
}