// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting.ner

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.intellij.plugins.markdown.ui.actions.styling.highlighting.*

internal enum class HighlightedEntityType(private val highlightingKey: TextAttributesKey, private val priority: Int, val highlightingManager: EntityHighlightingManager) {
  Date(MarkdownHighlighterColors.DATE, 1, DatesHighlightingManager),
  Money(MarkdownHighlighterColors.MONEY, 1, MoneyHighlightingManager),
  Number(MarkdownHighlighterColors.NUMBER, 0, NumbersHighlightingManager),
  Person(MarkdownHighlighterColors.PERSON, 1, PersonsHighlightingManager),
  Organization(MarkdownHighlighterColors.ORGANIZATION, 1, OrganizationsHighlightingManager);

  fun isEnabled(project: Project) = highlightingManager.isHighlightingEnabled(project)

  fun applyHighlightingTo(ranges: Collection<TextRange>, markupModel: MarkupModel, project: Project) {
    if (isEnabled(project)) {
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
