// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MarkdownColorSettingsPage implements ColorSettingsPage {

  private static final AttributesDescriptor[] ATTRIBUTE_DESCRIPTORS = AttributeDescriptorsHolder.INSTANCE.get();

  @Override
  @NotNull
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    final Map<String, TextAttributesKey> result = new HashMap<>();

    result.put("hh1", MarkdownHighlighterColors.HEADER_LEVEL_1_ATTR_KEY);
    result.put("hh2", MarkdownHighlighterColors.HEADER_LEVEL_2_ATTR_KEY);
    result.put("hh3", MarkdownHighlighterColors.HEADER_LEVEL_3_ATTR_KEY);
    result.put("hh4", MarkdownHighlighterColors.HEADER_LEVEL_4_ATTR_KEY);
    result.put("hh5", MarkdownHighlighterColors.HEADER_LEVEL_5_ATTR_KEY);
    result.put("hh6", MarkdownHighlighterColors.HEADER_LEVEL_6_ATTR_KEY);

    result.put("bold", MarkdownHighlighterColors.BOLD_ATTR_KEY);
    result.put("boldm", MarkdownHighlighterColors.BOLD_MARKER_ATTR_KEY);
    result.put("italic", MarkdownHighlighterColors.ITALIC_ATTR_KEY);
    result.put("italicm", MarkdownHighlighterColors.ITALIC_MARKER_ATTR_KEY);
    result.put("strike", MarkdownHighlighterColors.STRIKE_THROUGH_ATTR_KEY);

    result.put("alink", MarkdownHighlighterColors.AUTO_LINK_ATTR_KEY);
    result.put("link_def", MarkdownHighlighterColors.LINK_DEFINITION_ATTR_KEY);
    result.put("link_text", MarkdownHighlighterColors.LINK_TEXT_ATTR_KEY);
    result.put("link_label", MarkdownHighlighterColors.LINK_LABEL_ATTR_KEY);
    result.put("link_dest", MarkdownHighlighterColors.LINK_DESTINATION_ATTR_KEY);
    result.put("link_img", MarkdownHighlighterColors.IMAGE_ATTR_KEY);
    result.put("link_title", MarkdownHighlighterColors.LINK_TITLE_ATTR_KEY);

    result.put("code_span", MarkdownHighlighterColors.CODE_SPAN_ATTR_KEY);
    result.put("code_block", MarkdownHighlighterColors.CODE_BLOCK_ATTR_KEY);
    result.put("code_fence", MarkdownHighlighterColors.CODE_FENCE_ATTR_KEY);
    result.put("quote", MarkdownHighlighterColors.BLOCK_QUOTE_ATTR_KEY);

    result.put("ul", MarkdownHighlighterColors.UNORDERED_LIST_ATTR_KEY);
    result.put("ol", MarkdownHighlighterColors.ORDERED_LIST_ATTR_KEY);

    return result;
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return ATTRIBUTE_DESCRIPTORS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  @NonNls
  @NotNull
  public String getDemoText() {
    try (Reader reader = new InputStreamReader(getClass().getResourceAsStream("SampleDocument.md"), StandardCharsets.UTF_8)) {
      String result = StreamUtil.readText(reader);
      return StringUtil.convertLineSeparators(result);
    }
    catch (IOException ignored) { }

    return "*error loading text*";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return MarkdownBundle.message("markdown.plugin.name");
  }

  @Override
  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new MarkdownSyntaxHighlighter();
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return null;
  }

  private enum AttributeDescriptorsHolder {
    INSTANCE;

    private final Map<String, TextAttributesKey> myMap = new HashMap<>();

    AttributeDescriptorsHolder() {
      put("markdown.editor.colors.text", MarkdownHighlighterColors.TEXT_ATTR_KEY);
      put("markdown.editor.colors.bold", MarkdownHighlighterColors.BOLD_ATTR_KEY);
      put("markdown.editor.colors.bold_marker", MarkdownHighlighterColors.BOLD_MARKER_ATTR_KEY);
      put("markdown.editor.colors.italic", MarkdownHighlighterColors.ITALIC_ATTR_KEY);
      put("markdown.editor.colors.italic_marker", MarkdownHighlighterColors.ITALIC_MARKER_ATTR_KEY);
      put("markdown.editor.colors.strikethrough", MarkdownHighlighterColors.STRIKE_THROUGH_ATTR_KEY);
      put("markdown.editor.colors.header_level_1", MarkdownHighlighterColors.HEADER_LEVEL_1_ATTR_KEY);
      put("markdown.editor.colors.header_level_2", MarkdownHighlighterColors.HEADER_LEVEL_2_ATTR_KEY);
      put("markdown.editor.colors.header_level_3", MarkdownHighlighterColors.HEADER_LEVEL_3_ATTR_KEY);
      put("markdown.editor.colors.header_level_4", MarkdownHighlighterColors.HEADER_LEVEL_4_ATTR_KEY);
      put("markdown.editor.colors.header_level_5", MarkdownHighlighterColors.HEADER_LEVEL_5_ATTR_KEY);
      put("markdown.editor.colors.header_level_6", MarkdownHighlighterColors.HEADER_LEVEL_6_ATTR_KEY);

      put("markdown.editor.colors.blockquote", MarkdownHighlighterColors.BLOCK_QUOTE_ATTR_KEY);

      put("markdown.editor.colors.code_span", MarkdownHighlighterColors.CODE_SPAN_ATTR_KEY);
      put("markdown.editor.colors.code_span_marker", MarkdownHighlighterColors.CODE_SPAN_MARKER_ATTR_KEY);
      put("markdown.editor.colors.code_block", MarkdownHighlighterColors.CODE_BLOCK_ATTR_KEY);
      put("markdown.editor.colors.code_fence", MarkdownHighlighterColors.CODE_FENCE_ATTR_KEY);

      put("markdown.editor.colors.hrule", MarkdownHighlighterColors.HRULE_ATTR_KEY);
      put("markdown.editor.colors.table_separator", MarkdownHighlighterColors.TABLE_SEPARATOR_ATTR_KEY);
      put("markdown.editor.colors.blockquote_marker", MarkdownHighlighterColors.BLOCK_QUOTE_MARKER_ATTR_KEY);
      put("markdown.editor.colors.list_marker", MarkdownHighlighterColors.LIST_MARKER_ATTR_KEY);
      put("markdown.editor.colors.header_marker", MarkdownHighlighterColors.HEADER_MARKER_ATTR_KEY);

      put("markdown.editor.colors.auto_link", MarkdownHighlighterColors.AUTO_LINK_ATTR_KEY);
      put("markdown.editor.colors.explicit_link", MarkdownHighlighterColors.EXPLICIT_LINK_ATTR_KEY);
      put("markdown.editor.colors.reference_link", MarkdownHighlighterColors.REFERENCE_LINK_ATTR_KEY);
      put("markdown.editor.colors.image", MarkdownHighlighterColors.IMAGE_ATTR_KEY);
      put("markdown.editor.colors.link_definition", MarkdownHighlighterColors.LINK_DEFINITION_ATTR_KEY);
      put("markdown.editor.colors.link_text", MarkdownHighlighterColors.LINK_TEXT_ATTR_KEY);
      put("markdown.editor.colors.link_label", MarkdownHighlighterColors.LINK_LABEL_ATTR_KEY);
      put("markdown.editor.colors.link_destination", MarkdownHighlighterColors.LINK_DESTINATION_ATTR_KEY);
      put("markdown.editor.colors.link_title", MarkdownHighlighterColors.LINK_TITLE_ATTR_KEY);

      put("markdown.editor.colors.unordered_list", MarkdownHighlighterColors.UNORDERED_LIST_ATTR_KEY);
      put("markdown.editor.colors.ordered_list", MarkdownHighlighterColors.ORDERED_LIST_ATTR_KEY);
      put("markdown.editor.colors.list_item", MarkdownHighlighterColors.LIST_ITEM_ATTR_KEY);
      put("markdown.editor.colors.html_block", MarkdownHighlighterColors.HTML_BLOCK_ATTR_KEY);
      put("markdown.editor.colors.inline_html", MarkdownHighlighterColors.INLINE_HTML_ATTR_KEY);
    }

    public AttributesDescriptor @NotNull [] get() {
      final AttributesDescriptor[] result = new AttributesDescriptor[myMap.size()];
      int i = 0;

      for (Map.Entry<String, TextAttributesKey> entry : myMap.entrySet()) {
        result[i++] = new AttributesDescriptor(MarkdownBundle.message(entry.getKey()), entry.getValue());
      }

      return result;
    }

    private void put(@NotNull String bundleKey, @NotNull TextAttributesKey attributes) {
      if (myMap.put(bundleKey, attributes) != null) {
        throw new IllegalArgumentException("Duplicated key: " + bundleKey);
      }
    }
  }
}
