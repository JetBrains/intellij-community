// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.highlighting

import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Plugs the related keywords of a Python multipart compound statement (`if`/`elif`/`else`,
 * `for`/`else`, `while`/`else`, `try`/`except`/`else`/`finally`, `match`/`case`) into the platform
 * code-block machinery: they are highlighted together when the caret is on one of them and the
 * standard "Move Caret to Matching Brace" action jumps between the statement boundaries.
 *
 * The off-screen preview of the header is provided separately by [PyControlFlowKeywordBraceHighlighter].
 */
internal class PyControlFlowKeywordCodeBlockSupportHandler : CodeBlockSupportHandler {
  override fun getCodeBlockMarkerRanges(elementAtCursor: PsiElement): List<TextRange> =
    PyControlFlowKeywordMatcher.compoundStatementKeywordRanges(elementAtCursor) ?: emptyList()

  override fun getCodeBlockRange(elementAtCursor: PsiElement): TextRange =
    PyControlFlowKeywordMatcher.compoundStatementRange(elementAtCursor) ?: TextRange.EMPTY_RANGE
}
