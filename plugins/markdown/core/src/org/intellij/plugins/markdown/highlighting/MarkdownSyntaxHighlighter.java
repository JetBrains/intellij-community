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
    safeMap(ATTRIBUTES, MarkdownTokenTypes.TEXT, MarkdownHighlighterColors.TEXT);
    safeMap(ATTRIBUTES, MarkdownElementTypes.STRONG, MarkdownHighlighterColors.BOLD);
    safeMap(ATTRIBUTES, MarkdownElementTypes.EMPH, MarkdownHighlighterColors.ITALIC);
    safeMap(ATTRIBUTES, MarkdownElementTypes.STRIKETHROUGH, MarkdownHighlighterColors.STRIKE_THROUGH);

    safeMap(ATTRIBUTES, MarkdownTokenTypes.HORIZONTAL_RULE, MarkdownHighlighterColors.HRULE);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.TABLE_SEPARATOR, MarkdownHighlighterColors.TABLE_SEPARATOR);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.BLOCK_QUOTE, MarkdownHighlighterColors.BLOCK_QUOTE_MARKER);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.LIST_MARKERS, MarkdownHighlighterColors.LIST_MARKER);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.HEADER_MARKERS, MarkdownHighlighterColors.HEADER_MARKER);

    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.HEADER_LEVEL_1_SET, MarkdownHighlighterColors.HEADER_LEVEL_1);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.HEADER_LEVEL_2_SET, MarkdownHighlighterColors.HEADER_LEVEL_2);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.HEADER_LEVEL_3_SET, MarkdownHighlighterColors.HEADER_LEVEL_3);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.HEADER_LEVEL_4_SET, MarkdownHighlighterColors.HEADER_LEVEL_4);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.HEADER_LEVEL_5_SET, MarkdownHighlighterColors.HEADER_LEVEL_5);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.HEADER_LEVEL_6_SET, MarkdownHighlighterColors.HEADER_LEVEL_6);

    safeMap(ATTRIBUTES, MarkdownElementTypes.INLINE_LINK, MarkdownHighlighterColors.EXPLICIT_LINK);
    safeMap(ATTRIBUTES, MarkdownTokenTypeSets.REFERENCE_LINK_SET, MarkdownHighlighterColors.REFERENCE_LINK);
    safeMap(ATTRIBUTES, MarkdownElementTypes.IMAGE, MarkdownHighlighterColors.IMAGE);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.AUTOLINK, MarkdownHighlighterColors.AUTO_LINK);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.GFM_AUTOLINK, MarkdownHighlighterColors.AUTO_LINK);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.EMAIL_AUTOLINK, MarkdownHighlighterColors.AUTO_LINK);
    safeMap(ATTRIBUTES, MarkdownElementTypes.LINK_DEFINITION, MarkdownHighlighterColors.LINK_DEFINITION);
    safeMap(ATTRIBUTES, MarkdownElementTypes.LINK_TEXT, MarkdownHighlighterColors.LINK_TEXT);
    safeMap(ATTRIBUTES, MarkdownElementTypes.LINK_LABEL, MarkdownHighlighterColors.LINK_LABEL);
    safeMap(ATTRIBUTES, MarkdownElementTypes.LINK_DESTINATION, MarkdownHighlighterColors.LINK_DESTINATION);
    safeMap(ATTRIBUTES, MarkdownElementTypes.LINK_TITLE, MarkdownHighlighterColors.LINK_TITLE);
    safeMap(ATTRIBUTES, MarkdownElementTypes.LINK_COMMENT, MarkdownHighlighterColors.COMMENT);

    safeMap(ATTRIBUTES, MarkdownElementTypes.BLOCK_QUOTE, MarkdownHighlighterColors.BLOCK_QUOTE);
    safeMap(ATTRIBUTES, MarkdownElementTypes.UNORDERED_LIST, MarkdownHighlighterColors.UNORDERED_LIST);
    safeMap(ATTRIBUTES, MarkdownElementTypes.ORDERED_LIST, MarkdownHighlighterColors.ORDERED_LIST);
    safeMap(ATTRIBUTES, MarkdownElementTypes.LIST_ITEM, MarkdownHighlighterColors.LIST_ITEM);

    safeMap(ATTRIBUTES, MarkdownTokenTypes.BACKTICK, MarkdownHighlighterColors.TEXT);
    safeMap(ATTRIBUTES, MarkdownElementTypes.CODE_SPAN, MarkdownHighlighterColors.CODE_SPAN);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.CODE_LINE, MarkdownHighlighterColors.CODE_BLOCK);

    safeMap(ATTRIBUTES, MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownHighlighterColors.CODE_FENCE);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.CODE_FENCE_START, MarkdownHighlighterColors.CODE_FENCE_MARKER);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.CODE_FENCE_END, MarkdownHighlighterColors.CODE_FENCE_MARKER);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.FENCE_LANG, MarkdownHighlighterColors.CODE_FENCE_LANGUAGE);

    safeMap(ATTRIBUTES, MarkdownElementTypes.HTML_BLOCK, MarkdownHighlighterColors.HTML_BLOCK);
    safeMap(ATTRIBUTES, MarkdownTokenTypes.HTML_TAG, MarkdownHighlighterColors.INLINE_HTML);

    safeMap(ATTRIBUTES, MarkdownElementTypes.DEFINITION_LIST, MarkdownHighlighterColors.DEFINITION_LIST);
    safeMap(ATTRIBUTES, MarkdownElementTypes.DEFINITION_MARKER, MarkdownHighlighterColors.DEFINITION_LIST_MARKER);
    safeMap(ATTRIBUTES, MarkdownElementTypes.DEFINITION, MarkdownHighlighterColors.DEFINITION);
    safeMap(ATTRIBUTES, MarkdownElementTypes.DEFINITION_TERM, MarkdownHighlighterColors.TERM);

    safeMap(ATTRIBUTES, MarkdownElementTypes.FRONT_MATTER_HEADER_DELIMITER, MarkdownHighlighterColors.FRONT_MATTER_HEADER_DELIMITER);
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
