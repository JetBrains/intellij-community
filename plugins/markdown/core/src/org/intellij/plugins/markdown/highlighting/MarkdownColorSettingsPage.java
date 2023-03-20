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

    result.put("hh1", MarkdownHighlighterColors.HEADER_LEVEL_1);
    result.put("hh2", MarkdownHighlighterColors.HEADER_LEVEL_2);
    result.put("hh3", MarkdownHighlighterColors.HEADER_LEVEL_3);
    result.put("hh4", MarkdownHighlighterColors.HEADER_LEVEL_4);
    result.put("hh5", MarkdownHighlighterColors.HEADER_LEVEL_5);
    result.put("hh6", MarkdownHighlighterColors.HEADER_LEVEL_6);

    result.put("bold", MarkdownHighlighterColors.BOLD);
    result.put("boldm", MarkdownHighlighterColors.BOLD_MARKER);
    result.put("italic", MarkdownHighlighterColors.ITALIC);
    result.put("italicm", MarkdownHighlighterColors.ITALIC_MARKER);
    result.put("strike", MarkdownHighlighterColors.STRIKE_THROUGH);

    result.put("alink", MarkdownHighlighterColors.AUTO_LINK);
    result.put("link_def", MarkdownHighlighterColors.LINK_DEFINITION);
    result.put("link_text", MarkdownHighlighterColors.LINK_TEXT);
    result.put("link_label", MarkdownHighlighterColors.LINK_LABEL);
    result.put("link_dest", MarkdownHighlighterColors.LINK_DESTINATION);
    result.put("link_img", MarkdownHighlighterColors.IMAGE);
    result.put("link_title", MarkdownHighlighterColors.LINK_TITLE);

    result.put("code_span", MarkdownHighlighterColors.CODE_SPAN);
    result.put("code_block", MarkdownHighlighterColors.CODE_BLOCK);
    result.put("code_fence", MarkdownHighlighterColors.CODE_FENCE);
    result.put("quote", MarkdownHighlighterColors.BLOCK_QUOTE);

    result.put("ul", MarkdownHighlighterColors.UNORDERED_LIST);
    result.put("ol", MarkdownHighlighterColors.ORDERED_LIST);

    result.put("dl", MarkdownHighlighterColors.DEFINITION_LIST);
    result.put("dd", MarkdownHighlighterColors.DEFINITION);
    result.put("dt", MarkdownHighlighterColors.TERM);
    result.put("dm", MarkdownHighlighterColors.DEFINITION_LIST_MARKER);

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
      put("markdown.editor.colors.text", MarkdownHighlighterColors.TEXT);
      put("markdown.editor.colors.bold", MarkdownHighlighterColors.BOLD);
      put("markdown.editor.colors.bold_marker", MarkdownHighlighterColors.BOLD_MARKER);
      put("markdown.editor.colors.italic", MarkdownHighlighterColors.ITALIC);
      put("markdown.editor.colors.italic_marker", MarkdownHighlighterColors.ITALIC_MARKER);
      put("markdown.editor.colors.strikethrough", MarkdownHighlighterColors.STRIKE_THROUGH);
      put("markdown.editor.colors.header_level_1", MarkdownHighlighterColors.HEADER_LEVEL_1);
      put("markdown.editor.colors.header_level_2", MarkdownHighlighterColors.HEADER_LEVEL_2);
      put("markdown.editor.colors.header_level_3", MarkdownHighlighterColors.HEADER_LEVEL_3);
      put("markdown.editor.colors.header_level_4", MarkdownHighlighterColors.HEADER_LEVEL_4);
      put("markdown.editor.colors.header_level_5", MarkdownHighlighterColors.HEADER_LEVEL_5);
      put("markdown.editor.colors.header_level_6", MarkdownHighlighterColors.HEADER_LEVEL_6);

      put("markdown.editor.colors.blockquote", MarkdownHighlighterColors.BLOCK_QUOTE);

      put("markdown.editor.colors.code_span", MarkdownHighlighterColors.CODE_SPAN);
      put("markdown.editor.colors.code_span_marker", MarkdownHighlighterColors.CODE_SPAN_MARKER);
      put("markdown.editor.colors.code_block", MarkdownHighlighterColors.CODE_BLOCK);
      put("markdown.editor.colors.code_fence", MarkdownHighlighterColors.CODE_FENCE);

      put("markdown.editor.colors.hrule", MarkdownHighlighterColors.HRULE);
      put("markdown.editor.colors.table_separator", MarkdownHighlighterColors.TABLE_SEPARATOR);
      put("markdown.editor.colors.blockquote_marker", MarkdownHighlighterColors.BLOCK_QUOTE_MARKER);
      put("markdown.editor.colors.list_marker", MarkdownHighlighterColors.LIST_MARKER);
      put("markdown.editor.colors.header_marker", MarkdownHighlighterColors.HEADER_MARKER);

      put("markdown.editor.colors.auto_link", MarkdownHighlighterColors.AUTO_LINK);
      put("markdown.editor.colors.explicit_link", MarkdownHighlighterColors.EXPLICIT_LINK);
      put("markdown.editor.colors.reference_link", MarkdownHighlighterColors.REFERENCE_LINK);
      put("markdown.editor.colors.image", MarkdownHighlighterColors.IMAGE);
      put("markdown.editor.colors.link_definition", MarkdownHighlighterColors.LINK_DEFINITION);
      put("markdown.editor.colors.link_text", MarkdownHighlighterColors.LINK_TEXT);
      put("markdown.editor.colors.link_label", MarkdownHighlighterColors.LINK_LABEL);
      put("markdown.editor.colors.link_destination", MarkdownHighlighterColors.LINK_DESTINATION);
      put("markdown.editor.colors.link_title", MarkdownHighlighterColors.LINK_TITLE);

      put("markdown.editor.colors.unordered_list", MarkdownHighlighterColors.UNORDERED_LIST);
      put("markdown.editor.colors.ordered_list", MarkdownHighlighterColors.ORDERED_LIST);
      put("markdown.editor.colors.list_item", MarkdownHighlighterColors.LIST_ITEM);
      put("markdown.editor.colors.html_block", MarkdownHighlighterColors.HTML_BLOCK);
      put("markdown.editor.colors.inline_html", MarkdownHighlighterColors.INLINE_HTML);

      put("markdown.editor.colors.definition_list", MarkdownHighlighterColors.DEFINITION_LIST);
      put("markdown.editor.colors.definition_list_marker", MarkdownHighlighterColors.DEFINITION_LIST_MARKER);
      put("markdown.editor.colors.definition", MarkdownHighlighterColors.DEFINITION);
      put("markdown.editor.colors.term", MarkdownHighlighterColors.TERM);
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
