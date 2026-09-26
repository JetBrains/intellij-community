// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class PythonInfo(
  val languageLevel: LanguageLevel,
  val freeThreaded: Boolean = false,
  /**
   * The interpreter's own reported version, patch included (`3.15.0`), when whoever built this had it — a probe that
   * ran `--version` did, since [languageLevel] is parsed from that same output and keeps only major.minor.
   *
   * `null` when it was never captured (a [PythonInfo] derived from an SDK's language level, say). A caller that wants
   * to show a version should fall back to [languageLevel] rather than run the interpreter again for it.
   */
  val version: @NlsSafe String? = null,
) : Comparable<PythonInfo> {
  // [version] takes no part in this, which is historical rather than considered: the comparator predates the field.
  // Arguably it should take part — a newer patch of the same language level is the better pick, by the same reasoning
  // that ranks language levels — but this ordering is what decides which interpreter gets preferred in call sites that
  // have not been audited, so changing it is a behavioural change of unknown reach.
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