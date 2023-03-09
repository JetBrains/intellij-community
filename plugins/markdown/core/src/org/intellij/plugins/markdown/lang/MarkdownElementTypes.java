// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang;

import com.intellij.psi.templateLanguages.TemplateDataElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.OuterLanguageElementType;
import org.intellij.markdown.flavours.gfm.GFMElementTypes;
import org.intellij.markdown.flavours.gfm.GFMTokenTypes;
import org.intellij.plugins.markdown.lang.parser.MarkdownParserDefinition;
import org.intellij.plugins.markdown.lang.parser.blocks.DefinitionListMarkerProvider;
import org.intellij.plugins.markdown.lang.parser.blocks.frontmatter.FrontMatterHeaderMarkerProvider;
import org.jetbrains.annotations.ApiStatus;

import static org.intellij.plugins.markdown.lang.MarkdownElementType.platformType;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.HTML_BLOCK_CONTENT;

public interface MarkdownElementTypes {
  IFileElementType MARKDOWN_FILE_ELEMENT_TYPE = MarkdownParserDefinition.MARKDOWN_FILE_ELEMENT_TYPE;

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

  /**
   * CommonMark autolinks are wrapped with <> brackets, so parser creates a composite node:
   * <pre>
   * {@code
   * CompositeNode(AUTOLINK):
   * |-->LeafNode(<)
   * |-->LeafNode(AUTOLINK)
   * |-->LeafNode(>)
   * }
   * </pre>
   * Both composite and leaf nodes have AUTOLINK type, but first one comes from {@link MarkdownElementTypes}
   * and the second one comes from {@link MarkdownTokenTypes}.
   */
  IElementType AUTOLINK = platformType(org.intellij.markdown.MarkdownElementTypes.AUTOLINK);

  IElementType TABLE = platformType(GFMElementTypes.TABLE);
  IElementType TABLE_ROW = platformType(GFMElementTypes.ROW);
  IElementType TABLE_HEADER = platformType(GFMElementTypes.HEADER);
  IElementType TABLE_CELL = platformType(GFMTokenTypes.CELL);

  IElementType SETEXT_1 = MarkdownStubElementTypes.SETEXT_1;
  IElementType SETEXT_2 = MarkdownStubElementTypes.SETEXT_2;

  IElementType ATX_1 = MarkdownStubElementTypes.ATX_1;
  IElementType ATX_2 = MarkdownStubElementTypes.ATX_2;
  IElementType ATX_3 = MarkdownStubElementTypes.ATX_3;
  IElementType ATX_4 = MarkdownStubElementTypes.ATX_4;
  IElementType ATX_5 = MarkdownStubElementTypes.ATX_5;
  IElementType ATX_6 = MarkdownStubElementTypes.ATX_6;

  org.intellij.markdown.MarkdownElementType COMMENT = new org.intellij.markdown.MarkdownElementType("COMMENT", true);

  IElementType LINK_COMMENT = platformType(COMMENT);


  @ApiStatus.Experimental
  IElementType DEFINITION_LIST = platformType(DefinitionListMarkerProvider.DEFINITION_LIST);

  @ApiStatus.Experimental
  IElementType DEFINITION = platformType(DefinitionListMarkerProvider.DEFINITION);

  @ApiStatus.Experimental
  IElementType DEFINITION_MARKER = platformType(DefinitionListMarkerProvider.DEFINITION_MARKER);

  @ApiStatus.Experimental
  IElementType DEFINITION_TERM = platformType(DefinitionListMarkerProvider.TERM);

  @ApiStatus.Experimental
  IElementType FRONT_MATTER_HEADER = platformType(FrontMatterHeaderMarkerProvider.FRONT_MATTER_HEADER);

  @ApiStatus.Experimental
  IElementType FRONT_MATTER_HEADER_CONTENT = platformType(FrontMatterHeaderMarkerProvider.FRONT_MATTER_HEADER_CONTENT);

  @ApiStatus.Experimental
  IElementType FRONT_MATTER_HEADER_DELIMITER = platformType(FrontMatterHeaderMarkerProvider.FRONT_MATTER_HEADER_DELIMITER);
}
