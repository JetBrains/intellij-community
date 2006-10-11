/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.options.colors.pages;

import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.OptionsBundle;

import javax.swing.*;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

public class XMLColorsPage implements ColorSettingsPage {
  private static final ColorDescriptor[] COLORS = new ColorDescriptor[0];
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[] {
    new AttributesDescriptor(OptionsBundle.message("options.xml.attribute.descriptor.prologue"), HighlighterColors.XML_PROLOGUE),
    new AttributesDescriptor(OptionsBundle.message("options.xml.attribute.descriptor.comment"), HighlighterColors.XML_COMMENT),
    new AttributesDescriptor(OptionsBundle.message("options.xml.attribute.descriptor.tag"), HighlighterColors.XML_TAG),
    new AttributesDescriptor(OptionsBundle.message("options.xml.attribute.descriptor.tag.name"), HighlighterColors.XML_TAG_NAME),
    new AttributesDescriptor(OptionsBundle.message("options.xml.attribute.descriptor.attribute.name"), HighlighterColors.XML_ATTRIBUTE_NAME),
    new AttributesDescriptor(OptionsBundle.message("options.xml.attribute.descriptor.attribute.value"), HighlighterColors.XML_ATTRIBUTE_VALUE),
    new AttributesDescriptor(OptionsBundle.message("options.xml.attribute.descriptor.tag.data"), HighlighterColors.XML_TAG_DATA),
    new AttributesDescriptor(OptionsBundle.message("options.xml.attribute.descriptor.descriptor.entity,reference"), HighlighterColors.XML_ENTITY_REFERENCE),
  };

  @NotNull
  public String getDisplayName() {
    return OptionsBundle.message("options.xml.display.name");
  }

  public Icon getIcon() {
    return StdFileTypes.XML.getIcon();
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return COLORS;
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new XmlFileHighlighter();
  }

  @NotNull
  public String getDemoText() {
    return "<?xml version='1.0' encoding='ISO-8859-1'  ?>\n" +
           "<!DOCTYPE index>\n" +
           "<!-- Some xml example -->\n" +
           "<index version=\"1.0\">\n" +
           "   <name>Main Index</name>\n" +
           "   <indexitem text=\"rename\" target=\"refactoring.rename\"/>\n" +
           "   <indexitem text=\"move\" target=\"refactoring.move\"/>\n" +
           "   <indexitem text=\"migrate\" target=\"refactoring.migrate\"/>\n" +
           "   <indexitem text=\"usage search\" target=\"find.findUsages\"/>\n&amp; &#x00B7;" +
           "   <indexitem text=\"project\" target=\"project.management\"/>\n" +
           "</index>";
  }

  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }
}