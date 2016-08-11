/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.rest;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

/**
 * User : catherine
 */
public class RestColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[]{
    new AttributesDescriptor("Comment", RestSyntaxHighlighter.REST_COMMENT),
    new AttributesDescriptor("Title", RestSyntaxHighlighter.REST_SECTION_HEADER),
    new AttributesDescriptor("Explicit markup", RestSyntaxHighlighter.REST_EXPLICIT),
    new AttributesDescriptor("Fields", RestSyntaxHighlighter.REST_FIELD),
    new AttributesDescriptor("Reference name", RestSyntaxHighlighter.REST_REF_NAME),
    new AttributesDescriptor("Inline literals", RestSyntaxHighlighter.REST_FIXED),
    new AttributesDescriptor("Bold text", RestSyntaxHighlighter.REST_BOLD),
    new AttributesDescriptor("Italic text", RestSyntaxHighlighter.REST_ITALIC),
    new AttributesDescriptor("Interpreted text", RestSyntaxHighlighter.REST_INTERPRETED),
    new AttributesDescriptor("Literal and line blocks", RestSyntaxHighlighter.REST_INLINE),
  };

  @NonNls private static final HashMap<String, TextAttributesKey> ourTagToDescriptorMap = new HashMap<>();


  @NotNull
  public String getDisplayName() {
    return "reStructuredText";
  }

  public Icon getIcon() {
    return RestFileType.INSTANCE.getIcon();
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    final SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(RestFileType.INSTANCE, null, null);
    assert highlighter != null;
    return highlighter;
  }

  @NotNull
  public String getDemoText() {
    return
      ".. comment for documentation master file\n\n" +
      "===============\n" +
      " Section Title\n" +
      "===============\n\n" +
      ".. toctree::\n" +
      "   :maxdepth: 2\n\n" +
      "There is *some italics text*\n" +
      "and **bold one** " +
      "and ``inline literals``\n\n" +
      "A link_ in citation style.\n" +
      "\n" +
      ".. _link: http://www.google.com\n\n" +
      ".. rubric:: Footnotes\n" +
      ".. [*] footnote.\n" +
      ".. [REL09] Citation\n\n" +
      ":ref:`builders`\n\n" +
      "\n" +
      "::\n" +
      "\n" +
      "  Whitespace, newlines, blank lines, and\n" +
      "  all kinds of markup (like *this* or\n" +
      "  \\this) is preserved by literal blocks.\n\n" +
      "It was literal block.";
  }

  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourTagToDescriptorMap;
  }
}
