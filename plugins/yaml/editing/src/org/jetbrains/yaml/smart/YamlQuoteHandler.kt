// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.smart

import com.intellij.codeInsight.editorActions.QuoteHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import org.jetbrains.yaml.YAMLElementTypes
import org.jetbrains.yaml.settingsSync.shouldDoNothingInBackendMode

private class YamlQuoteHandler : QuoteHandler {

  private fun isQuote(it: Char?) = it == '"' || it == '\''

  private fun isOneQuote(iterator: HighlighterIterator): Boolean {
    with(iterator) {
      if (!YAMLElementTypes.TEXT_SCALAR_ITEMS.contains(tokenType)) return false
      return isQuote(document.charsSequence[start]) && !isQuote(document.charsSequence.getOrNull(start + 1))
    }
  }

  override fun isClosingQuote(iterator: HighlighterIterator, offset: Int): Boolean =
    !shouldDoNothingInBackendMode() && isOneQuote(iterator) && iterator.end == offset

  override fun isOpeningQuote(iterator: HighlighterIterator, offset: Int): Boolean =
    !shouldDoNothingInBackendMode() && isOneQuote(iterator) && with(iterator) { start == offset || end - start == 1 }

  override fun hasNonClosedLiteral(editor: Editor, iterator: HighlighterIterator, offset: Int): Boolean =
    !shouldDoNothingInBackendMode() && isOneQuote (iterator)

  override fun isInsideLiteral(iterator: HighlighterIterator): Boolean = false
}