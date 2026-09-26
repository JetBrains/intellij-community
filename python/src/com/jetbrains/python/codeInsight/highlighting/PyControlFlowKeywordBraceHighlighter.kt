// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.highlighting

import com.intellij.codeInsight.highlighting.HeavyBraceHighlighter
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyFile

/**
 * Highlights a secondary control-flow keyword (`elif`/`else`/`except`/`finally`/`case`) together
 * with the header keyword that opens its compound statement and, reusing the platform
 * brace-highlighting machinery, shows a preview of that header line when it is scrolled above the
 * visible area.
 *
 * Matching control-flow keywords requires the PSI structure (an `else` may belong to an
 * `if`/`for`/`while`/`try`), so a token-pair [com.intellij.lang.PairedBraceMatcher] cannot express
 * it; this lightweight PSI lookup runs in the background read action the extension point provides.
 */
internal class PyControlFlowKeywordBraceHighlighter : HeavyBraceHighlighter() {
  override fun isAvailable(psiFile: PsiFile, offset: Int): Boolean = psiFile is PyFile

  override fun matchBrace(psiFile: PsiFile, offset: Int): Pair<TextRange, TextRange>? {
    val context = PyControlFlowKeywordMatcher.findKeywordContext(psiFile, offset) ?: return null
    // Caret on the header keyword itself: there is no preceding keyword to preview above.
    if (context.caretIndex <= 0) return null
    val header = context.keywords.first()
    val current = context.keywords[context.caretIndex]
    // The header is above the current keyword, so it goes first (see HeavyBraceHighlighter.match contract).
    return Pair.create(header.textRange, current.textRange)
  }
}
