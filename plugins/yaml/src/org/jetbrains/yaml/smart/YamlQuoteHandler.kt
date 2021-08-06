// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.smart

import com.intellij.codeInsight.editorActions.QuoteHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import org.jetbrains.yaml.YAMLTokenTypes

private class YamlQuoteHandler : QuoteHandler {

  private fun isOneQuote(iterator: HighlighterIterator): Boolean {
    with(iterator) {
      if (tokenType != YAMLTokenTypes.TEXT) return false
      if (end - start != 1) return false
      return document.charsSequence[start].let { it == '"' || it == '\'' }
    }
  }

  override fun isClosingQuote(iterator: HighlighterIterator, offset: Int): Boolean = isOneQuote(iterator) && iterator.end == offset

  override fun isOpeningQuote(iterator: HighlighterIterator, offset: Int): Boolean = isOneQuote(iterator)

  override fun hasNonClosedLiteral(editor: Editor, iterator: HighlighterIterator, offset: Int): Boolean = isOneQuote(iterator)

  override fun isInsideLiteral(iterator: HighlighterIterator): Boolean = false
}