// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting.ner

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.intellij.plugins.markdown.ui.actions.styling.highlighting.HighlightDatesAction
import org.intellij.plugins.markdown.ui.actions.styling.highlighting.HighlightMoneyAction

internal enum class HighlightedEntityType(private val highlightingKey: TextAttributesKey, private val priority: Int) {
  Date(MarkdownHighlighterColors.DATE, 1) {
    override val isEnabled: Boolean
      get() = HighlightDatesAction.isHighlightingEnabled
  },

  Money(MarkdownHighlighterColors.MONEY, 1) {
    override val isEnabled: Boolean
      get() = HighlightMoneyAction.isHighlightingEnabled
  };

  abstract val isEnabled: Boolean

  fun applyHighlightingTo(ranges: Collection<TextRange>, markupModel: MarkupModel) {
    if (isEnabled) {
      for (range in ranges) {
        val highlighter = markupModel.addRangeHighlighter(highlightingKey,
                                                          range.startOffset, range.endOffset,
                                                          HighlighterLayer.ADDITIONAL_SYNTAX + priority,
                                                          HighlighterTargetArea.EXACT_RANGE)
        highlighter.putUserData(ENTITY_TYPE, this)
      }
    }
    else {
      markupModel.allHighlighters
        .filter { it.getUserData(ENTITY_TYPE) === this }
        .forEach(markupModel::removeHighlighter)
    }
  }
}

internal val ENTITY_TYPE = Key.create<HighlightedEntityType>("MARKDOWN_DATE_EXTERNAL_ANNOTATOR_ENTITY_TYPE")
