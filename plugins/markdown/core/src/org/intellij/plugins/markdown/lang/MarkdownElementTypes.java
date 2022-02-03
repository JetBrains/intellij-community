// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang;

import com.intellij.psi.templateLanguages.TemplateDataElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.tree.OuterLanguageElementType;
import org.intellij.markdown.flavours.gfm.GFMElementTypes;
import org.intellij.markdown.flavours.gfm.GFMTokenTypes;

import static org.intellij.plugins.markdown.lang.MarkdownElementType.platformType;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.HTML_BLOCK_CONTENT;

public interface MarkdownElementTypes {
  IFileElementType MARKDOWN_FILE_ELEMENT_TYPE = new IStubFileElementType("Markdown file", MarkdownLanguage.INSTANCE);

  IElementType MARKDOWN_FILE = platformType(org.intellij.markdown.MarkdownElementTypes.MARKDOWN_FILE);

  IElementType UNORDERED_LIST = platformType(org.intellij.markdown.MarkdownElementTypes.UNORDERED_LIST);

  IElementType ORDERED_LIST = platformType(org.intellij.markdown.MarkdownElementTypes.ORDERED_LIST);

  IElementType LIST_ITEM = platformType(org.intellij.markdown.MarkdownElementTypes.LIST_ITEM);

  IElementType BLOCK_QUOTE = platformType(org.intellij.markdown.MarkdownElementTypes.BLOCK_QUOTE);

  IElementType CODE_FENCE = platformType(org.intellij.markdown.MarkdownElementTypes.CODE_FENCE);

  IElementType CODE_BLOCK = platformType(org.intellij.markdown.MarkdownElementTypes.CODE_BLOCK);

  IElementType CODE_SPAN = platformType(org.intellij.markdown.MarkdownElementTypes.CODE_SPAN);

  IElementType PARAGRAPH = platformType(org.intellij.markdown.MarkdownElementTypes.PARAGRAPH);

  IElementType EMPH = platformType(org.intellij.markdown.MarkdownElementTypes.EMPH);

  IElementType STRONG = platformType(org.intellij.markdown.MarkdownElementTypes.STRONG);

  IElementType STRIKETHROUGH = platformType(GFMElementTypes.STRIKETHROUGH);

  IElementType LINK_DEFINITION = platformType(org.intellij.markdown.MarkdownElementTypes.LINK_DEFINITION);
  IElementType LINK_LABEL = platformType(org.intellij.markdown.MarkdownElementTypes.LINK_LABEL);
  IElementType LINK_DESTINATION = platformType(org.intellij.markdown.MarkdownElementTypes.LINK_DESTINATION);
  IElementType LINK_TITLE = platformType(org.intellij.markdown.MarkdownElementTypes.LINK_TITLE);
  IElementType LINK_TEXT = platformType(org.intellij.markdown.MarkdownElementTypes.LINK_TEXT);
  IElementType INLINE_LINK = platformType(org.intellij.markdown.MarkdownElementTypes.INLINE_LINK);
  IElementType FULL_REFERENCE_LINK = platformType(org.intellij.markdown.MarkdownElementTypes.FULL_REFERENCE_LINK);
  IElementType SHORT_REFERENCE_LINK = platformType(org.intellij.markdown.MarkdownElementTypes.SHORT_REFERENCE_LINK);
  IElementType IMAGE = platformType(org.intellij.markdown.MarkdownElementTypes.IMAGE);

  IElementType HTML_BLOCK = platformType(org.intellij.markdown.MarkdownElementTypes.HTML_BLOCK);

  IElementType MARKDOWN_OUTER_BLOCK = new OuterLanguageElementType("MARKDOWN_OUTER_BLOCK", MarkdownLanguage.INSTANCE);

  TemplateDataElementType MARKDOWN_TEMPLATE_DATA =
    new TemplateDataElementType("MARKDOWN_TEMPLATE_DATA", MarkdownLanguage.INSTANCE, HTML_BLOCK_CONTENT, MARKDOWN_OUTER_BLOCK);

  IElementType AUTOLINK = platformType(org.intellij.markdown.MarkdownElementTypes.AUTOLINK);

  IElementType TABLE = platformType(GFMElementTypes.TABLE);
  IElementType TABLE_ROW = platformType(GFMElementTypes.ROW);
  IElementType TABLE_HEADER = platformType(GFMElementTypes.HEADER);
  IElementType TABLE_CELL = platformType(GFMTokenTypes.CELL);

  IElementType SETEXT_1 = platformType(org.intellij.markdown.MarkdownElementTypes.SETEXT_1);
  IElementType SETEXT_2 = platformType(org.intellij.markdown.MarkdownElementTypes.SETEXT_2);

  IElementType ATX_1 = platformType(org.intellij.markdown.MarkdownElementTypes.ATX_1);
  IElementType ATX_2 = platformType(org.intellij.markdown.MarkdownElementTypes.ATX_2);
  IElementType ATX_3 = platformType(org.intellij.markdown.MarkdownElementTypes.ATX_3);
  IElementType ATX_4 = platformType(org.intellij.markdown.MarkdownElementTypes.ATX_4);
  IElementType ATX_5 = platformType(org.intellij.markdown.MarkdownElementTypes.ATX_5);
  IElementType ATX_6 = platformType(org.intellij.markdown.MarkdownElementTypes.ATX_6);

  org.intellij.markdown.MarkdownElementType COMMENT = new org.intellij.markdown.MarkdownElementType("COMMENT", true);

  IElementType LINK_COMMENT = platformType(COMMENT);
}
