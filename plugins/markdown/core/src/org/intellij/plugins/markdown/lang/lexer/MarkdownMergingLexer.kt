// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.lexer

import com.intellij.lexer.MergeFunction
import com.intellij.lexer.MergingLexerAdapterBase
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes

class MarkdownMergingLexer : MergingLexerAdapterBase(MarkdownLexerAdapter()) {
  override fun getMergeFunction(): MergeFunction {
    return APOSTROPHE_MERGING_FUNCTION
  }

  companion object {
    private val APOSTROPHE_MERGING_FUNCTION = MergeFunction { type, originalLexer ->
      if (type == MarkdownTokenTypes.TEXT && originalLexer.tokenType == MarkdownTokenTypes.SINGLE_QUOTE) {
        originalLexer.advance()
        if (originalLexer.tokenType == MarkdownTokenTypes.TEXT) {
          originalLexer.advance()
        }
        return@MergeFunction MarkdownTokenTypes.TEXT
      }
      type
    }
  }
}