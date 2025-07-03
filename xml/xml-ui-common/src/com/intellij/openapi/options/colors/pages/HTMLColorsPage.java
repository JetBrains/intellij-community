// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.xml.XmlCoreBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

public class HTMLColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[] {
    new AttributesDescriptor(XmlCoreBundle.message("options.html.attribute.descriptor.code"), XmlHighlighterColors.HTML_CODE),
    new AttributesDescriptor(XmlCoreBundle.message("options.html.attribute.descriptor.comment"), XmlHighlighterColors.HTML_COMMENT),
    new AttributesDescriptor(XmlCoreBundle.message("options.html.attribute.descriptor.tag"), XmlHighlighterColors.HTML_TAG),
    new AttributesDescriptor(XmlCoreBundle.message("options.xml.attribute.descriptor.tag.name.custom"), XmlHighlighterColors.HTML_CUSTOM_TAG_NAME),
    new AttributesDescriptor(XmlCoreBundle.message("options.html.attribute.descriptor.tag.name"), XmlHighlighterColors.HTML_TAG_NAME),
    new AttributesDescriptor(XmlCoreBundle.message("options.html.attribute.descriptor.attribute.name"), XmlHighlighterColors.HTML_ATTRIBUTE_NAME),
    new AttributesDescriptor(XmlCoreBundle.message("options.html.attribute.descriptor.attribute.value"), XmlHighlighterColors.HTML_ATTRIBUTE_VALUE),
    new AttributesDescriptor(XmlCoreBundle.message("options.html.attribute.descriptor.entity.reference"), XmlHighlighterColors.HTML_ENTITY_REFERENCE),
    new AttributesDescriptor(OptionsBundle.message("options.any.color.descriptor.injected.language.fragment"), XmlHighlighterColors.HTML_INJECTED_LANGUAGE_FRAGMENT),
  };
  private static final String FULL_PRODUCT_NAME = ApplicationNamesInfo.getInstance().getFullProductName();

  @Override
  public @NotNull String getDisplayName() {
    return XmlCoreBundle.message("options.html.display.name");
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
      colorDescriptors[i] = new ColorDescriptor(XmlCoreBundle.message("options.html.attribute.descriptor.tag.tree", i + 1),
                                                colorKeys[i], ColorDescriptor.Kind.BACKGROUND);
    }

    return colorDescriptors;
  }

  @Override
  public @NotNull SyntaxHighlighter getHighlighter() {
    return new HtmlFileHighlighter();
  }

  @Override
  public @NotNull String getDemoText() {
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
           "<<custom_tag_name>custom-tag</custom_tag_name>>" +
           "hello" +
           "</<custom_tag_name>custom_tag</custom_tag_name>>\n" +
           "</body>\n" +
           "</html>";
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return Map.of(
      "custom_tag_name", XmlHighlighterColors.HTML_CUSTOM_TAG_NAME);
  }
}
