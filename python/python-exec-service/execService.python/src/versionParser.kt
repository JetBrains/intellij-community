// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.python

import com.intellij.util.PatternUtil
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus

private val VERSION_RE = "((Python|GraalPy) (\\S+)).*".toRegex(RegexOption.DOT_MATCHES_ALL)

@ApiStatus.Internal
fun getVersionStringFromOutput(output: String): String? =
  PatternUtil.getFirstMatch(output.lines(), VERSION_RE.toPattern())

@ApiStatus.Internal
fun getLanguageLevelFromVersionStringSafe(versionString: String): LanguageLevel? {
  val matchResult = VERSION_RE.matchEntire(versionString)
  return matchResult?.let {
    val (_, _, version) = it.destructured
    LanguageLevel.fromPythonVersionSafe(version)
  }
}
