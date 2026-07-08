// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@JvmInline
value class PyPackageName private constructor(val name: @NlsSafe String) {
  companion object {
    fun from(name: String): PyPackageName =
      PyPackageName(normalizePackageName(name))

    /**
     * Normalizes a project name according to
     * https://packaging.python.org/en/latest/specifications/name-normalization/#name-format
     *
     * Keeps only lowercase ASCII letters, digits, and hyphens.
     * Replaces everything else with a hyphen, collapses consecutive hyphens, and strips leading/trailing hyphens.
     */
    @JvmStatic
    fun normalizeProjectName(name: String): String {
      return name
        .lowercase()
        .replace(Regex("[^a-z0-9-]"), "-")
        .replace(Regex("-{2,}"), "-")
        .trim('-')
    }

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