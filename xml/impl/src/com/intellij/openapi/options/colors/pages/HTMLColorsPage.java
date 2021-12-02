/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.options.colors.pages;

import com.intellij.codeInsight.daemon.impl.tagTreeHighlighting.XmlTagTreeHighlightingColors;
import com.intellij.ide.highlighter.HtmlFileHighlighter;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

public class HTMLColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[] {
    new AttributesDescriptor(XmlBundle.message("options.html.attribute.descriptor.code"), XmlHighlighterColors.HTML_CODE),
    new AttributesDescriptor(XmlBundle.message("options.html.attribute.descriptor.comment"), XmlHighlighterColors.HTML_COMMENT),
    new AttributesDescriptor(XmlBundle.message("options.html.attribute.descriptor.tag"), XmlHighlighterColors.HTML_TAG),
    new AttributesDescriptor(XmlBundle.message("options.xml.attribute.descriptor.tag.custom"), XmlHighlighterColors.HTML_CUSTOM_TAG),
    new AttributesDescriptor(XmlBundle.message("options.xml.attribute.descriptor.tag.name.custom"), XmlHighlighterColors.HTML_CUSTOM_TAG_NAME),
    new AttributesDescriptor(XmlBundle.message("options.html.attribute.descriptor.tag.name"), XmlHighlighterColors.HTML_TAG_NAME),
    new AttributesDescriptor(XmlBundle.message("options.html.attribute.descriptor.attribute.name"), XmlHighlighterColors.HTML_ATTRIBUTE_NAME),
    new AttributesDescriptor(XmlBundle.message("options.html.attribute.descriptor.attribute.value"), XmlHighlighterColors.HTML_ATTRIBUTE_VALUE),
    new AttributesDescriptor(XmlBundle.message("options.html.attribute.descriptor.entity.reference"), XmlHighlighterColors.HTML_ENTITY_REFERENCE),
    new AttributesDescriptor(OptionsBundle.message("options.any.color.descriptor.injected.language.fragment"), XmlHighlighterColors.HTML_INJECTED_LANGUAGE_FRAGMENT),
  };
  private static final String FULL_PRODUCT_NAME = ApplicationNamesInfo.getInstance().getFullProductName();

  @Override
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("options.html.display.name");
  }

  @Override
  public Icon getIcon() {
    return HtmlFileType.INSTANCE.getIcon();
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return ATTRS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    // todo: make preview for it

    final ColorKey[] colorKeys = XmlTagTreeHighlightingColors.getColorKeys();
    final ColorDescriptor[] colorDescriptors = new ColorDescriptor[colorKeys.length];

    for (int i = 0; i < colorDescriptors.length; i++) {
      colorDescriptors[i] = new ColorDescriptor(XmlBundle.message("options.html.attribute.descriptor.tag.tree", i + 1),
                                                colorKeys[i], ColorDescriptor.Kind.BACKGROUND);
    }

    return colorDescriptors;
  }

  @Override
  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new HtmlFileHighlighter();
  }

  @Override
  @NotNull
  public String getDemoText() {
    return "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2//EN\">\n" +
           "<!--\n" +
           "*        Sample comment\n" +
           "-->\n" +
           "<HTML>\n" +
           "<head>\n" +
           "<title>" + FULL_PRODUCT_NAME + "</title>\n" +
           "</head>\n" +
           "<body>\n" +
           "<h1>" + FULL_PRODUCT_NAME + "</h1>\n" +
           "<p><br><b><IMG border=0 height=12 src=\"images/hg.gif\" width=18 >\n" +
           "What is " + FULL_PRODUCT_NAME.replaceAll(" ", "&nbsp;") + "? &#x00B7; &Alpha; </b><br><br>\n" +
           "<custom_tag><<custom_tag_name>custom-tag</custom_tag_name>></custom_tag>" +
           "hello" +
           "<custom_tag></<custom_tag_name>custom_tag</custom_tag_name>></custom_tag>\n" +
           "</body>\n" +
           "</html>";
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return new ContainerUtil.ImmutableMapBuilder<String, TextAttributesKey>()
      .put("custom_tag", XmlHighlighterColors.HTML_CUSTOM_TAG)
      .put("custom_tag_name", XmlHighlighterColors.HTML_CUSTOM_TAG_NAME)
      .build();
  }
}
