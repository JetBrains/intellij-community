// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter

import com.intellij.formatting.SpacingBuilder
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.util.applyIf
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.formatter.blocks.MarkdownFormattingBlock
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings


/**
 * Spacing model for Markdown files.
 *
 * It defines which elements should have blank-lines around, which should
 * be prepended with spaces and so on.
 *
 * Note, that Spacing model works only with [TokenType.WHITE_SPACE] elements,
 * if you need to format something INSIDE you should break it into few
 * [MarkdownFormattingBlock]
 *
 * As a source of inspiration following styleguides were used:
 * * [Google Markdown code style](https://github.com/google/styleguide/blob/gh-pages/docguide/style.md)
 * * [The Arctic Ice Studio Markdown code style](https://github.com/arcticicestudio/styleguide-markdown)
 */
internal object MarkdownSpacingBuilder {
  fun get(settings: CodeStyleSettings): SpacingBuilder {
    val markdown = settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java)

    return SpacingBuilder(settings, MarkdownLanguage.INSTANCE)
      //CODE
      .aroundInside(MarkdownElementTypes.CODE_FENCE, MarkdownElementTypes.MARKDOWN_FILE)
      .blankLinesRange(markdown.MIN_LINES_AROUND_BLOCK_ELEMENTS, markdown.MAX_LINES_AROUND_BLOCK_ELEMENTS)
      .aroundInside(MarkdownElementTypes.CODE_BLOCK, MarkdownElementTypes.MARKDOWN_FILE)
      .blankLinesRange(markdown.MIN_LINES_AROUND_BLOCK_ELEMENTS, markdown.MAX_LINES_AROUND_BLOCK_ELEMENTS)

      //TABLE
      .aroundInside(MarkdownElementTypes.TABLE, MarkdownElementTypes.MARKDOWN_FILE)
      .blankLinesRange(markdown.MIN_LINES_AROUND_BLOCK_ELEMENTS, markdown.MAX_LINES_AROUND_BLOCK_ELEMENTS)

      //LISTS
      //we can't enforce one line between lists since from commonmark perspective
      //one-line break does not break lists, but considered as a space between items
      .between(MarkdownTokenTypeSets.LISTS, MarkdownTokenTypeSets.LISTS).blankLines(markdown.MIN_LINES_AROUND_BLOCK_ELEMENTS)
      .aroundInside(MarkdownTokenTypeSets.LISTS, MarkdownElementTypes.LIST_ITEM).blankLines(0)
      //but we can enforce one line around LISTS
      .aroundInside(MarkdownTokenTypeSets.LISTS, MarkdownElementTypes.MARKDOWN_FILE)
      .blankLinesRange(markdown.MIN_LINES_AROUND_BLOCK_ELEMENTS, markdown.MAX_LINES_AROUND_BLOCK_ELEMENTS)

      .applyIf(markdown.FORCE_ONE_SPACE_AFTER_LIST_BULLET) {
        between(MarkdownTokenTypeSets.LIST_MARKERS, MarkdownElementTypes.PARAGRAPH).spaces(1)
      }


      //HEADINGS
      .aroundInside(MarkdownTokenTypeSets.ATX_HEADERS, MarkdownElementTypes.MARKDOWN_FILE)
      .blankLinesRange(markdown.MIN_LINES_AROUND_HEADER, markdown.MAX_LINES_AROUND_HEADER)

      .applyIf(markdown.FORCE_ONE_SPACE_AFTER_HEADER_SYMBOL) {
        between(MarkdownTokenTypes.ATX_HEADER, MarkdownTokenTypes.ATX_CONTENT).spaces(1)
      }

      //BLOCKQUOTES
      .applyIf(markdown.FORCE_ONE_SPACE_AFTER_BLOCKQUOTE_SYMBOL) {
        after(MarkdownTokenTypes.BLOCK_QUOTE).spaces(1)
      }

      //LINKS
      .before(MarkdownElementTypes.LINK_DEFINITION).blankLines(1)

      //PARAGRAPHS
      .betweenInside(MarkdownElementTypes.PARAGRAPH, MarkdownElementTypes.PARAGRAPH, MarkdownElementTypes.MARKDOWN_FILE)
      .blankLinesRange(markdown.MIN_LINES_BETWEEN_PARAGRAPHS, markdown.MAX_LINES_BETWEEN_PARAGRAPHS)
      .apply {
        val spaces = if (markdown.FORCE_ONE_SPACE_BETWEEN_WORDS) 1 else Integer.MAX_VALUE
        between(MarkdownTokenTypes.TEXT, MarkdownTokenTypes.TEXT).spacing(1, spaces, 0, markdown.KEEP_LINE_BREAKS_INSIDE_TEXT_BLOCKS, 0)
      }
  }

  private fun SpacingBuilder.RuleBuilder.blankLinesRange(from: Int, to: Int): SpacingBuilder {
    return spacing(0, 0, to + 1, false, from)
  }
}
