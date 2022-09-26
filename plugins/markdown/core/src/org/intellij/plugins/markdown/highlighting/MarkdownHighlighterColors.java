// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.*;
import static com.intellij.openapi.editor.colors.CodeInsightColors.DEPRECATED_ATTRIBUTES;
import static com.intellij.openapi.editor.colors.CodeInsightColors.HYPERLINK_ATTRIBUTES;
import static com.intellij.openapi.editor.colors.EditorColors.INJECTED_LANGUAGE_FRAGMENT;
import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public final class MarkdownHighlighterColors {
  public static final TextAttributesKey TEXT = createTextAttributesKey("MARKDOWN_TEXT", HighlighterColors.TEXT);
  public static final TextAttributesKey BOLD = createTextAttributesKey("MARKDOWN_BOLD");
  public static final TextAttributesKey BOLD_MARKER = createTextAttributesKey("MARKDOWN_BOLD_MARKER", KEYWORD);
  public static final TextAttributesKey ITALIC = createTextAttributesKey("MARKDOWN_ITALIC");
  public static final TextAttributesKey ITALIC_MARKER = createTextAttributesKey("MARKDOWN_ITALIC_MARKER", KEYWORD);
  public static final TextAttributesKey CODE_SPAN = createTextAttributesKey("MARKDOWN_CODE_SPAN", STRING);
  public static final TextAttributesKey CODE_SPAN_MARKER = createTextAttributesKey("MARKDOWN_CODE_SPAN_MARKER", KEYWORD);
  public static final TextAttributesKey BLOCK_QUOTE = createTextAttributesKey("MARKDOWN_BLOCK_QUOTE", STRING);
  public static final TextAttributesKey BLOCK_QUOTE_MARKER = createTextAttributesKey("MARKDOWN_BLOCK_QUOTE_MARKER", KEYWORD);
  public static final TextAttributesKey LIST_MARKER = createTextAttributesKey("MARKDOWN_LIST_MARKER", KEYWORD);
  public static final TextAttributesKey HEADER_MARKER = createTextAttributesKey("MARKDOWN_HEADER_MARKER", KEYWORD);
  public static final TextAttributesKey UNORDERED_LIST = createTextAttributesKey("MARKDOWN_UNORDERED_LIST");
  public static final TextAttributesKey ORDERED_LIST = createTextAttributesKey("MARKDOWN_ORDERED_LIST");
  public static final TextAttributesKey HTML_BLOCK = createTextAttributesKey("MARKDOWN_HTML_BLOCK");
  public static final TextAttributesKey INLINE_HTML = createTextAttributesKey("MARKDOWN_INLINE_HTML");
  public static final TextAttributesKey HEADER_LEVEL_1 = createTextAttributesKey("MARKDOWN_HEADER_LEVEL_1", CONSTANT);
  public static final TextAttributesKey HEADER_LEVEL_2 = createTextAttributesKey("MARKDOWN_HEADER_LEVEL_2", CONSTANT);
  public static final TextAttributesKey HEADER_LEVEL_3 = createTextAttributesKey("MARKDOWN_HEADER_LEVEL_3", CONSTANT);
  public static final TextAttributesKey HEADER_LEVEL_4 = createTextAttributesKey("MARKDOWN_HEADER_LEVEL_4", CONSTANT);
  public static final TextAttributesKey HEADER_LEVEL_5 = createTextAttributesKey("MARKDOWN_HEADER_LEVEL_5", CONSTANT);
  public static final TextAttributesKey HEADER_LEVEL_6 = createTextAttributesKey("MARKDOWN_HEADER_LEVEL_6", CONSTANT);
  public static final TextAttributesKey HRULE = createTextAttributesKey("MARKDOWN_HRULE", KEYWORD);

  public static final TextAttributesKey CODE_BLOCK = createTextAttributesKey("MARKDOWN_CODE_BLOCK", STRING);

  public static final TextAttributesKey CODE_FENCE = createTextAttributesKey("MARKDOWN_CODE_FENCE", STRING);
  public static final TextAttributesKey CODE_FENCE_MARKER = createTextAttributesKey("MARKDOWN_CODE_FENCE_MARKER", KEYWORD);
  public static final TextAttributesKey CODE_FENCE_LANGUAGE = createTextAttributesKey("MARKDOWN_CODE_FENCE_LANGUAGE", CONSTANT);

  public static final TextAttributesKey LIST_ITEM = createTextAttributesKey("MARKDOWN_LIST_ITEM");
  public static final TextAttributesKey TABLE_SEPARATOR = createTextAttributesKey("MARKDOWN_TABLE_SEPARATOR", KEYWORD);
  public static final TextAttributesKey STRIKE_THROUGH = createTextAttributesKey("MARKDOWN_STRIKE_THROUGH", DEPRECATED_ATTRIBUTES);

  public static final TextAttributesKey LINK_DEFINITION = createTextAttributesKey("MARKDOWN_LINK_DEFINITION");
  public static final TextAttributesKey REFERENCE_LINK = createTextAttributesKey("MARKDOWN_REFERENCE_LINK");
  public static final TextAttributesKey IMAGE = createTextAttributesKey("MARKDOWN_IMAGE", INJECTED_LANGUAGE_FRAGMENT);
  public static final TextAttributesKey EXPLICIT_LINK = createTextAttributesKey("MARKDOWN_EXPLICIT_LINK", STRING);
  public static final TextAttributesKey LINK_TEXT = createTextAttributesKey("MARKDOWN_LINK_TEXT", HYPERLINK_ATTRIBUTES);
  public static final TextAttributesKey LINK_DESTINATION = createTextAttributesKey("MARKDOWN_LINK_DESTINATION", STATIC_METHOD);
  public static final TextAttributesKey LINK_LABEL = createTextAttributesKey("MARKDOWN_LINK_LABEL", KEYWORD);
  public static final TextAttributesKey LINK_TITLE = createTextAttributesKey("MARKDOWN_LINK_TITLE", STRING);
  public static final TextAttributesKey AUTO_LINK = createTextAttributesKey("MARKDOWN_AUTO_LINK", HYPERLINK_ATTRIBUTES);

  public static final TextAttributesKey COMMENT = createTextAttributesKey("MARKDOWN_COMMENT", LINE_COMMENT);

  public static final TextAttributesKey DEFINITION_LIST = createTextAttributesKey("MARKDOWN_DEFINITION_LIST", UNORDERED_LIST);
  public static final TextAttributesKey TERM = createTextAttributesKey("MARKDOWN_TERM", HEADER_LEVEL_1);
  public static final TextAttributesKey DEFINITION = createTextAttributesKey("MARKDOWN_DEFINITION", LIST_ITEM);
  public static final TextAttributesKey DEFINITION_LIST_MARKER = createTextAttributesKey("MARKDOWN_DEFINITION_LIST_MARKER", LIST_MARKER);
}
