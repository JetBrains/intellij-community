// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class MarkdownSyntaxHighlighter extends SyntaxHighlighterBase {

  protected final Lexer lexer = new MarkdownHighlightingLexer();

  protected static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<>();

  static {
    safeMap(ATTRIBUTES, MarkdownTokenTypes.TEXT, MarkdownHighlighterColors.TEXT_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.STRONG, MarkdownHighlighterColors.BOLD_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.EMPH, MarkdownHighlighterColors.ITALIC_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.STRIKETHROUGH, MarkdownHighlighterColors.STRIKE_THROUGH_ATTR_KEY);

    safeMap(ATTRIBUTES, MarkdownTokenTypes.HORIZONTAL_RULE, MarkdownHighlighterColors.HRULE_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.TABLE_SEPARATOR, MarkdownHighlighterColors.TABLE_SEPARATOR_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.BLOCK_QUOTE, MarkdownHighlighterColors.BLOCK_QUOTE_MARKER_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.LIST_MARKERS, MarkdownHighlighterColors.LIST_MARKER_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.HEADER_MARKERS, MarkdownHighlighterColors.HEADER_MARKER_ATTR_KEY);

    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.HEADER_LEVEL_1_SET, MarkdownHighlighterColors.HEADER_LEVEL_1_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.HEADER_LEVEL_2_SET, MarkdownHighlighterColors.HEADER_LEVEL_2_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.HEADER_LEVEL_3_SET, MarkdownHighlighterColors.HEADER_LEVEL_3_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.HEADER_LEVEL_4_SET, MarkdownHighlighterColors.HEADER_LEVEL_4_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.HEADER_LEVEL_5_SET, MarkdownHighlighterColors.HEADER_LEVEL_5_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.HEADER_LEVEL_6_SET, MarkdownHighlighterColors.HEADER_LEVEL_6_ATTR_KEY);

    safeMap(ATTRIBUTES, MarkdownElementTypes.INLINE_LINK, MarkdownHighlighterColors.EXPLICIT_LINK_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.REFERENCE_LINK_SET, MarkdownHighlighterColors.REFERENCE_LINK_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.IMAGE, MarkdownHighlighterColors.IMAGE_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.AUTOLINK, MarkdownHighlighterColors.AUTO_LINK_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.LINK_DEFINITION, MarkdownHighlighterColors.LINK_DEFINITION_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.LINK_TEXT, MarkdownHighlighterColors.LINK_TEXT_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.LINK_LABEL, MarkdownHighlighterColors.LINK_LABEL_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.LINK_DESTINATION, MarkdownHighlighterColors.LINK_DESTINATION_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.LINK_TITLE, MarkdownHighlighterColors.LINK_TITLE_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.LINK_COMMENT, MarkdownHighlighterColors.COMMENT_ATTR_KEY);

    safeMap(ATTRIBUTES, MarkdownElementTypes.BLOCK_QUOTE, MarkdownHighlighterColors.BLOCK_QUOTE_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.UNORDERED_LIST, MarkdownHighlighterColors.UNORDERED_LIST_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.ORDERED_LIST, MarkdownHighlighterColors.ORDERED_LIST_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.LIST_ITEM, MarkdownHighlighterColors.LIST_ITEM_ATTR_KEY);

    safeMap(ATTRIBUTES, MarkdownTokenTypes.BACKTICK, MarkdownHighlighterColors.TEXT_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownElementTypes.CODE_SPAN, MarkdownHighlighterColors.CODE_SPAN_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.CODE_LINE, MarkdownHighlighterColors.CODE_BLOCK_ATTR_KEY);

    safeMap(ATTRIBUTES, MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownHighlighterColors.CODE_FENCE_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.CODE_FENCE_START, MarkdownHighlighterColors.CODE_FENCE_MARKER_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.CODE_FENCE_END, MarkdownHighlighterColors.CODE_FENCE_MARKER_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.FENCE_LANG, MarkdownHighlighterColors.CODE_FENCE_LANGUAGE_ATTR_KEY);

    safeMap(ATTRIBUTES, MarkdownElementTypes.HTML_BLOCK, MarkdownHighlighterColors.HTML_BLOCK_ATTR_KEY);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.HTML_TAG, MarkdownHighlighterColors.INLINE_HTML_ATTR_KEY);
  }

  @Override
  @NotNull
  public Lexer getHighlightingLexer() {
    return lexer;
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    return pack(ATTRIBUTES.get(tokenType));
  }
}
