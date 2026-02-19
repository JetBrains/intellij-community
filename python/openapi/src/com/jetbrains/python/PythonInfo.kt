// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class PythonInfo(
  val languageLevel: LanguageLevel,
  val freeThreaded: Boolean = false,
) : Comparable<PythonInfo> {
  override fun compareTo(other: PythonInfo): Int {
    // Backward, a newer version has higher priority
    val versionComparison = -LanguageLevel.VERSION_COMPARATOR.compare(languageLevel, other.languageLevel)
    if (versionComparison != 0) {
      return versionComparison
    }

    // Not free threaded is more stable, hence it has higher priority
    return freeThreaded.compareTo(other.freeThreaded)
  }
}